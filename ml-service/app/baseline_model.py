from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
import math
from pathlib import Path
from typing import Any

import joblib
from sklearn.feature_extraction import DictVectorizer
from sklearn.impute import SimpleImputer
from sklearn.linear_model import Ridge
from sklearn.pipeline import Pipeline


MODEL_VERSION = "player-baseline-v2"

TARGETS = (
    ("projected_points", "points"),
    ("projected_rebounds", "rebounds"),
    ("projected_assists", "assists"),
    ("projected_minutes", "minutes"),
    ("projected_steals", "steals"),
    ("projected_blocks", "blocks"),
    ("projected_turnovers", "turnovers"),
    ("fantasy_points", "fantasyPoints"),
    ("projected_field_goals_made", "fieldGoalsMade"),
    ("projected_field_goals_attempted", "fieldGoalsAttempted"),
)

HIT_THRESHOLDS = {
    "projected_points": 5,
    "projected_rebounds": 2,
    "projected_assists": 2,
    "projected_minutes": 5,
    "projected_steals": 1,
    "projected_blocks": 1,
    "projected_turnovers": 2,
    "fantasy_points": 8,
    "projected_field_goals_made": 3,
    "projected_field_goals_attempted": 5,
}


@dataclass
class PlayerBaselineModel:
    pipeline: Pipeline | None = None
    fallback_targets: dict[str, float] = field(default_factory=dict)
    trained_rows: int = 0
    model_version: str = MODEL_VERSION
    recency_halflife_days: float | None = None

    @classmethod
    def fit(cls, rows: list[dict[str, Any]], recency_halflife_days: float | None = None) -> "PlayerBaselineModel":
        examples = complete_training_examples(rows)
        if not examples:
            raise ValueError("No complete player training rows were provided")
        return cls._fit_examples(examples, recency_halflife_days)

    @classmethod
    def evaluate_time_split(
            cls,
            rows: list[dict[str, Any]],
            train_ratio: float = 0.8,
            recency_halflife_days: float | None = None) -> dict[str, Any]:
        if train_ratio <= 0 or train_ratio >= 1:
            raise ValueError("train_ratio must be greater than 0 and less than 1")

        examples = complete_training_examples(rows)
        if len(examples) < 2:
            raise ValueError("At least two complete player training rows are required for evaluation")

        train_examples, test_examples, train_groups, test_groups = split_time_groups(examples, train_ratio)
        model = cls._fit_examples(train_examples, recency_halflife_days)

        values_by_target: dict[str, list[tuple[float, float]]] = {output_name: [] for output_name, _ in TARGETS}
        baseline_values: dict[str, dict[str, list[tuple[float, float]]]] = {
            "feature_average": {output_name: [] for output_name, _ in TARGETS},
            "training_mean": {output_name: [] for output_name, _ in TARGETS},
        }
        for example in test_examples:
            prediction = model.predict(example["features"])
            feature_average_prediction = model._fallback_prediction(example["features"])
            for index, (output_name, _) in enumerate(TARGETS):
                actual = example["targets"][index]
                values_by_target[output_name].append((prediction[output_name], actual))
                baseline_values["feature_average"][output_name].append((feature_average_prediction[output_name], actual))
                baseline_values["training_mean"][output_name].append((model.fallback_targets[output_name], actual))

        return {
            "model_version": model.model_version,
            "train_rows": len(train_examples),
            "test_rows": len(test_examples),
            "total_rows": len(examples),
            "train_ratio": train_ratio,
            "recency_halflife_days": recency_halflife_days,
            "split_strategy": "time_grouped_by_game_datetime",
            "train_groups": train_groups,
            "test_groups": test_groups,
            "training_data_start": example_time(train_examples[0]),
            "training_data_end": example_time(train_examples[-1]),
            "validation_data_start": example_time(test_examples[0]),
            "validation_data_end": example_time(test_examples[-1]),
            "metrics": {
                output_name: regression_metrics(values, HIT_THRESHOLDS.get(output_name))
                for output_name, values in values_by_target.items()
            },
            "baseline_metrics": {
                baseline_name: {
                    output_name: regression_metrics(values, HIT_THRESHOLDS.get(output_name))
                    for output_name, values in target_values.items()
                }
                for baseline_name, target_values in baseline_values.items()
            },
        }

    @classmethod
    def _fit_examples(cls, examples: list[dict[str, Any]], recency_halflife_days: float | None = None) -> "PlayerBaselineModel":
        features = [example["features"] for example in examples]
        targets = [example["targets"] for example in examples]
        pipeline = Pipeline([
            ("features", DictVectorizer(sparse=False)),
            ("imputer", SimpleImputer(strategy="constant", fill_value=0.0)),
            ("model", Ridge(alpha=1.0)),
        ])
        weights = sample_weights(examples, recency_halflife_days)
        if weights is None:
            pipeline.fit(features, targets)
        else:
            pipeline.fit(features, targets, model__sample_weight=weights)

        fallback_targets = {}
        for index, (output_name, _) in enumerate(TARGETS):
            fallback_targets[output_name] = round(sum(row[index] for row in targets) / len(targets), 2)

        return cls(
            pipeline=pipeline,
            fallback_targets=fallback_targets,
            trained_rows=len(examples),
            recency_halflife_days=recency_halflife_days,
        )

    @classmethod
    def load(cls, artifact_path: Path) -> "PlayerBaselineModel":
        if not artifact_path.exists():
            return cls()
        artifact = joblib.load(artifact_path)
        model_version = artifact.get("model_version", MODEL_VERSION)
        if not str(model_version).startswith(MODEL_VERSION):
            return cls()
        return cls(
            pipeline=artifact.get("pipeline"),
            fallback_targets=artifact.get("fallback_targets", {}),
            trained_rows=artifact.get("trained_rows", 0),
            model_version=model_version,
            recency_halflife_days=artifact.get("recency_halflife_days"),
        )

    def save(self, artifact_path: Path) -> None:
        artifact_path.parent.mkdir(parents=True, exist_ok=True)
        joblib.dump({
            "pipeline": self.pipeline,
            "fallback_targets": self.fallback_targets,
            "trained_rows": self.trained_rows,
            "model_version": self.model_version,
            "recency_halflife_days": self.recency_halflife_days,
        }, artifact_path)

    def predict(self, raw_features: dict[str, Any]) -> dict[str, Any]:
        features = clean_features(raw_features)
        if self.pipeline is None:
            predictions = self._fallback_prediction(features)
        else:
            values = self.pipeline.predict([features])[0]
            predictions = {
                output_name: clamp_round(float(values[index]))
                for index, (output_name, _) in enumerate(TARGETS)
            }

        predictions = normalize_prediction(predictions)
        predictions["fantasy_floor"] = clamp_round(predictions["fantasy_points"] * 0.85)
        predictions["fantasy_ceiling"] = clamp_round(predictions["fantasy_points"] * 1.15)
        predictions["risk_level"] = risk_level(features)
        predictions["confidence_score"] = confidence_score(features, self.pipeline is not None, self.trained_rows)
        predictions["factors"] = factors(raw_features)
        return predictions

    def _fallback_prediction(self, features: dict[str, float | str]) -> dict[str, float]:
        points = feature_average(features, "points", self.fallback_targets.get("projected_points", 0.0))
        rebounds = feature_average(features, "rebounds", self.fallback_targets.get("projected_rebounds", 0.0))
        assists = feature_average(features, "assists", self.fallback_targets.get("projected_assists", 0.0))
        minutes = feature_average(features, "minutes", self.fallback_targets.get("projected_minutes", 0.0))
        steals = feature_average(features, "steals", self.fallback_targets.get("projected_steals", 0.0))
        blocks = feature_average(features, "blocks", self.fallback_targets.get("projected_blocks", 0.0))
        turnovers = feature_average(features, "turnovers", self.fallback_targets.get("projected_turnovers", 0.0))
        field_goals_made = feature_average(features, "field_goals_made", self.fallback_targets.get("projected_field_goals_made", 0.0))
        field_goals_attempted = feature_average(features, "field_goals_attempted", self.fallback_targets.get("projected_field_goals_attempted", 0.0))
        fantasy = points + (1.2 * rebounds) + (1.5 * assists) + (3.0 * steals) + (3.0 * blocks) - turnovers
        return {
            "projected_points": clamp_round(points),
            "projected_rebounds": clamp_round(rebounds),
            "projected_assists": clamp_round(assists),
            "projected_minutes": clamp_round(minutes),
            "projected_steals": clamp_round(steals),
            "projected_blocks": clamp_round(blocks),
            "projected_turnovers": clamp_round(turnovers),
            "fantasy_points": clamp_round(fantasy),
            "projected_field_goals_made": clamp_round(field_goals_made),
            "projected_field_goals_attempted": clamp_round(field_goals_attempted),
        }


def clean_features(raw_features: dict[str, Any]) -> dict[str, float | str]:
    clean: dict[str, float | str] = {}
    for key, value in raw_features.items():
        parsed = number(value)
        if parsed is not None:
            clean[key] = parsed
        elif isinstance(value, str) and value.strip():
            clean[key] = value.strip()
    return clean


def complete_training_examples(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    examples: list[dict[str, Any]] = []
    for row in sorted(rows, key=training_row_sort_key):
        target = row.get("targets", {})
        values = [number(target.get(source_name)) for _, source_name in TARGETS]
        if any(value is None for value in values):
            continue
        examples.append({
            "features": clean_features(row.get("features", {})),
            "targets": [float(value) for value in values],
            "row": row,
        })
    return examples


def normalize_prediction(predictions: dict[str, float]) -> dict[str, float]:
    normalized = {key: clamp_round(value) for key, value in predictions.items()}
    made = normalized.get("projected_field_goals_made", 0.0)
    attempted = max(normalized.get("projected_field_goals_attempted", 0.0), made)
    normalized["projected_field_goals_made"] = made
    normalized["projected_field_goals_attempted"] = attempted
    normalized["projected_field_goal_percentage"] = clamp_round(made / attempted) if attempted > 0 else 0.0
    return normalized


def training_row_sort_key(row: dict[str, Any]) -> tuple[str, str, str]:
    return (
        str(row.get("gameDateTime") or ""),
        str(row.get("gameId") or ""),
        str(row.get("playerId") or ""),
    )


def example_time(example: dict[str, Any]) -> str | None:
    value = example["row"].get("gameDateTime")
    return str(value) if value is not None else None


def example_datetime(example: dict[str, Any]) -> datetime | None:
    value = example_time(example)
    if value is None:
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None
    if parsed.tzinfo is not None:
        return parsed.astimezone(timezone.utc).replace(tzinfo=None)
    return parsed


def sample_weights(examples: list[dict[str, Any]], recency_halflife_days: float | None) -> list[float] | None:
    if recency_halflife_days is None or recency_halflife_days <= 0:
        return None
    times = [example_datetime(example) for example in examples]
    known_times = [value for value in times if value is not None]
    if not known_times:
        return None
    latest = max(known_times)
    weights = []
    for value in times:
        if value is None:
            weights.append(1.0)
            continue
        age_days = max((latest - value).total_seconds() / 86400, 0.0)
        weights.append(max(0.05, 0.5 ** (age_days / recency_halflife_days)))
    return weights


def split_time_groups(examples: list[dict[str, Any]], train_ratio: float) -> tuple[list[dict[str, Any]], list[dict[str, Any]], int, int]:
    groups: list[list[dict[str, Any]]] = []
    previous_key = object()
    for example in examples:
        group_key = split_group_key(example)
        if group_key != previous_key:
            groups.append([])
            previous_key = group_key
        groups[-1].append(example)

    if len(groups) < 2:
        raise ValueError("At least two chronological groups are required for evaluation")

    split_index = min(max(int(len(groups) * train_ratio), 1), len(groups) - 1)
    train_groups = groups[:split_index]
    test_groups = groups[split_index:]
    return (
        [example for group in train_groups for example in group],
        [example for group in test_groups for example in group],
        len(train_groups),
        len(test_groups),
    )


def split_group_key(example: dict[str, Any]) -> str:
    row = example["row"]
    return str(row.get("gameDateTime") or row.get("gameId") or "")


def regression_metrics(values: list[tuple[float, float]], hit_threshold: float | None = None) -> dict[str, float]:
    errors = [predicted - actual for predicted, actual in values]
    predictions = [predicted for predicted, _ in values]
    actuals = [actual for _, actual in values]
    metrics = {
        "mae": round(sum(abs(error) for error in errors) / len(errors), 4),
        "rmse": round(math.sqrt(sum(error * error for error in errors) / len(errors)), 4),
        "mean_prediction": round(sum(predictions) / len(predictions), 4),
        "mean_actual": round(sum(actuals) / len(actuals), 4),
    }
    if hit_threshold is not None:
        metrics["hit_threshold"] = float(hit_threshold)
        metrics["hit_rate"] = round(
            sum(1 for error in errors if abs(error) <= hit_threshold) / len(errors),
            4,
        )
    return metrics


def number(value: Any) -> float | None:
    if value is None:
        return None
    if isinstance(value, bool):
        return 1.0 if value else 0.0
    if isinstance(value, (int, float)):
        return float(value)
    return None


def feature_average(features: dict[str, float | str], stat: str, fallback: float) -> float:
    candidates = [
        numeric_feature(features, f"last_5_{stat}_avg"),
        numeric_feature(features, f"last_3_{stat}_avg"),
        numeric_feature(features, f"season_{stat}_avg"),
    ]
    values = [value for value in candidates if value is not None]
    if not values and stat == "minutes":
        values = [value for value in (
            numeric_feature(features, "last_5_minutes_avg"),
            numeric_feature(features, "season_minutes_avg"),
        ) if value is not None]
    return sum(values) / len(values) if values else fallback


def risk_level(features: dict[str, float | str]) -> str:
    games_played = numeric_feature(features, "games_played_prior", 0)
    minutes_trend = abs(numeric_feature(features, "recent_minutes_trend", numeric_feature(features, "minutes_trend", 0)))
    rest_risk = numeric_feature(features, "rest_management_risk", 0)
    volatility = numeric_feature(features, "fantasy_volatility_score", 0)
    career_stage = features.get("career_stage")
    if games_played < 3 or minutes_trend >= 8 or rest_risk >= 0.6 or volatility >= 14:
        return "high"
    if games_played < 10 or minutes_trend >= 4 or rest_risk >= 0.3 or volatility >= 8 or career_stage == "rookie":
        return "medium"
    return "low"


def confidence_score(features: dict[str, float | str], trained: bool, trained_rows: int) -> float:
    base = 0.55 if trained else 0.4
    sample_bonus = min(trained_rows, 5000) / 20000
    experience_bonus = min(numeric_feature(features, "games_played_prior", 0), 30) / 150
    context_penalty = min(numeric_feature(features, "rest_management_risk", 0), 0.4) / 2
    volatility_penalty = min(numeric_feature(features, "fantasy_volatility_score", 0), 20) / 200
    risk_penalty = {"low": 0.0, "medium": 0.08, "high": 0.16}[risk_level(features)]
    return round(min(0.95, max(0.05, base + sample_bonus + experience_bonus - risk_penalty - context_penalty - volatility_penalty)), 4)


def factors(raw_features: dict[str, Any]) -> list[dict[str, Any]]:
    preferred = (
        "last_5_points_avg",
        "season_points_avg",
        "last_5_rebounds_avg",
        "last_5_assists_avg",
        "last_5_minutes_avg",
        "minutes_trend",
        "recent_minutes_trend",
        "days_rest",
        "projected_starter",
        "player_changed_team_before_game",
        "team_missing_starters_count",
        "team_roster_turnover_score",
        "team_minutes_vacated_by_departures",
        "team_usage_vacated_by_departures",
        "teammate_injury_usage_boost",
        "teammate_injury_minutes_boost",
        "age_at_game",
        "career_stage",
        "rest_management_risk",
        "injury_history_count_before_game",
        "fantasy_volatility_score",
        "is_home",
        "opponent_points_allowed_avg",
    )
    return [
        {"name": key, "value": raw_features[key]}
        for key in preferred
        if key in raw_features and raw_features[key] is not None
    ][:8]


def clamp_round(value: float) -> float:
    return round(max(0.0, value), 2)


def numeric_feature(features: dict[str, float | str], key: str, fallback: float | None = None) -> float | None:
    value = features.get(key)
    return value if isinstance(value, (int, float)) else fallback

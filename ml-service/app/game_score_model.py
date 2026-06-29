from __future__ import annotations

from dataclasses import dataclass, field
import math
from pathlib import Path
from typing import Any

import joblib
from sklearn.feature_extraction import DictVectorizer
from sklearn.impute import SimpleImputer
from sklearn.linear_model import Ridge
from sklearn.pipeline import Pipeline

from app.baseline_model import (
    clean_features,
    example_time,
    numeric_feature,
    regression_metrics,
    sample_weights,
    split_time_groups,
)


TARGETS = (
    ("home_team_score", "homeScore"),
    ("away_team_score", "awayScore"),
)

HIT_THRESHOLDS = {
    "home_team_score": 10,
    "away_team_score": 10,
}

MODEL_VERSION = "game-score-baseline-v2"
MIN_PLAUSIBLE_SCORE = 60.0
MAX_PLAUSIBLE_SCORE = 180.0


@dataclass
class GameScoreBaselineModel:
    pipeline: Pipeline | None = None
    fallback_targets: dict[str, float] = field(default_factory=dict)
    trained_rows: int = 0
    model_version: str = MODEL_VERSION
    recency_halflife_days: float | None = None

    @classmethod
    def fit(cls, rows: list[dict[str, Any]], recency_halflife_days: float | None = None) -> "GameScoreBaselineModel":
        examples = complete_training_examples(rows)
        if not examples:
            raise ValueError("No complete game-score training rows were provided")
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
            raise ValueError("At least two complete game-score training rows are required for evaluation")

        train_examples, test_examples, train_groups, test_groups = split_time_groups(examples, train_ratio)
        model = cls._fit_examples(train_examples, recency_halflife_days)

        values_by_target: dict[str, list[tuple[float, float]]] = {output_name: [] for output_name, _ in TARGETS}
        baseline_values: dict[str, dict[str, list[tuple[float, float]]]] = {
            "feature_average": {output_name: [] for output_name, _ in TARGETS},
            "training_mean": {output_name: [] for output_name, _ in TARGETS},
        }
        for example in test_examples:
            prediction = model.predict(example["features"], example["row"].get("homeTeamId"), example["row"].get("awayTeamId"))
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
    def _fit_examples(cls, examples: list[dict[str, Any]], recency_halflife_days: float | None = None) -> "GameScoreBaselineModel":
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
    def load(cls, artifact_path: Path) -> "GameScoreBaselineModel":
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

    def predict(self, raw_features: dict[str, Any], home_team_id: int | None, away_team_id: int | None) -> dict[str, Any]:
        features = clean_features(raw_features)
        if self.pipeline is None:
            raw_scores = self._fallback_prediction(features)
        else:
            values = self.pipeline.predict([features])[0]
            raw_scores = {
                output_name: max(0.0, float(values[index]))
                for index, (output_name, _) in enumerate(TARGETS)
            }

        scores = legal_basketball_scores(plausible_scores(raw_scores, features, self.fallback_targets), features)
        point_differential = scores["home_team_score"] - scores["away_team_score"]
        winner = home_team_id if point_differential > 0 else away_team_id
        return {
            **scores,
            "predicted_winner_team_id": winner,
            "point_differential": float(point_differential),
            "confidence_score": confidence_score(features, self.pipeline is not None, self.trained_rows, abs(point_differential)),
            "factors": factors(raw_features),
        }

    def _fallback_prediction(self, features: dict[str, float | str]) -> dict[str, float]:
        home = score_average(features, "home", self.fallback_targets.get("home_team_score", 110.0))
        away = score_average(features, "away", self.fallback_targets.get("away_team_score", 108.0))
        return {
            "home_team_score": max(0.0, home),
            "away_team_score": max(0.0, away),
        }


def complete_training_examples(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    examples: list[dict[str, Any]] = []
    for row in sorted(rows, key=training_row_sort_key):
        target = row.get("targets", {})
        values = [target.get(source_name) for _, source_name in TARGETS]
        if any(value is None for value in values):
            continue
        examples.append({
            "features": clean_features(row.get("features", {})),
            "targets": [float(value) for value in values],
            "row": row,
        })
    return examples


def training_row_sort_key(row: dict[str, Any]) -> tuple[str, str]:
    return (
        str(row.get("gameDateTime") or ""),
        str(row.get("gameId") or ""),
    )


def score_average(features: dict[str, float | str], side: str, fallback: float) -> float:
    candidates = [
        numeric_feature(features, f"{side}_last_5_team_score_avg"),
        numeric_feature(features, f"{side}_last_3_team_score_avg"),
        numeric_feature(features, f"{side}_season_team_score_avg"),
        numeric_feature(features, f"{side}_home_team_score_avg"),
        numeric_feature(features, f"{side}_away_team_score_avg"),
    ]
    values = [value for value in candidates if value is not None]
    return sum(values) / len(values) if values else fallback


def plausible_scores(
        raw_scores: dict[str, float],
        features: dict[str, float | str],
        fallback_targets: dict[str, float]) -> dict[str, float]:
    return {
        "home_team_score": plausible_score(
            raw_scores["home_team_score"],
            score_average(features, "home", fallback_targets.get("home_team_score", 110.0))),
        "away_team_score": plausible_score(
            raw_scores["away_team_score"],
            score_average(features, "away", fallback_targets.get("away_team_score", 108.0))),
    }


def plausible_score(value: float, fallback: float) -> float:
    if math.isfinite(value) and MIN_PLAUSIBLE_SCORE <= value <= MAX_PLAUSIBLE_SCORE:
        return value
    return fallback


def legal_basketball_scores(raw_scores: dict[str, float], features: dict[str, float | str]) -> dict[str, float]:
    home = whole_score(raw_scores["home_team_score"])
    away = whole_score(raw_scores["away_team_score"])
    if home != away:
        return {
            "home_team_score": float(home),
            "away_team_score": float(away),
        }

    raw_margin = raw_scores["home_team_score"] - raw_scores["away_team_score"]
    if raw_margin == 0:
        raw_margin = tiebreak_margin(features)
    if raw_margin > 0:
        home += 1
    else:
        away += 1
    return {
        "home_team_score": float(home),
        "away_team_score": float(away),
    }


def whole_score(value: float) -> int:
    return int(max(0.0, value) + 0.5)


def tiebreak_margin(features: dict[str, float | str]) -> float:
    signals = (
        numeric_feature(features, "season_point_differential_delta", 0),
        numeric_feature(features, "last_5_point_differential_delta", 0),
        numeric_feature(features, "home_season_point_differential_avg", 0)
        - numeric_feature(features, "away_season_point_differential_avg", 0),
        numeric_feature(features, "home_games_played_prior", 0)
        - numeric_feature(features, "away_games_played_prior", 0),
    )
    for signal in signals:
        if signal != 0:
            return signal
    return -1.0


def confidence_score(features: dict[str, float | str], trained: bool, trained_rows: int, spread: float) -> float:
    base = 0.58 if trained else 0.42
    sample_bonus = min(trained_rows, 5000) / 25000
    context_bonus = min(
        numeric_feature(features, "home_games_played_prior", 0)
        + numeric_feature(features, "away_games_played_prior", 0),
        40,
    ) / 200
    spread_bonus = min(spread, 20) / 200
    return round(min(0.95, max(0.05, base + sample_bonus + context_bonus + spread_bonus)), 4)


def factors(raw_features: dict[str, Any]) -> list[dict[str, Any]]:
    preferred = (
        "home_last_5_team_score_avg",
        "away_last_5_team_score_avg",
        "home_season_point_differential_avg",
        "away_season_point_differential_avg",
        "season_point_differential_delta",
        "last_5_point_differential_delta",
        "home_days_rest",
        "away_days_rest",
        "home_team_missing_starters_count",
        "away_team_missing_starters_count",
        "home_average_team_age",
        "away_average_team_age",
    )
    return [
        {"name": key, "value": raw_features[key]}
        for key in preferred
        if key in raw_features and raw_features[key] is not None
    ][:8]

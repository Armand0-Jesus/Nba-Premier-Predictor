from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import joblib
from sklearn.feature_extraction import DictVectorizer
from sklearn.impute import SimpleImputer
from sklearn.linear_model import Ridge
from sklearn.pipeline import Pipeline


TARGETS = (
    ("projected_points", "points"),
    ("projected_rebounds", "rebounds"),
    ("projected_assists", "assists"),
    ("projected_minutes", "minutes"),
    ("fantasy_points", "fantasyPoints"),
)


@dataclass
class PlayerBaselineModel:
    pipeline: Pipeline | None = None
    fallback_targets: dict[str, float] = field(default_factory=dict)
    trained_rows: int = 0
    model_version: str = "player-baseline-v1"

    @classmethod
    def fit(cls, rows: list[dict[str, Any]]) -> "PlayerBaselineModel":
        features: list[dict[str, float]] = []
        targets: list[list[float]] = []

        for row in rows:
            target = row.get("targets", {})
            values = [number(target.get(source_name)) for _, source_name in TARGETS]
            if any(value is None for value in values):
                continue
            features.append(clean_features(row.get("features", {})))
            targets.append([float(value) for value in values])

        if not targets:
            raise ValueError("No complete player training rows were provided")

        pipeline = Pipeline([
            ("features", DictVectorizer(sparse=False)),
            ("imputer", SimpleImputer(strategy="constant", fill_value=0.0)),
            ("model", Ridge(alpha=1.0)),
        ])
        pipeline.fit(features, targets)

        fallback_targets = {}
        for index, (output_name, _) in enumerate(TARGETS):
            fallback_targets[output_name] = round(sum(row[index] for row in targets) / len(targets), 2)

        return cls(pipeline=pipeline, fallback_targets=fallback_targets, trained_rows=len(targets))

    @classmethod
    def load(cls, artifact_path: Path) -> "PlayerBaselineModel":
        if not artifact_path.exists():
            return cls()
        artifact = joblib.load(artifact_path)
        return cls(
            pipeline=artifact.get("pipeline"),
            fallback_targets=artifact.get("fallback_targets", {}),
            trained_rows=artifact.get("trained_rows", 0),
            model_version=artifact.get("model_version", "player-baseline-v1"),
        )

    def save(self, artifact_path: Path) -> None:
        artifact_path.parent.mkdir(parents=True, exist_ok=True)
        joblib.dump({
            "pipeline": self.pipeline,
            "fallback_targets": self.fallback_targets,
            "trained_rows": self.trained_rows,
            "model_version": self.model_version,
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

        predictions["fantasy_floor"] = clamp_round(predictions["fantasy_points"] * 0.85)
        predictions["fantasy_ceiling"] = clamp_round(predictions["fantasy_points"] * 1.15)
        predictions["risk_level"] = risk_level(features)
        predictions["confidence_score"] = confidence_score(features, self.pipeline is not None, self.trained_rows)
        predictions["factors"] = factors(raw_features)
        return predictions

    def _fallback_prediction(self, features: dict[str, float]) -> dict[str, float]:
        points = feature_average(features, "points", self.fallback_targets.get("projected_points", 0.0))
        rebounds = feature_average(features, "rebounds", self.fallback_targets.get("projected_rebounds", 0.0))
        assists = feature_average(features, "assists", self.fallback_targets.get("projected_assists", 0.0))
        minutes = feature_average(features, "minutes", self.fallback_targets.get("projected_minutes", 0.0))
        fantasy = points + (1.2 * rebounds) + (1.5 * assists)
        return {
            "projected_points": clamp_round(points),
            "projected_rebounds": clamp_round(rebounds),
            "projected_assists": clamp_round(assists),
            "projected_minutes": clamp_round(minutes),
            "fantasy_points": clamp_round(fantasy),
        }


def clean_features(raw_features: dict[str, Any]) -> dict[str, float]:
    clean: dict[str, float] = {}
    for key, value in raw_features.items():
        parsed = number(value)
        if parsed is not None:
            clean[key] = parsed
    return clean


def number(value: Any) -> float | None:
    if value is None:
        return None
    if isinstance(value, bool):
        return 1.0 if value else 0.0
    if isinstance(value, (int, float)):
        return float(value)
    return None


def feature_average(features: dict[str, float], stat: str, fallback: float) -> float:
    candidates = [
        features.get(f"last_5_{stat}_avg"),
        features.get(f"last_3_{stat}_avg"),
        features.get(f"season_{stat}_avg"),
    ]
    values = [value for value in candidates if value is not None]
    if not values and stat == "minutes":
        values = [value for value in (
            features.get("last_5_minutes_avg"),
            features.get("season_minutes_avg"),
        ) if value is not None]
    return sum(values) / len(values) if values else fallback


def risk_level(features: dict[str, float]) -> str:
    games_played = features.get("games_played_prior", 0)
    minutes_trend = abs(features.get("minutes_trend", 0))
    if games_played < 3 or minutes_trend >= 8:
        return "high"
    if games_played < 10 or minutes_trend >= 4:
        return "medium"
    return "low"


def confidence_score(features: dict[str, float], trained: bool, trained_rows: int) -> float:
    base = 0.55 if trained else 0.4
    sample_bonus = min(trained_rows, 5000) / 20000
    experience_bonus = min(features.get("games_played_prior", 0), 30) / 150
    risk_penalty = {"low": 0.0, "medium": 0.08, "high": 0.16}[risk_level(features)]
    return round(min(0.95, max(0.05, base + sample_bonus + experience_bonus - risk_penalty)), 4)


def factors(raw_features: dict[str, Any]) -> list[dict[str, Any]]:
    preferred = (
        "last_5_points_avg",
        "season_points_avg",
        "last_5_rebounds_avg",
        "last_5_assists_avg",
        "last_5_minutes_avg",
        "minutes_trend",
        "days_rest",
        "is_home",
        "opponent_points_allowed_avg",
    )
    return [
        {"name": key, "value": raw_features[key]}
        for key in preferred
        if key in raw_features and raw_features[key] is not None
    ][:6]


def clamp_round(value: float) -> float:
    return round(max(0.0, value), 2)

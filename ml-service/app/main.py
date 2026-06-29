from __future__ import annotations

import json
import os
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Annotated, Any

from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel, Field

from app.baseline_model import PlayerBaselineModel
from app.game_score_model import GameScoreBaselineModel


BACKEND_API_URL = os.getenv("BACKEND_API_URL", "http://localhost:8080").rstrip("/")
DEFAULT_ARTIFACT_PATH = Path(__file__).resolve().parents[1] / "artifacts" / "player_baseline.joblib"
MODEL_ARTIFACT_PATH = Path(os.getenv("MODEL_ARTIFACT_PATH", str(DEFAULT_ARTIFACT_PATH)))
DEFAULT_GAME_SCORE_ARTIFACT_PATH = Path(__file__).resolve().parents[1] / "artifacts" / "game_score_baseline.joblib"
GAME_SCORE_ARTIFACT_PATH = Path(os.getenv("GAME_SCORE_ARTIFACT_PATH", str(DEFAULT_GAME_SCORE_ARTIFACT_PATH)))
CANDIDATE_ARTIFACT_DIR = Path(os.getenv(
    "CANDIDATE_ARTIFACT_DIR",
    str(Path(__file__).resolve().parents[1] / "artifacts" / "candidates"),
))
PLAYER_METRICS_PATH = Path(os.getenv(
    "PLAYER_METRICS_PATH",
    str(MODEL_ARTIFACT_PATH.with_name("player_metrics.json")),
))
GAME_SCORE_METRICS_PATH = Path(os.getenv(
    "GAME_SCORE_METRICS_PATH",
    str(GAME_SCORE_ARTIFACT_PATH.with_name("game_score_metrics.json")),
))
BACKEND_PAGE_SIZE = 10000
DEFAULT_TRAINING_ROWS = 100000
MAX_TRAINING_ROWS = 500000
DEFAULT_RECENCY_HALFLIFE_DAYS = 1095.0


def load_json(path: Path) -> dict[str, Any] | None:
    if not path.exists():
        return None
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    return data if isinstance(data, dict) else None


def save_json(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2, sort_keys=True)


app = FastAPI(title="NBA Premier Predictor ML Service", version="0.1.0")
app.state.player_model = PlayerBaselineModel.load(MODEL_ARTIFACT_PATH)
app.state.player_metrics = load_json(PLAYER_METRICS_PATH)
app.state.game_score_model = GameScoreBaselineModel.load(GAME_SCORE_ARTIFACT_PATH)
app.state.game_score_metrics = load_json(GAME_SCORE_METRICS_PATH)


class PlayerPredictionRequest(BaseModel):
    game_id: int | None = None
    player_id: int = Field(gt=0)
    team_id: int | None = None
    data_cutoff_time: str | None = None
    features: dict[str, Any] = Field(min_length=1)


class PlayerPredictionResponse(BaseModel):
    model_version: str
    trained_rows: int
    game_id: int | None
    player_id: int
    team_id: int | None
    projected_points: float
    projected_rebounds: float
    projected_assists: float
    projected_minutes: float
    projected_steals: float
    projected_blocks: float
    projected_turnovers: float
    projected_field_goals_made: float
    projected_field_goals_attempted: float
    projected_field_goal_percentage: float
    fantasy_points: float
    fantasy_floor: float
    fantasy_ceiling: float
    confidence_score: float
    risk_level: str
    factors: list[dict[str, Any]]


class GameScorePredictionRequest(BaseModel):
    game_id: int = Field(gt=0)
    home_team_id: int = Field(gt=0)
    away_team_id: int = Field(gt=0)
    data_cutoff_time: str | None = None
    features: dict[str, Any] = Field(min_length=1)


class GameScorePredictionResponse(BaseModel):
    model_version: str
    trained_rows: int
    game_id: int
    home_team_id: int
    away_team_id: int
    home_team_score: float
    away_team_score: float
    predicted_winner_team_id: int | None
    point_differential: float
    confidence_score: float
    factors: list[dict[str, Any]]


class TrainingResponse(BaseModel):
    model_version: str
    trained_rows: int
    artifact_path: str
    season: int | None = None
    start_season: int | None = None
    end_season: int | None = None
    recency_halflife_days: float | None = None


class EvaluationResponse(BaseModel):
    model_version: str
    train_rows: int
    test_rows: int
    total_rows: int
    train_ratio: float
    recency_halflife_days: float | None = None
    split_strategy: str | None = None
    train_groups: int | None = None
    test_groups: int | None = None
    training_data_start: str | None
    training_data_end: str | None
    validation_data_start: str | None
    validation_data_end: str | None
    metrics: dict[str, dict[str, float]]
    baseline_metrics: dict[str, dict[str, dict[str, float]]] = Field(default_factory=dict)


class PromoteModelRequest(BaseModel):
    model_type: str
    artifact_path: str


class PromoteModelResponse(BaseModel):
    model_type: str
    model_version: str
    trained_rows: int
    artifact_path: str


@app.get("/health")
def health() -> dict[str, Any]:
    player_model: PlayerBaselineModel = app.state.player_model
    game_score_model: GameScoreBaselineModel = app.state.game_score_model
    return {
        "status": "ok",
        "modelVersion": player_model.model_version,
        "trainedRows": player_model.trained_rows,
        "models": {
            "player": model_health(player_model),
            "gameScore": model_health(game_score_model),
        },
    }


@app.post("/train/player", response_model=TrainingResponse)
@app.post("/train/player-baseline", response_model=TrainingResponse)
def train_player_baseline(
        season: int | None = None,
        start_season: Annotated[int | None, Query(alias="startSeason")] = None,
        end_season: Annotated[int | None, Query(alias="endSeason")] = None,
        limit: Annotated[int, Query(ge=1, le=MAX_TRAINING_ROWS)] = DEFAULT_TRAINING_ROWS,
        recency_halflife_days: Annotated[float | None, Query(alias="recencyHalflifeDays", ge=0)] = DEFAULT_RECENCY_HALFLIFE_DAYS,
        version_name: Annotated[str | None, Query(alias="versionName")] = None,
        activate: bool = True) -> TrainingResponse:
    validate_training_window(season, start_season, end_season)
    rows = fetch_player_training_rows(season, limit, start_season, end_season)
    try:
        model = PlayerBaselineModel.fit(rows, normalized_halflife(recency_halflife_days))
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex

    if version_name:
        model.model_version = version_name
    artifact_path = MODEL_ARTIFACT_PATH if activate else candidate_artifact_path("player", model.model_version)
    model.save(artifact_path)
    if activate:
        app.state.player_model = model
    return TrainingResponse(
        model_version=model.model_version,
        trained_rows=model.trained_rows,
        artifact_path=str(artifact_path),
        season=season,
        start_season=start_season,
        end_season=end_season,
        recency_halflife_days=model.recency_halflife_days,
    )


@app.post("/train/game-score", response_model=TrainingResponse)
def train_game_score_baseline(
        season: int | None = None,
        start_season: Annotated[int | None, Query(alias="startSeason")] = None,
        end_season: Annotated[int | None, Query(alias="endSeason")] = None,
        limit: Annotated[int, Query(ge=1, le=MAX_TRAINING_ROWS)] = DEFAULT_TRAINING_ROWS,
        recency_halflife_days: Annotated[float | None, Query(alias="recencyHalflifeDays", ge=0)] = DEFAULT_RECENCY_HALFLIFE_DAYS,
        version_name: Annotated[str | None, Query(alias="versionName")] = None,
        activate: bool = True) -> TrainingResponse:
    validate_training_window(season, start_season, end_season)
    rows = fetch_game_score_training_rows(season, limit, start_season, end_season)
    try:
        model = GameScoreBaselineModel.fit(rows, normalized_halflife(recency_halflife_days))
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex

    if version_name:
        model.model_version = version_name
    artifact_path = GAME_SCORE_ARTIFACT_PATH if activate else candidate_artifact_path("game-score", model.model_version)
    model.save(artifact_path)
    if activate:
        app.state.game_score_model = model
    return TrainingResponse(
        model_version=model.model_version,
        trained_rows=model.trained_rows,
        artifact_path=str(artifact_path),
        season=season,
        start_season=start_season,
        end_season=end_season,
        recency_halflife_days=model.recency_halflife_days,
    )


@app.post("/evaluate/player-baseline", response_model=EvaluationResponse)
def evaluate_player_baseline(
        season: int | None = None,
        start_season: Annotated[int | None, Query(alias="startSeason")] = None,
        end_season: Annotated[int | None, Query(alias="endSeason")] = None,
        limit: Annotated[int, Query(ge=2, le=MAX_TRAINING_ROWS)] = DEFAULT_TRAINING_ROWS,
        train_ratio: Annotated[float, Query(gt=0, lt=1)] = 0.8,
        recency_halflife_days: Annotated[float | None, Query(alias="recencyHalflifeDays", ge=0)] = DEFAULT_RECENCY_HALFLIFE_DAYS) -> EvaluationResponse:
    validate_training_window(season, start_season, end_season)
    rows = fetch_player_training_rows(season, limit, start_season, end_season)
    try:
        metrics = PlayerBaselineModel.evaluate_time_split(rows, train_ratio, normalized_halflife(recency_halflife_days))
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex

    app.state.player_metrics = metrics
    save_json(PLAYER_METRICS_PATH, metrics)
    return EvaluationResponse(**metrics)


@app.post("/evaluate/game-score", response_model=EvaluationResponse)
def evaluate_game_score_baseline(
        season: int | None = None,
        start_season: Annotated[int | None, Query(alias="startSeason")] = None,
        end_season: Annotated[int | None, Query(alias="endSeason")] = None,
        limit: Annotated[int, Query(ge=2, le=MAX_TRAINING_ROWS)] = DEFAULT_TRAINING_ROWS,
        train_ratio: Annotated[float, Query(gt=0, lt=1)] = 0.8,
        recency_halflife_days: Annotated[float | None, Query(alias="recencyHalflifeDays", ge=0)] = DEFAULT_RECENCY_HALFLIFE_DAYS) -> EvaluationResponse:
    validate_training_window(season, start_season, end_season)
    rows = fetch_game_score_training_rows(season, limit, start_season, end_season)
    try:
        metrics = GameScoreBaselineModel.evaluate_time_split(rows, train_ratio, normalized_halflife(recency_halflife_days))
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex

    app.state.game_score_metrics = metrics
    save_json(GAME_SCORE_METRICS_PATH, metrics)
    return EvaluationResponse(**metrics)


@app.post("/evaluate/player-baseline/windows")
def evaluate_player_baseline_windows(
        windows: Annotated[str, Query()] = "2014:2025,2019:2025,2021:2025",
        limit: Annotated[int, Query(ge=2, le=MAX_TRAINING_ROWS)] = DEFAULT_TRAINING_ROWS,
        train_ratio: Annotated[float, Query(gt=0, lt=1)] = 0.8,
        recency_halflife_days: Annotated[float | None, Query(alias="recencyHalflifeDays", ge=0)] = DEFAULT_RECENCY_HALFLIFE_DAYS) -> dict[str, Any]:
    return evaluate_windows(
        "player",
        parse_windows(windows),
        limit,
        train_ratio,
        normalized_halflife(recency_halflife_days),
    )


@app.post("/evaluate/game-score/windows")
def evaluate_game_score_windows(
        windows: Annotated[str, Query()] = "2014:2025,2019:2025,2021:2025",
        limit: Annotated[int, Query(ge=2, le=MAX_TRAINING_ROWS)] = DEFAULT_TRAINING_ROWS,
        train_ratio: Annotated[float, Query(gt=0, lt=1)] = 0.8,
        recency_halflife_days: Annotated[float | None, Query(alias="recencyHalflifeDays", ge=0)] = DEFAULT_RECENCY_HALFLIFE_DAYS) -> dict[str, Any]:
    return evaluate_windows(
        "gameScore",
        parse_windows(windows),
        limit,
        train_ratio,
        normalized_halflife(recency_halflife_days),
    )


@app.post("/predict/player", response_model=PlayerPredictionResponse)
@app.post("/predict/player-stats", response_model=PlayerPredictionResponse)
def predict_player_stats(request: PlayerPredictionRequest) -> PlayerPredictionResponse:
    model: PlayerBaselineModel = app.state.player_model
    prediction = model.predict(request.features)
    return PlayerPredictionResponse(
        model_version=model.model_version,
        trained_rows=model.trained_rows,
        game_id=request.game_id,
        player_id=request.player_id,
        team_id=request.team_id,
        **prediction,
    )


@app.post("/predict/fantasy", response_model=PlayerPredictionResponse)
def predict_fantasy(request: PlayerPredictionRequest) -> PlayerPredictionResponse:
    return predict_player_stats(request)


@app.post("/predict/game-score", response_model=GameScorePredictionResponse)
def predict_game_score(request: GameScorePredictionRequest) -> GameScorePredictionResponse:
    model: GameScoreBaselineModel = app.state.game_score_model
    prediction = model.predict(request.features, request.home_team_id, request.away_team_id)
    return GameScorePredictionResponse(
        model_version=model.model_version,
        trained_rows=model.trained_rows,
        game_id=request.game_id,
        home_team_id=request.home_team_id,
        away_team_id=request.away_team_id,
        **prediction,
    )


@app.get("/model/metrics")
def model_metrics() -> dict[str, Any]:
    model: PlayerBaselineModel = app.state.player_model
    game_score_model: GameScoreBaselineModel = app.state.game_score_model
    return {
        "modelVersion": model.model_version,
        "trainedRows": model.trained_rows,
        "playerBaseline": app.state.player_metrics,
        "gameScoreModelVersion": game_score_model.model_version,
        "gameScoreTrainedRows": game_score_model.trained_rows,
        "gameScoreBaseline": app.state.game_score_metrics,
    }


@app.get("/model/versions")
def model_versions() -> dict[str, Any]:
    model: PlayerBaselineModel = app.state.player_model
    game_score_model: GameScoreBaselineModel = app.state.game_score_model
    return {
        "activeModel": {
            "versionName": model.model_version,
            "modelType": "ridge-regression",
            "targetVariables": [
                "points",
                "rebounds",
                "assists",
                "minutes",
                "steals",
                "blocks",
                "turnovers",
                "fantasyPoints",
                "fieldGoalsMade",
                "fieldGoalsAttempted",
            ],
            "trainedRows": model.trained_rows,
            "artifactPath": str(MODEL_ARTIFACT_PATH),
            "recencyHalflifeDays": model.recency_halflife_days,
        },
        "gameScoreModel": {
            "versionName": game_score_model.model_version,
            "modelType": "ridge-regression",
            "targetVariables": [
                "homeScore",
                "awayScore",
            ],
            "trainedRows": game_score_model.trained_rows,
            "artifactPath": str(GAME_SCORE_ARTIFACT_PATH),
            "recencyHalflifeDays": game_score_model.recency_halflife_days,
        }
    }


@app.get("/model/active")
def active_models() -> dict[str, Any]:
    return model_versions()


@app.post("/model/promote", response_model=PromoteModelResponse)
def promote_model(request: PromoteModelRequest) -> PromoteModelResponse:
    artifact_path = Path(request.artifact_path)
    if request.model_type == "player":
        model = PlayerBaselineModel.load(artifact_path)
        if model.trained_rows <= 0:
            raise HTTPException(status_code=400, detail="Player artifact is not a trained compatible model")
        model.save(MODEL_ARTIFACT_PATH)
        app.state.player_model = model
        return PromoteModelResponse(
            model_type="player",
            model_version=model.model_version,
            trained_rows=model.trained_rows,
            artifact_path=str(MODEL_ARTIFACT_PATH),
        )
    if request.model_type in {"gameScore", "game-score", "game_score"}:
        model = GameScoreBaselineModel.load(artifact_path)
        if model.trained_rows <= 0:
            raise HTTPException(status_code=400, detail="Game-score artifact is not a trained compatible model")
        model.save(GAME_SCORE_ARTIFACT_PATH)
        app.state.game_score_model = model
        return PromoteModelResponse(
            model_type="gameScore",
            model_version=model.model_version,
            trained_rows=model.trained_rows,
            artifact_path=str(GAME_SCORE_ARTIFACT_PATH),
        )
    raise HTTPException(status_code=400, detail="model_type must be player or gameScore")


def validate_training_window(season: int | None, start_season: int | None, end_season: int | None) -> None:
    if season is not None and (start_season is not None or end_season is not None):
        raise HTTPException(status_code=400, detail="Use either season or startSeason/endSeason, not both")
    if start_season is not None and end_season is not None and start_season > end_season:
        raise HTTPException(status_code=400, detail="startSeason must be before or equal to endSeason")


def normalized_halflife(value: float | None) -> float | None:
    return None if value is None or value <= 0 else value


def parse_windows(raw_windows: str) -> list[tuple[int, int]]:
    windows: list[tuple[int, int]] = []
    for raw_window in raw_windows.split(","):
        value = raw_window.strip()
        if not value:
            continue
        separator = ":" if ":" in value else "-"
        parts = value.split(separator, 1)
        if len(parts) != 2:
            raise HTTPException(status_code=400, detail=f"Invalid window '{value}'")
        try:
            start_season = int(parts[0].strip())
            end_season = int(parts[1].strip())
        except ValueError as ex:
            raise HTTPException(status_code=400, detail=f"Invalid window '{value}'") from ex
        if start_season > end_season:
            raise HTTPException(status_code=400, detail=f"Invalid window '{value}'")
        windows.append((start_season, end_season))
    if not windows:
        raise HTTPException(status_code=400, detail="At least one window is required")
    return windows


def evaluate_windows(
        model_type: str,
        windows: list[tuple[int, int]],
        limit: int,
        train_ratio: float,
        recency_halflife_days: float | None) -> dict[str, Any]:
    results: list[dict[str, Any]] = []
    for start_season, end_season in windows:
        try:
            if model_type == "player":
                rows = fetch_player_training_rows(None, limit, start_season, end_season)
                metrics = PlayerBaselineModel.evaluate_time_split(rows, train_ratio, recency_halflife_days)
            else:
                rows = fetch_game_score_training_rows(None, limit, start_season, end_season)
                metrics = GameScoreBaselineModel.evaluate_time_split(rows, train_ratio, recency_halflife_days)
            results.append({
                "label": season_window_label(start_season, end_season),
                "startSeason": start_season,
                "endSeason": end_season,
                "primaryScore": primary_score(metrics),
                "metrics": metrics,
            })
        except (HTTPException, ValueError) as ex:
            detail = ex.detail if isinstance(ex, HTTPException) else str(ex)
            results.append({
                "label": season_window_label(start_season, end_season),
                "startSeason": start_season,
                "endSeason": end_season,
                "error": detail,
            })

    successful = [result for result in results if "primaryScore" in result]
    if not successful:
        raise HTTPException(status_code=400, detail="No evaluation windows had enough training data")
    best_window = min(successful, key=lambda result: result["primaryScore"])
    return {
        "modelType": model_type,
        "limit": limit,
        "trainRatio": train_ratio,
        "recencyHalflifeDays": recency_halflife_days,
        "windows": results,
        "bestWindow": best_window,
    }


def season_window_label(start_season: int, end_season: int) -> str:
    return f"{start_season}-{start_season + 1} to {end_season}-{end_season + 1}"


def primary_score(metrics: dict[str, Any]) -> float:
    maes = [
        values["mae"]
        for values in metrics.get("metrics", {}).values()
        if isinstance(values, dict) and "mae" in values
    ]
    if not maes:
        return 999999.0
    return round(sum(maes) / len(maes), 4)


def fetch_player_training_rows(
        season: int | None,
        limit: int,
        start_season: int | None = None,
        end_season: int | None = None) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    offset = 0
    remaining = limit
    while remaining > 0:
        page_limit = min(remaining, BACKEND_PAGE_SIZE)
        page = fetch_player_training_page(season, page_limit, offset, start_season, end_season)
        rows.extend(page)
        if len(page) < page_limit:
            break
        offset += len(page)
        remaining -= len(page)
    return rows


def fetch_game_score_training_rows(
        season: int | None,
        limit: int,
        start_season: int | None = None,
        end_season: int | None = None) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    offset = 0
    remaining = limit
    while remaining > 0:
        page_limit = min(remaining, BACKEND_PAGE_SIZE)
        page = fetch_game_score_training_page(season, page_limit, offset, start_season, end_season)
        rows.extend(page)
        if len(page) < page_limit:
            break
        offset += len(page)
        remaining -= len(page)
    return rows


def fetch_player_training_page(
        season: int | None,
        limit: int,
        offset: int,
        start_season: int | None = None,
        end_season: int | None = None) -> list[dict[str, Any]]:
    params: dict[str, Any] = {"limit": limit, "offset": offset}
    if season is not None:
        params["season"] = season
    if start_season is not None:
        params["startSeason"] = start_season
    if end_season is not None:
        params["endSeason"] = end_season
    url = f"{BACKEND_API_URL}/api/training-data/player-stats?{urllib.parse.urlencode(params)}"

    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            if response.status != 200:
                raise HTTPException(status_code=502, detail=f"Backend returned HTTP {response.status}")
            payload = response.read().decode("utf-8")
    except urllib.error.URLError as ex:
        raise HTTPException(status_code=502, detail=f"Could not fetch data: {ex}") from ex

    data = json.loads(payload)
    if not isinstance(data, list):
        raise HTTPException(status_code=502, detail="Backend training data response was not a list")
    return data


def fetch_game_score_training_page(
        season: int | None,
        limit: int,
        offset: int,
        start_season: int | None = None,
        end_season: int | None = None) -> list[dict[str, Any]]:
    params: dict[str, Any] = {"limit": limit, "offset": offset}
    if season is not None:
        params["season"] = season
    if start_season is not None:
        params["startSeason"] = start_season
    if end_season is not None:
        params["endSeason"] = end_season
    url = f"{BACKEND_API_URL}/api/training-data/game-scores?{urllib.parse.urlencode(params)}"

    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            if response.status != 200:
                raise HTTPException(status_code=502, detail=f"Backend returned HTTP {response.status}")
            payload = response.read().decode("utf-8")
    except urllib.error.URLError as ex:
        raise HTTPException(status_code=502, detail=f"Could not fetch data: {ex}") from ex

    data = json.loads(payload)
    if not isinstance(data, list):
        raise HTTPException(status_code=502, detail="Backend game-score training data response was not a list")
    return data


def model_health(model: PlayerBaselineModel | GameScoreBaselineModel) -> dict[str, Any]:
    return {
        "status": "trained" if model.trained_rows > 0 else "untrained",
        "modelVersion": model.model_version,
        "trainedRows": model.trained_rows,
        "recencyHalflifeDays": model.recency_halflife_days,
    }


def candidate_artifact_path(model_type: str, version_name: str) -> Path:
    safe_version = "".join(char if char.isalnum() or char in {"-", "_"} else "-" for char in version_name)
    return CANDIDATE_ARTIFACT_DIR / f"{model_type}_{safe_version}.joblib"

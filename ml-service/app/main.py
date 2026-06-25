from __future__ import annotations

import json
import os
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel, Field

from app.baseline_model import PlayerBaselineModel
from app.game_score_model import GameScoreBaselineModel


BACKEND_API_URL = os.getenv("BACKEND_API_URL", "http://localhost:8080").rstrip("/")
DEFAULT_ARTIFACT_PATH = Path(__file__).resolve().parents[1] / "artifacts" / "player_baseline.joblib"
MODEL_ARTIFACT_PATH = Path(os.getenv("MODEL_ARTIFACT_PATH", str(DEFAULT_ARTIFACT_PATH)))
DEFAULT_GAME_SCORE_ARTIFACT_PATH = Path(__file__).resolve().parents[1] / "artifacts" / "game_score_baseline.joblib"
GAME_SCORE_ARTIFACT_PATH = Path(os.getenv("GAME_SCORE_ARTIFACT_PATH", str(DEFAULT_GAME_SCORE_ARTIFACT_PATH)))
BACKEND_PAGE_SIZE = 10000
MAX_TRAINING_ROWS = 50000

app = FastAPI(title="NBA Premier Predictor ML Service", version="0.1.0")
app.state.player_model = PlayerBaselineModel.load(MODEL_ARTIFACT_PATH)
app.state.player_metrics = None
app.state.game_score_model = GameScoreBaselineModel.load(GAME_SCORE_ARTIFACT_PATH)
app.state.game_score_metrics = None


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


class EvaluationResponse(BaseModel):
    model_version: str
    train_rows: int
    test_rows: int
    total_rows: int
    train_ratio: float
    training_data_start: str | None
    training_data_end: str | None
    validation_data_start: str | None
    validation_data_end: str | None
    metrics: dict[str, dict[str, float]]


@app.get("/health")
def health() -> dict[str, Any]:
    model: PlayerBaselineModel = app.state.player_model
    return {
        "status": "ok",
        "modelVersion": model.model_version,
        "trainedRows": model.trained_rows,
    }


@app.post("/train/player", response_model=TrainingResponse)
@app.post("/train/player-baseline", response_model=TrainingResponse)
def train_player_baseline(
        season: int | None = None,
        limit: int = Query(default=10000, ge=1, le=MAX_TRAINING_ROWS)) -> TrainingResponse:
    rows = fetch_player_training_rows(season, limit)
    try:
        model = PlayerBaselineModel.fit(rows)
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex

    model.save(MODEL_ARTIFACT_PATH)
    app.state.player_model = model
    return TrainingResponse(
        model_version=model.model_version,
        trained_rows=model.trained_rows,
        artifact_path=str(MODEL_ARTIFACT_PATH),
    )


@app.post("/train/game-score", response_model=TrainingResponse)
def train_game_score_baseline(
        season: int | None = None,
        limit: int = Query(default=10000, ge=1, le=MAX_TRAINING_ROWS)) -> TrainingResponse:
    rows = fetch_game_score_training_rows(season, limit)
    try:
        model = GameScoreBaselineModel.fit(rows)
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex

    model.save(GAME_SCORE_ARTIFACT_PATH)
    app.state.game_score_model = model
    return TrainingResponse(
        model_version=model.model_version,
        trained_rows=model.trained_rows,
        artifact_path=str(GAME_SCORE_ARTIFACT_PATH),
    )


@app.post("/evaluate/player-baseline", response_model=EvaluationResponse)
def evaluate_player_baseline(
        season: int | None = None,
        limit: int = Query(default=10000, ge=2, le=MAX_TRAINING_ROWS),
        train_ratio: float = Query(default=0.8, gt=0, lt=1)) -> EvaluationResponse:
    rows = fetch_player_training_rows(season, limit)
    try:
        metrics = PlayerBaselineModel.evaluate_time_split(rows, train_ratio)
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex

    app.state.player_metrics = metrics
    return EvaluationResponse(**metrics)


@app.post("/evaluate/game-score", response_model=EvaluationResponse)
def evaluate_game_score_baseline(
        season: int | None = None,
        limit: int = Query(default=10000, ge=2, le=MAX_TRAINING_ROWS),
        train_ratio: float = Query(default=0.8, gt=0, lt=1)) -> EvaluationResponse:
    rows = fetch_game_score_training_rows(season, limit)
    try:
        metrics = GameScoreBaselineModel.evaluate_time_split(rows, train_ratio)
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex

    app.state.game_score_metrics = metrics
    return EvaluationResponse(**metrics)


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
                "fantasyPoints",
            ],
            "trainedRows": model.trained_rows,
            "artifactPath": str(MODEL_ARTIFACT_PATH),
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
        }
    }


def fetch_player_training_rows(season: int | None, limit: int) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    offset = 0
    remaining = limit
    while remaining > 0:
        page_limit = min(remaining, BACKEND_PAGE_SIZE)
        page = fetch_player_training_page(season, page_limit, offset)
        rows.extend(page)
        if len(page) < page_limit:
            break
        offset += len(page)
        remaining -= len(page)
    return rows


def fetch_game_score_training_rows(season: int | None, limit: int) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    offset = 0
    remaining = limit
    while remaining > 0:
        page_limit = min(remaining, BACKEND_PAGE_SIZE)
        page = fetch_game_score_training_page(season, page_limit, offset)
        rows.extend(page)
        if len(page) < page_limit:
            break
        offset += len(page)
        remaining -= len(page)
    return rows


def fetch_player_training_page(season: int | None, limit: int, offset: int) -> list[dict[str, Any]]:
    params: dict[str, Any] = {"limit": limit, "offset": offset}
    if season is not None:
        params["season"] = season
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


def fetch_game_score_training_page(season: int | None, limit: int, offset: int) -> list[dict[str, Any]]:
    params: dict[str, Any] = {"limit": limit, "offset": offset}
    if season is not None:
        params["season"] = season
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

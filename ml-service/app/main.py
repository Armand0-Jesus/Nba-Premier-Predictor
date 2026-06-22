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


BACKEND_API_URL = os.getenv("BACKEND_API_URL", "http://localhost:8080").rstrip("/")
DEFAULT_ARTIFACT_PATH = Path(__file__).resolve().parents[1] / "artifacts" / "player_baseline.joblib"
MODEL_ARTIFACT_PATH = Path(os.getenv("MODEL_ARTIFACT_PATH", str(DEFAULT_ARTIFACT_PATH)))

app = FastAPI(title="NBA Premier Predictor ML Service", version="0.1.0")
app.state.player_model = PlayerBaselineModel.load(MODEL_ARTIFACT_PATH)


class PlayerPredictionRequest(BaseModel):
    game_id: int | None = None
    player_id: int
    team_id: int | None = None
    data_cutoff_time: str | None = None
    features: dict[str, Any] = Field(default_factory=dict)


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


class TrainingResponse(BaseModel):
    model_version: str
    trained_rows: int
    artifact_path: str


@app.get("/health")
def health() -> dict[str, Any]:
    model: PlayerBaselineModel = app.state.player_model
    return {
        "status": "ok",
        "modelVersion": model.model_version,
        "trainedRows": model.trained_rows,
    }


@app.post("/train/player-baseline", response_model=TrainingResponse)
def train_player_baseline(
        season: int | None = None,
        limit: int = Query(default=10000, ge=1, le=10000)) -> TrainingResponse:
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


def fetch_player_training_rows(season: int | None, limit: int) -> list[dict[str, Any]]:
    params: dict[str, Any] = {"limit": limit}
    if season is not None:
        params["season"] = season
    url = f"{BACKEND_API_URL}/api/training-data/player-stats?{urllib.parse.urlencode(params)}"

    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            if response.status != 200:
                raise HTTPException(status_code=502, detail=f"Backend returned HTTP {response.status}")
            payload = response.read().decode("utf-8")
    except urllib.error.URLError as ex:
        raise HTTPException(status_code=502, detail=f"Could not fetch backend training data: {ex}") from ex

    data = json.loads(payload)
    if not isinstance(data, list):
        raise HTTPException(status_code=502, detail="Backend training data response was not a list")
    return data

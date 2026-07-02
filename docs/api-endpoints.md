# API endpoints

## Important notes

Base URL:
- Local: `http://localhost:8080`
- If deployed: use the deployed backend URL

Many of the endpoints endpoints are read only and safe for normal browsing, such as players, teams, games, standings projections, model metrics and prediction history.

Admin or expensive endpoints should be used more carefully, automated retraining takes more resources:
- `/api/features/*/generate`
- `/api/model/evaluate`
- `/api/model/retrain`
- `/api/model/promote/{modelVersionId}`
- `/api/model/rollback/{modelVersionId}`
- `/api/context/refresh`
- `/api/jobs/*`

Prediction endpoints require pre-game data to exist or be generated first. The normal UI uses the `ensure` endpoints before prediction:
- `POST /api/features/player-snapshots/ensure`
- `POST /api/features/game-snapshots/ensure`

Season values use the NBA season start year. For example:
- `2023` means the `2023-2024` NBA season
- `2026` means the projected `2026-2027` season

If deployed or gone into production at a serious level protect admin endpoints with authentication before exposing them publicly.


## List of all endpoints

Here are the endpoints used by the backend:

- `GET /api/players`
- `GET /api/players/{id}`
- `GET /api/players/{id}/games`
- `GET /api/players/{id}/averages`
- `GET /api/players/{id}/dashboard`
- `GET /api/teams/{id}`
- `GET /api/teams/{id}/dashboard`
- `GET /api/teams/{id}/projection?season={seasonStartYear}`
- `GET /api/teams/{id}/roster-impact?season={seasonStartYear}`
- `GET /api/games`
- `GET /api/games/{id}`
- `GET /api/features/player-snapshots/latest?gameId={gameId}&playerId={playerId}`
- `GET /api/features/game-snapshots/latest?gameId={gameId}`
- `POST /api/features/player-snapshots/generate?season={seasonStartYear}`
- `POST /api/features/team-snapshots/generate?season={seasonStartYear}`
- `POST /api/features/game-snapshots/generate?season={seasonStartYear}`
- `POST /api/features/player-snapshots/generate?startSeason={start}&endSeason={end}`
- `POST /api/features/team-snapshots/generate?startSeason={start}&endSeason={end}`
- `POST /api/features/game-snapshots/generate?startSeason={start}&endSeason={end}`
- `GET /api/training-data/player-stats?season={seasonStartYear}&limit=1000`
- `GET /api/training-data/game-scores?season={seasonStartYear}&limit=1000`
- `GET /api/training-data/player-stats?startSeason={start}&endSeason={end}&limit=10000`
- `GET /api/training-data/game-scores?startSeason={start}&endSeason={end}&limit=10000`
- `POST /api/predictions/player`
- `POST /api/predictions/fantasy`
- `POST /api/predictions/game-score`
- `GET /api/predictions/history`
- `GET /api/model/metrics`
- `GET /api/model/versions`
- `GET /api/model/versions/active`
- `GET /api/model/training-runs`
- `GET /api/model/promotion-history`
- `GET /api/model/monitoring`
- `GET /api/model/prediction-errors`
- `POST /api/model/evaluate`
- `POST /api/model/prediction-errors/refresh`
- `POST /api/model/retrain`
- `POST /api/model/promote/{modelVersionId}`
- `POST /api/model/rollback/{modelVersionId}`
- `GET /api/standings/projections`
- `GET /api/standings/projections/{seasonStartYear}`
- `POST /api/standings/simulate?season={seasonStartYear}&runs=1000`
- `POST /api/context/rosters`
- `GET /api/context/rosters`
- `POST /api/context/transactions`
- `GET /api/context/transactions`
- `POST /api/context/draft-picks`
- `GET /api/context/draft-picks`
- `POST /api/context/injuries`
- `GET /api/context/injuries`
- `POST /api/context/refresh`
- `GET /actuator/health`
- `GET /api/seasons`
- `GET /api/teams`
- `GET /api/players/{id}/seasons`
- `GET /api/teams/{id}/games`
- `GET /api/teams/{id}/seasons`
- `GET /api/games/{id}/box-score`
- `POST /api/features/player-snapshots/ensure?gameId={gameId}&playerId={playerId}`
- `POST /api/features/game-snapshots/ensure?gameId={gameId}`
- `POST /api/jobs/model-evaluation`
- `POST /api/jobs/model-retraining`
- `POST /api/jobs/prediction-error-refresh`
- `POST /api/jobs/context-refresh`

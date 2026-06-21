# Architecture

NBA Premier Predictor uses Spring Boot as the public API boundary. The frontend
never calls the Python ML service directly.

```text
Browser
  |
  v
React frontend
  |
  v
Spring Boot backend
  |-- PostgreSQL: source of truth for historical data, feature snapshots,
  |   predictions, model versions, and monitoring records
  |-- Redis: cache and rate-limit support
  |-- FastAPI ML service: internal prediction, training, metrics, model metadata
```

## Phase Boundaries

1. Phase 1 builds the database, historical import, and read-only core API.
2. Phase 2 adds pre-game feature snapshots.
3. Phase 3 adds FastAPI baseline player/fantasy predictions.
4. Later phases add Spring-to-ML integration, injury/roster context, Redis,
   frontend dashboards, and MLOps monitoring.

## Leakage Prevention

The model must never use same-game box-score data for that same game's
prediction. Feature tables therefore include:

- `snapshot_time`
- `generated_at`
- `data_cutoff_time`
- `game_id`
- `player_id` or `team_id` when applicable
- `model_version_id` when used by a prediction

The feature pipeline will only aggregate games where `game_date_time_est` is
earlier than the target game's cutoff time.

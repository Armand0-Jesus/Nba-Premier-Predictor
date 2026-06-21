# Implementation Plan

## Phase 1

- Create repository structure.
- Add Flyway-managed PostgreSQL schema.
- Import historical players, teams, games, player game stats, and team game
  stats from the provided Kaggle zip or an extracted CSV directory.
- Expose basic Spring Boot read endpoints for players, teams, and games.
- Add a self-contained test profile, seed dataset, endpoint integration tests,
  and backend CI.

Phase 2 stays blocked until `cd backend && mvn test` passes.

## Phase 2

- Generate pre-game player/team/game feature snapshots.
- Include rolling averages, rest days, home/away, opponent context, and minutes
  trends.
- Add no-leakage checks proving target-game box scores are excluded.

## Phase 3

- Add FastAPI ML service.
- Train/load baseline models for player stat and fantasy predictions.
- Return model version, confidence, and factor summaries.

## Phase 4

- Connect Spring Boot to FastAPI.
- Persist prediction history, model versions, and model metrics.

## Later Phases

- Add roster, transaction, draft, starter, and injury context.
- Add team score prediction mode.
- Add Redis caching/rate limiting and CI.
- Build React dashboards.
- Add automated retraining, model promotion rules, rollback, and monitoring.

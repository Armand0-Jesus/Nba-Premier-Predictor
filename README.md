# NBA Premier Predictor

NBA Premier Predictor is a full-stack basketball analytics platform for player
stat, team score, and fantasy basketball forecasting. The project is designed as
a backend/data/ML system: Spring Boot is the public API, PostgreSQL is the source
of truth, Redis is for cache/rate-limit support, and FastAPI will own internal
ML inference.

## Architecture

```text
React frontend
      |
      v
Spring Boot API  ---- Redis
      |
      +---- PostgreSQL
      |
      v
FastAPI ML service
      |
      v
Saved model artifacts
```

## Repository Layout

```text
backend/        Spring Boot public API
ml-service/     FastAPI ML service, added in Phase 3
frontend/       React UI, added after backend/ML foundations
data-pipeline/  ingestion and feature engineering notes/scripts
database/       PostgreSQL schema and database notes
docs/           architecture and implementation plan
notebooks/      experiments only, never production logic
```

## Phase 1 Scope

- PostgreSQL schema for core data, predictions, context, feature snapshots, and
  later MLOps tables.
- Historical import support for the Kaggle zip or extracted CSV directory.
- Read-only Spring Boot endpoints for players, teams, games, player game logs,
  player averages, and simple dashboards.

## Local Database

```powershell
docker compose up -d postgres redis
```

The Spring Boot backend applies Flyway migrations on startup.

## Backend

```powershell
cd backend
$env:NBA_IMPORT_ENABLED="true"
$env:NBA_IMPORT_PATH="C:\Users\Armando\Downloads\archive.zip"
.\mvnw.cmd spring-boot:run
```

After importing once, set `NBA_IMPORT_ENABLED=false` for normal API startup.

Useful endpoints:

- `GET /api/players`
- `GET /api/players/{id}`
- `GET /api/players/{id}/games`
- `GET /api/players/{id}/averages`
- `GET /api/players/{id}/dashboard`
- `GET /api/teams/{id}`
- `GET /api/teams/{id}/dashboard`
- `GET /api/games`
- `GET /api/games/{id}`
- `GET /actuator/health`

## Backend Tests

```powershell
cd backend
mvn test
```

The `test` profile uses an in-memory H2 database and a tiny seed dataset, so
tests do not require local PostgreSQL, Docker, or the full Kaggle archive.
Flyway remains the application schema source for real PostgreSQL startup.

## Data Leakage Rule

Prediction features must be generated as pre-game snapshots. Each feature
snapshot stores `snapshot_time`, `generated_at`, and `data_cutoff_time` so the
system can prove a prediction only used information available before tipoff.

## Docs

- [Architecture](docs/architecture.md)
- [Implementation Plan](docs/implementation-plan.md)

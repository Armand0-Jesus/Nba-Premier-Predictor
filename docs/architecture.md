# Architecture

This project uses Spring Boot as the heart of the system . The frontend never 
calls the Machine Learning service (FastAPI) directly.

```mermaid
flowchart LR
    browser[Browser] --> frontend[React frontend]
    frontend --> spring[Spring Boot API]
    spring --> postgres[(PostgreSQL)]
    spring --> redis[(Redis)]
    spring --> localstack[LocalStack]
    spring --> fastapi[FastAPI ML service]
    fastapi --> artifacts[Joblib model artifacts]
    spring --> artifacts

    postgres --- storage[Historical data, feature snapshots, predictions, model registry, monitoring]
    redis --- cache[API cache, prediction fingerprints, rate limiting]
    localstack --- cloud[S3 prediction exports and SQS backend jobs]
```

## Leakage Prevention

The model should NOT use the same game box score data for the same game's
prediction. The feature tables include:

- `snapshot_time`
- `generated_at`
- `data_cutoff_time`
- `game_id`
- `player_id` or `team_id` when applicable
- `model_version_id` when used by a prediction

The feature pipeline will only aggregate games where `game_date_time_est` is
earlier than the target game's cutoff time. Or in simpler words, before the game begins.

## ML Architecture

```mermaid
flowchart TD
    trigger[Manual or scheduled retraining] --> export[Export pre-game training rows]
    export --> train[Train candidate artifacts in FastAPI]
    train --> evaluate[Evaluate candidate models]
    evaluate --> store[Store model_versions and model_metrics]
    store --> check{Metrics present and validation sample large enough?}
    check -- no --> reject[Reject candidate]
    check -- yes --> compare{Candidate baseline acurracy improves?}
    compare -- yes --> promote[Promote candidate]
    compare -- no --> reject
    promote --> archive[Archive previous model_versions and model_registry rows]
    reject --> history[Write promotion history]
    archive --> history
    history --> monitor[Refresh prediction error monitoring]
    history --> sqs[SQS job message]
```

Scheduled retraining uses the same service path as manual
`POST /api/model/retrain`. It is currently disabled by default and can be enabled with
`NBA_MODEL_RETRAINING_ENABLED=true` in application.properties.

## Cloud Architecture

```mermaid
flowchart TD
    prediction[Prediction request] --> spring[Spring validates and calls ML service]
    spring --> postgres[(PostgreSQL prediction history)]
    postgres --> report[S3 JSON report after commit]
    report --> localS3[LocalStack S3]

    evaluate[Evaluate, retrain, context refresh or error refresh] --> job[SQS job message]
    publishOnly["/api/jobs publish-only endpoints"] --> job
    job --> localSqs[LocalStack SQS]
```

LocalStack hobby plan was used for the local AWS compatibility. The app uses regular AWS SDK
clients, externalized endpoint settings, bucket settings, queue settings and test credentials.

## Runtime Boundaries

- The browser talks to React and Spring Boot.
- Spring Boot owns API validation, persistence, caching, rate limiting, model
  registry state, cloud exports, async job messages and monitoring records, basically most of it.
- FastAPI owns model training, model loading, inference and evaluation.
- PostgreSQL is the good old durable source of truth.
- Redis owns the caching and rate limiting management.
- LocalStack emulates AWS S3 and SQS locally for the cloud infrastructure.

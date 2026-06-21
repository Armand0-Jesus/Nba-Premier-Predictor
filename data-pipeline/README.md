# Data Pipeline

Phase 1 import is implemented in the Spring Boot backend so the project can load
the provided Kaggle zip directly into PostgreSQL without a notebook.

Supported source files:

- `Players.csv`
- `TeamHistories.csv`
- `Games.csv`
- `PlayerStatistics.csv`
- `TeamStatistics.csv`

Later phases will move feature generation and scheduled ingestion workflows into
this folder while keeping PostgreSQL as the source of truth.

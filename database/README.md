# Database

Flyway migrations are the source of truth for the PostgreSQL schema.

Current migration:

- `backend/src/main/resources/db/migration/V1__initial_schema.sql`

Docker Compose only starts PostgreSQL and Redis. The Spring Boot backend applies
Flyway migrations on startup, then Hibernate validates the mapped Phase 1
entities against the migrated database.

Backend tests use `application-test.properties` with H2 and tiny SQL seed files
so they do not depend on local PostgreSQL, Docker, or the Kaggle archive.

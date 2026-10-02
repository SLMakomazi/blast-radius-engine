# Synthetic PostgreSQL dependency

Compose uses the official `docker.io/library/postgres:17.6` image and the named
`postgres-data` volume. Copy root `.env.example` to `.env` for synthetic local credentials.
This database belongs exclusively to document-service, not to the Blast Radius API.

Flyway runs `mock-services/document-service/src/main/resources/db/migration/V1__create_synthetic_documents.sql`
on application startup. Migration history lives in this database; repeated restarts
do not recreate or clear the schema. Add new versioned migrations for schema changes.

Each accepted request inserts a UUID-keyed row with synthetic customer/document
references, a creation timestamp and the propagated correlation ID. Repeated document
references are intentionally allowed: each request is a new synthetic transaction.
Stopping PostgreSQL interrupts real JDBC operations. Starting it again preserves the
volume, and the running document-service connection pool reconnects.

`podman compose down` preserves data. `podman compose down -v` deletes this lab's
named volume and all synthetic records; use it only when deliberately resetting the lab.

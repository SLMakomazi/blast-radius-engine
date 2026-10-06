# Where new code belongs

When adding a new file, put it in the package matching its responsibility, not simply beside the class that currently calls it.

## Main application

| Responsibility | Package | Rule |
|---|---|---|
| Controller | `api` | Handles HTTP, parses inputs and maps status/errors. Calls application functionality. No core business rules. |
| DTO | `api.dto` | Request/response data only. Keep JSON compatible. No business logic. |
| Domain | `domain.incident`, `domain.topology`, `domain.evidence`, `domain.diagnosis`, `domain.experiment` | Core concepts and pure rules. No Spring controllers, database clients or telemetry vendors. |
| Application service | `service` | Coordinates use cases, ports and domain behavior. This is the application layer; a second `application` package is unnecessary. |
| Port | `ports` | Defines what the application needs from an external system, using domain types. No vendor response objects. |
| Adapter | `adapters.<responsibility>` | Implements a port or supplies an external entry point for a technology. Scheduled lifecycle entry points belong in `adapters.scheduling`. |
| Persistence | `adapters.persistence` | SQL, row mapping and file details behind repository/store ports. Flyway migrations stay in `src/main/resources/db/migration`. |
| Exception | Owning layer/package | Domain errors stay with the domain; application errors with their use case; provider errors with their adapter. HTTP handlers stay at the API boundary. No global bucket of unrelated exceptions. |
| Configuration | `config` or owning adapter/API package | Spring/application wiring only. Keep provider properties beside their adapter. |
| Sanitization | `sanitization` | Redacts sensitive telemetry before persistence, display, export or AI. Adapters sanitize before constructing normalized evidence; stored diagnosis is sanitized again. |

Provider-internal Loki, Tempo, Prometheus and Actuator models stay beside their adapters. They are not public API DTOs. Domain diagnosis records describe the diagnosis port and contain no Gemini schema.

Controllers currently return several domain results directly, and history/topology queries use ports directly. Preserve those JSON contracts during package cleanup. A query facade or independent response mapping should be reviewed as a separate change.

## Dependencies

API and scheduled entry points call application functionality. Application code calls ports and domain rules. External implementations depend on the ports they implement. Domain code depends on Java and other domain code only. Configuration may reference all layers to wire the application.

Topology calculates potential impact. Runtime telemetry proves observed impact. AI explains sanitized evidence. AI remains optional and advisory; it does not select origin, calculate impact or severity, or control lifecycle.

Keep evidence timestamps, scope, provenance and coverage. Missing telemetry never proves health. Retained topology is knowledge about possible dependencies, not current incident evidence.

## Mock services

Payment, customer and document are small synthetic monitored applications. Keep their `controller`, `service`, `dto`, `exception`, `client`, `persistence`, `config` and `fault` packages where those responsibilities exist. Do not add enterprise DDD layers or a shared runtime library just to avoid a few repeated transport records or filters. Fault controls are local lab support only.

## Working safely

Start from current, clean `main`; work on a branch. Make small moves and update package declarations, imports, same-package references, tests and docs. Root Spring scanning already includes all application subpackages. Preserve bean names and configuration keys.

Use each module's own Maven POM and Java 21. Run `mvn clean verify` in the affected module after each major move. Maven's default suite excludes `*LiveIT`; run those explicitly only against a healthy local lab. Do not weaken assertions or change telemetry requirements to make a refactor pass.

Run `docker compose config --quiet`, build the full stack and run all Stage 1–4 suites in order. Follow the telemetry recovery checks and pauses in [E2E_VALIDATION.md](E2E_VALIDATION.md). Confirm no unexpected ACTIVE incident remains and inspect recent Collector errors.

Use simple English in comments and docs. Keep established technical terms and API/error text when behavior depends on it. Avoid mass renaming for style.

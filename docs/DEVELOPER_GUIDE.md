# Where new code belongs

Choose the capability first, then the layer. When adding a new file, put it in the package matching its responsibility, not simply beside the class that currently calls it.

## Capability ownership

| Capability | Owns |
|---|---|
| `incident` | Origin/impact/severity concepts, analysis, incident persistence, containment validation and REST contracts |
| `topology` | Components/dependencies, graph rules, acquisition/retention ports, discovery and file storage |
| `telemetry` | Normalized evidence/coverage, acquisition port and provider adapters |
| `diagnosis` | Advisory diagnosis contracts, use cases/context mapping, provider port and AI implementations |
| `lifecycle` | Incident creation/update/resolution orchestration and scheduled detection/recovery entry point |
| `shared` | Common sanitization utilities and OpenAPI metadata configuration |

## Layer rules

| Responsibility | Location | Rule |
|---|---|---|
| Controller | `incident.api` | Handles HTTP and calls application functionality. No core business rules. |
| DTO | `incident.api.dto` | Request/response data only. Preserve JSON contracts. No business logic. |
| Domain | Owning capability's `domain` | Plain Java concepts and rules. No Spring, HTTP clients, JDBC, database mapping or telemetry vendors. |
| Application | Owning capability's `application` | Coordinates use cases, domain behavior and ports. Does not import vendor/infrastructure implementations. |
| Port | Owning capability's `application.port` | Existing external boundary described using domain types. No vendor response objects; no interface added only for ceremony. |
| Adapter | Owning capability's `infrastructure` | Implements a port or connects a technical entry point. Keep vendor models/properties beside the adapter. |
| Persistence | `incident.infrastructure.persistence` or `topology.infrastructure.persistence` | SQL/row mapping or file-storage details. Keep JDBC and Flyway migrations in their existing resources directory. |
| Exception | Owning responsibility/layer | Domain errors with domain rules, use-case errors with application, provider errors with infrastructure, REST handlers at API. No global exceptions bucket. |
| Configuration | Capability infrastructure/config or `shared.config` | Spring wiring or shared application metadata only. |
| Scheduling | `lifecycle.infrastructure` | Spring timer invokes analysis/lifecycle application services. AI stays out of deterministic recovery. |
| Sanitization | `shared.sanitization` | Redacts sensitive evidence before persistence/display/export/AI. Providers sanitize before creating evidence; stored diagnosis is sanitized again. |

The optional failure-experiment feature is incident containment: its plain Java types belong in `incident.domain.containment`, provider port in `incident.application.port`, local catalogue in `incident.infrastructure.containment`. Do not create a top-level experiment capability.

API may expose the established domain-record data shape; this does not let domain code depend on HTTP. Applications may consume other capability domain contracts and application ports. Infrastructure-to-infrastructure wiring stays outside application/domain. The architecture tests guard these boundaries.

## Mock services and repository assets

Mock services remain simple independent lab applications with controller/service/dto/exception/client/config/persistence/fault packages. Their fault controls are separate from the main engine's containment metadata. Do not apply enterprise-scale DDD to them.

Do not create empty layers or placeholder packages. Maven `target` directories are generated. Compose mount/data paths, observability settings, Flyway migrations, fixtures, scripts and frontend assets remain where their tooling requires them.

## Verification

After each major capability move, run the API's actual Maven `clean verify`. Never weaken assertions or change thresholds to accommodate imports. The standard suite includes source architecture checks; opt-in live tests need a healthy lab. Stage 1–4 runtime validation remains the regression gate. If the user runs it separately, report that it is pending rather than reusing the historical 18 / 18 result.

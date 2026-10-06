# Repository cleanup report

Branch: `refactor/ddd-repository-cleanup`. Baseline: main at `2360328`. No merge or push to main.

## Before and problems found

The repository already used domain models, ports, provider adapters, application services, REST and sanitization. The main problem was a mixed `service` package: pure rules, a fallback adapter and a scheduler lived beside orchestration. HTTP transport records were split between a top-level request and nested controller records. Documentation described deleted branches/documents and planned features that already existed.

See [the pre-change audit](ARCHITECTURAL_AUDIT.md) for all 164 Java files, original packages, responsibilities and dependencies, plus the original tracked tree.

## Files moved

Paths below are relative to `blast-radius-api/src/main/java/com/madlanga/blastradius`:

| Before | After |
|---|---|
| `service/DeterministicGraphEngine.java` | `domain/topology/DeterministicGraphEngine.java` |
| `service/IncidentSeverityCalculator.java` | `domain/incident/IncidentSeverityCalculator.java` |
| `service/FailureExperimentAssessmentService.java` | `domain/experiment/FailureExperimentAssessmentService.java` |
| `service/DeterministicDiagnosisAdapter.java` | `adapters/ai/DeterministicDiagnosisAdapter.java` |
| `service/IncidentLifecycleMonitor.java` | `adapters/scheduling/IncidentLifecycleMonitor.java` |
| `api/AnalyzeIncidentRequest.java` | `api/dto/AnalyzeIncidentRequest.java` |

The three matching rule tests moved from `src/test/.../service` to the corresponding domain packages. All references were updated, including topology tests and live integration tests. Root Spring scanning already covers the destinations; constructor injection, bean names and settings remain unchanged.

The six moved production classes have unchanged executable content after removing package/import declarations and block comments from the comparison.

## Extracted and consolidated contracts

`IncidentResponse` and `ResolveIncidentRequest` were extracted from `IncidentHistoryController` into `api.dto`, keeping their names and fields. The identical nested error records were consolidated as public `api.dto.ErrorResponse`; `HistoryErrorResponse` is the only renamed/consolidated internal type. The request also moved to `api.dto`. No endpoint, JSON field, error code or status was changed. Repository references were checked; this is an application, not a published Java contract library.

Exception handlers and the private `ApiRequestException` stay at the REST boundary. Mock-service exceptions stay with their existing responsibility. No global exceptions package was introduced.

## Deleted files and packages

Deleted only nine empty `.gitkeep` files: `docker`, `infrastructure/failure-driver`, `fixtures/experiments`, `fixtures/incidents`, `fixtures/topology`, and `fixtures/telemetry/{health,logs,metrics,traces}`. They had no runtime/source references. No Java source, functionality, runtime configuration, migrations, tests, runners or active fixture was deleted.

Created packages: `api.dto`, `adapters.scheduling`. No existing Java package was removed. Existing domain subpackages were reused. No established class/method/variable names were changed for style.

## Classes kept in place

All existing domain concepts stay in their original packages because they describe core evidence, topology, incidents, diagnosis or experiments. All seven ports remain provider-neutral. Loki/Tempo/Prometheus/Actuator response models and provider properties remain beside adapters. JDBC/file persistence stays under `adapters.persistence`. Trace discovery and retention remain acquisition adapters. Gemini wiring stays with AI adapters; OpenAPI metadata stays at the API boundary.

Analysis/lifecycle/AI services and both context mappers remain in `service`, the application layer. The stored mapper supports the diagnosis use case and does not implement storage. Mock services keep their small independent package structure; repeated transport records and filters do not justify a shared runtime library.

## Architecture and JDBC decisions

Use practical DDD: pure rules belong with their domain concepts, orchestration stays in `service`, ports describe external needs and adapters implement them. Keep current JSON exposure and lifecycle policy intact; facade/policy extraction needs a separate change.

JDBC remains appropriate for explicit incident queries/upsert/JSONB snapshots and one synthetic document table. JPA would help a larger entity relationship model, but would add ORM mapping, persistence-context and transaction/query decisions here without solving a demonstrated problem. No JDBC-to-JPA migration. ADR-029 and ADR-030 record these choices.

Topology calculates potential impact. Runtime telemetry proves observed impact. AI explains sanitized evidence. AI remains optional/advisory and has no role in deterministic origin, impact, severity or lifecycle decisions.

## Documentation and simple English

Updated README, ABOUT, SETUP, PROJECT_STRUCTURE, CODEX, ARCHITECTURE, INTEGRATION, DECISIONS, E2E_VALIDATION and frontend README. Added this report, the audit and DEVELOPER_GUIDE. Normal stable branch is main. Baseline Stage 1–4 status is 18 / 18 validated locally; synthetic capabilities and enterprise integration requirements are distinguished.

Replaced stale class names, deleted document references and planned adapter descriptions with actual implementation. Simplified descriptive Java comments for analysis, stored diagnosis mapping, lifecycle monitoring, telemetry/topology ports, API requests and lab fault controls. Runtime error text is preserved because some handlers use prefixes.

## Verification so far

- API baseline: 192 tests passed.
- After domain moves: 192 passed; `clean verify` succeeded.
- After adapter moves: 192 passed; `clean verify` succeeded.
- After API contract move/extraction: 192 passed; `clean verify` succeeded.
- Full Docker build: succeeded for API, three mock services, frontend and traffic generator.
- Docker Java tests: API 192, payment 17, customer 12, document 19; all passed (240 total).
- Traffic tests: 4 passed. Frontend Vite build passed.
- `docker compose config --quiet` passed.
- Package paths and domain import boundaries checked.

The managed environment required temporary proxy/CA build settings outside Git and a writable buildx state directory. Repository Dockerfiles and Compose configuration are unchanged. Its VFS storage filled the root filesystem during startup; clearing disposable build cache recovered space without deleting application images or lab volumes.

Fresh Stage 1–4 regression and final platform checks are pending. The supplied baseline 18 / 18 is not a new branch test result.

## Remaining architectural debt

- Analysis combines orchestration, evidence policy, lab markers and metric interpretation.
- Recovery policy remains inside the scheduler.
- Controllers directly query ports and expose some domain results as JSON.
- Generic errors use message prefixes; typed errors would need compatibility work.
- Per-provider timeout properties are not all independently applied.
- Retained topology uses a discovery helper constructed without its telemetry provider; a separate pure mapper would be clearer.
- Enterprise schemas, identity mapping, multi-application scheduling, RBAC/audit, retention and deployment remain integration work.

## Final tree and Git summary

[PROJECT_STRUCTURE.md](../PROJECT_STRUCTURE.md) contains the complete source tree and package guide. Final validation results and Git summary will be added after the regression gate.

# Repository cleanup report

> Historical record of the original seven-commit cleanup. Current capability-first ownership and results are in [CAPABILITY_REFACTOR.md](CAPABILITY_REFACTOR.md); current tree is in [PROJECT_STRUCTURE.md](../PROJECT_STRUCTURE.md).


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

## Verification

- API baseline: 192 tests passed.
- After domain moves: 192 passed; `clean verify` succeeded.
- After adapter moves: 192 passed; `clean verify` succeeded.
- After API contract move/extraction: 192 passed; `clean verify` succeeded.
- Full Docker build: succeeded for API, three mock services, frontend and traffic generator.
- Docker Java tests: API 192, payment 17, customer 12, document 19; all passed (240 total).
- Traffic tests: 4 passed. Frontend Vite build passed.
- `docker compose config --quiet` passed.
- Package paths and domain import boundaries checked.
- Final API `clean verify` including the two opt-in live integration tests: 194 passed. The first live topology attempt saw incomplete startup traces; after fresh traces contained all three dependency edges, the unchanged tests passed.

The managed environment required temporary proxy/CA build settings outside Git and a writable buildx state directory. Repository Dockerfiles and Compose configuration are unchanged. A temporary runtime override excludes local Compose names from the inherited external proxy and keeps loopback readiness checks local. Frontend/traffic images were rebuilt after correcting this checkout’s restrictive file permissions. Its VFS storage filled the root filesystem during startup; clearing disposable build cache recovered space without deleting application images or lab volumes.

Fresh regression on this branch, 6 October 2026:

| Stage | Passed | Scenarios |
|---|---|---|
| 1 | 6 / 6 | Healthy baseline, PostgreSQL, document, customer, payment outages, partial observability |
| 2 | 4 / 4 | HTTP 500, intermittent failures, latency, database connectivity |
| 3 | 4 / 4 | Deployment regression, configuration error, contract break, feature flag regression |
| 4 | 4 / 4 | Distributed cascade, compound dependency, flapping dependency, partial observability |
| Total | **18 / 18** | Existing assertions and runners unchanged |

The first Stage 1 attempt passed baseline/PostgreSQL but failed document detection because the expected incident already existed from startup. The gate was stopped and services restored. The API also stopped scheduler progress while consuming nearly two CPU cores under its 96 MB heap. Restarting with a temporary 256 MB heap and 512 MB container limit restored progress; all leftover incidents resolved automatically after three healthy windows. Stage 1 was rerun from the beginning with no ACTIVE incidents and passed all six unchanged scenarios. This environment-specific resource override is outside Git; no lifecycle, classification or test timing was changed.

After Stage 1, Tempo readiness returned 503 following the deliberate tracing outage. Only Tempo and the Collector were restarted. Both readiness checks then passed, fresh traces appeared, and recent Collector logs contained zero failed/dropping/no-more-retries/no-such-host errors before Stage 2. Required pauses between stages were observed.

Final platform checks: all 12 Compose services are running; all eight configured health checks are healthy. Loki, Tempo and Collector readiness endpoints return HTTP 200 and Tempo contains fresh traces. `GET /api/v1/blast-radius/incidents?status=ACTIVE` returns an empty list. Collector logs from the final 20-second window contain zero matches for the specified error terms. The final partial-observability scenario required the same Tempo/Collector restart to restore readiness. `docker compose config --quiet` passed again.

Session evidence is in `/tmp/blast-stack-build.log`, `/tmp/blast-permission-rebuild.log`, `/tmp/blast-final-api-live-retry.log`, `/tmp/blast-stage1-retry.log`, `/tmp/blast-stage2.log`, `/tmp/blast-stage3.log`, `/tmp/blast-stage4.log`, `/tmp/blast-final-platform.txt` and `/tmp/blast-final-collector.log`. These are local session artifacts, not runtime inputs.

## Remaining architectural debt

- Analysis combines orchestration, evidence policy, lab markers and metric interpretation.
- Recovery policy remains inside the scheduler.
- Controllers directly query ports and expose some domain results as JSON.
- Generic errors use message prefixes; typed errors would need compatibility work.
- Per-provider timeout properties are not all independently applied.
- The 96 MB API heap and tracing readiness after deliberate outages deserve a separate operational investigation; this cleanup preserved repository resource settings.
- Retained topology uses a discovery helper constructed without its telemetry provider; a separate pure mapper would be clearer.
- Enterprise schemas, identity mapping, multi-application scheduling, RBAC/audit, retention and deployment remain integration work.

## Final tree and Git summary

[PROJECT_STRUCTURE.md](../PROJECT_STRUCTURE.md) contains the complete source tree and package guide. The change is split into seven commits: pre-change audit, domain rules, adapter placement, API contracts, unused placeholders, architecture documentation, and regression evidence. Run `git diff main...HEAD --stat` and `git log --oneline main..HEAD` for the exact review summary. No branch was merged or pushed.

Final diff against main: 47 files changed, 1,327 insertions and 553 deletions, including moves detected by Git and the full audit/source-tree documentation.

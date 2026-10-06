# Capability-first package audit and plan

> Record of the package refactor. Validation statements below describe that earlier work; the current two-stage gate and latest cleanup results are in [E2E_VALIDATION.md](E2E_VALIDATION.md).

Baseline: `cdc48d8` plus verified placeholder cleanup. The previous uncommitted experiment removal was restored before this structural refactor. All original seven commits remain intact.

## Decisions

Preserve every executable production statement, endpoint, JSON field, configuration key, JDBC query/migration, and test assertion. Move one capability at a time and run Maven clean verify after each. No mock-service package changes. Root Spring scanning still covers all targets. Ports live in the owning capability application layer; technical wiring stays in infrastructure. Shared contains only redaction utilities and OpenAPI configuration.

The optional experiment API is working containment/failure-validation behavior. Keep its four plain-Java domain classes under `incident.domain.containment`, metadata port under `incident.application.port`, and local catalogue under `incident.infrastructure.containment`. Scheduler, frontend and Stage runners do not select it, but manual analysis and diagnosis mapping do. Do not delete it.

Seven placeholder-only packages and four redundant `.gitkeep` files were verified unused and removed. Maven annotation-output directories are generated. Compose data/mount paths, Flyway resources and observability assets are retained.

## Production class inventory

| Class | Original package | Target package | Responsibility |
|---|---|---|---|
| `BlastRadiusApplication` | `com.madlanga.blastradius` | `com.madlanga.blastradius` | CONFIGURATION |
| `AiDiagnosisConfiguration` | `com.madlanga.blastradius.adapters.ai` | `com.madlanga.blastradius.diagnosis.infrastructure` | CONFIGURATION |
| `DeterministicDiagnosisAdapter` | `com.madlanga.blastradius.adapters.ai` | `com.madlanga.blastradius.diagnosis.infrastructure` | ADAPTER |
| `GeminiDiagnosisAdapter` | `com.madlanga.blastradius.adapters.ai` | `com.madlanga.blastradius.diagnosis.infrastructure` | ADAPTER |
| `GeminiProperties` | `com.madlanga.blastradius.adapters.ai` | `com.madlanga.blastradius.diagnosis.infrastructure` | ADAPTER |
| `LocalFailureExperimentProvider` | `com.madlanga.blastradius.adapters.experiment` | `com.madlanga.blastradius.incident.infrastructure.containment` | ADAPTER |
| `FileTopologyStore` | `com.madlanga.blastradius.adapters.persistence` | `com.madlanga.blastradius.topology.infrastructure.persistence` | PERSISTENCE |
| `JdbcIncidentRepository` | `com.madlanga.blastradius.adapters.persistence` | `com.madlanga.blastradius.incident.infrastructure.persistence` | PERSISTENCE |
| `IncidentLifecycleMonitor` | `com.madlanga.blastradius.adapters.scheduling` | `com.madlanga.blastradius.lifecycle.infrastructure` | ADAPTER |
| `LocalTelemetryProvider` | `com.madlanga.blastradius.adapters.telemetry` | `com.madlanga.blastradius.telemetry.infrastructure` | ADAPTER |
| `ActuatorHealthAdapter` | `com.madlanga.blastradius.adapters.telemetry.health` | `com.madlanga.blastradius.telemetry.infrastructure.health` | ADAPTER |
| `ActuatorHealthProperties` | `com.madlanga.blastradius.adapters.telemetry.health` | `com.madlanga.blastradius.telemetry.infrastructure.health` | ADAPTER |
| `ActuatorHealthResponse` | `com.madlanga.blastradius.adapters.telemetry.health` | `com.madlanga.blastradius.telemetry.infrastructure.health` | ADAPTER |
| `LokiLogAdapter` | `com.madlanga.blastradius.adapters.telemetry.loki` | `com.madlanga.blastradius.telemetry.infrastructure.loki` | ADAPTER |
| `LokiLogLine` | `com.madlanga.blastradius.adapters.telemetry.loki` | `com.madlanga.blastradius.telemetry.infrastructure.loki` | ADAPTER |
| `LokiProperties` | `com.madlanga.blastradius.adapters.telemetry.loki` | `com.madlanga.blastradius.telemetry.infrastructure.loki` | ADAPTER |
| `LokiResponse` | `com.madlanga.blastradius.adapters.telemetry.loki` | `com.madlanga.blastradius.telemetry.infrastructure.loki` | ADAPTER |
| `PrometheusMetricsAdapter` | `com.madlanga.blastradius.adapters.telemetry.prometheus` | `com.madlanga.blastradius.telemetry.infrastructure.prometheus` | ADAPTER |
| `PrometheusProperties` | `com.madlanga.blastradius.adapters.telemetry.prometheus` | `com.madlanga.blastradius.telemetry.infrastructure.prometheus` | ADAPTER |
| `PrometheusResponse` | `com.madlanga.blastradius.adapters.telemetry.prometheus` | `com.madlanga.blastradius.telemetry.infrastructure.prometheus` | ADAPTER |
| `TempoProperties` | `com.madlanga.blastradius.adapters.telemetry.tempo` | `com.madlanga.blastradius.telemetry.infrastructure.tempo` | ADAPTER |
| `TempoResponse` | `com.madlanga.blastradius.adapters.telemetry.tempo` | `com.madlanga.blastradius.telemetry.infrastructure.tempo` | ADAPTER |
| `TempoSearchResponse` | `com.madlanga.blastradius.adapters.telemetry.tempo` | `com.madlanga.blastradius.telemetry.infrastructure.tempo` | ADAPTER |
| `TempoTraceAdapter` | `com.madlanga.blastradius.adapters.telemetry.tempo` | `com.madlanga.blastradius.telemetry.infrastructure.tempo` | ADAPTER |
| `RetainedTopologyProvider` | `com.madlanga.blastradius.adapters.topology` | `com.madlanga.blastradius.topology.infrastructure` | ADAPTER |
| `TraceDiscoveredTopologyProvider` | `com.madlanga.blastradius.adapters.topology` | `com.madlanga.blastradius.topology.infrastructure` | ADAPTER |
| `BlastRadiusController` | `com.madlanga.blastradius.api` | `com.madlanga.blastradius.incident.api` | API / CONTROLLER |
| `IncidentHistoryController` | `com.madlanga.blastradius.api` | `com.madlanga.blastradius.incident.api` | API / CONTROLLER |
| `OpenApiConfiguration` | `com.madlanga.blastradius.api` | `com.madlanga.blastradius.shared.config` | CONFIGURATION |
| `AnalyzeIncidentRequest` | `com.madlanga.blastradius.api.dto` | `com.madlanga.blastradius.incident.api.dto` | DTO / API CONTRACT |
| `ErrorResponse` | `com.madlanga.blastradius.api.dto` | `com.madlanga.blastradius.incident.api.dto` | DTO / API CONTRACT |
| `IncidentResponse` | `com.madlanga.blastradius.api.dto` | `com.madlanga.blastradius.incident.api.dto` | DTO / API CONTRACT |
| `ResolveIncidentRequest` | `com.madlanga.blastradius.api.dto` | `com.madlanga.blastradius.incident.api.dto` | DTO / API CONTRACT |
| `TelemetryAdapterConfig` | `com.madlanga.blastradius.config` | `com.madlanga.blastradius.telemetry.infrastructure.config` | CONFIGURATION |
| `TopologyConfig` | `com.madlanga.blastradius.config` | `com.madlanga.blastradius.topology.infrastructure.config` | CONFIGURATION |
| `AiDiagnosis` | `com.madlanga.blastradius.domain.diagnosis` | `com.madlanga.blastradius.diagnosis.domain` | DOMAIN |
| `DiagnosisContext` | `com.madlanga.blastradius.domain.diagnosis` | `com.madlanga.blastradius.diagnosis.domain` | DOMAIN |
| `CoverageStatus` | `com.madlanga.blastradius.domain.evidence` | `com.madlanga.blastradius.telemetry.domain` | DOMAIN |
| `EvidenceFamily` | `com.madlanga.blastradius.domain.evidence` | `com.madlanga.blastradius.telemetry.domain` | DOMAIN |
| `EvidenceProvenance` | `com.madlanga.blastradius.domain.evidence` | `com.madlanga.blastradius.telemetry.domain` | DOMAIN |
| `HealthEvidence` | `com.madlanga.blastradius.domain.evidence` | `com.madlanga.blastradius.telemetry.domain` | DOMAIN |
| `HealthState` | `com.madlanga.blastradius.domain.evidence` | `com.madlanga.blastradius.telemetry.domain` | DOMAIN |
| `LogEvidence` | `com.madlanga.blastradius.domain.evidence` | `com.madlanga.blastradius.telemetry.domain` | DOMAIN |
| `MetricEvidence` | `com.madlanga.blastradius.domain.evidence` | `com.madlanga.blastradius.telemetry.domain` | DOMAIN |
| `SpanEvidence` | `com.madlanga.blastradius.domain.evidence` | `com.madlanga.blastradius.telemetry.domain` | DOMAIN |
| `SpanKind` | `com.madlanga.blastradius.domain.evidence` | `com.madlanga.blastradius.telemetry.domain` | DOMAIN |
| `SpanStatus` | `com.madlanga.blastradius.domain.evidence` | `com.madlanga.blastradius.telemetry.domain` | DOMAIN |
| `TelemetryBundle` | `com.madlanga.blastradius.domain.evidence` | `com.madlanga.blastradius.telemetry.domain` | DOMAIN |
| `TelemetryCoverage` | `com.madlanga.blastradius.domain.evidence` | `com.madlanga.blastradius.telemetry.domain` | DOMAIN |
| `TelemetryQuery` | `com.madlanga.blastradius.domain.evidence` | `com.madlanga.blastradius.telemetry.domain` | DOMAIN |
| `ContainmentStatus` | `com.madlanga.blastradius.domain.experiment` | `com.madlanga.blastradius.incident.domain.containment` | DOMAIN |
| `ExperimentAssessment` | `com.madlanga.blastradius.domain.experiment` | `com.madlanga.blastradius.incident.domain.containment` | DOMAIN |
| `FailureExperiment` | `com.madlanga.blastradius.domain.experiment` | `com.madlanga.blastradius.incident.domain.containment` | DOMAIN |
| `FailureExperimentAssessmentService` | `com.madlanga.blastradius.domain.experiment` | `com.madlanga.blastradius.incident.domain.containment` | DOMAIN |
| `ComponentImpact` | `com.madlanga.blastradius.domain.incident` | `com.madlanga.blastradius.incident.domain` | DOMAIN |
| `ConfidenceLevel` | `com.madlanga.blastradius.domain.incident` | `com.madlanga.blastradius.incident.domain` | DOMAIN |
| `EvidenceSignal` | `com.madlanga.blastradius.domain.incident` | `com.madlanga.blastradius.incident.domain` | DOMAIN |
| `IncidentAnalysis` | `com.madlanga.blastradius.domain.incident` | `com.madlanga.blastradius.incident.domain` | DOMAIN |
| `IncidentSeverity` | `com.madlanga.blastradius.domain.incident` | `com.madlanga.blastradius.incident.domain` | DOMAIN |
| `IncidentSeverityCalculator` | `com.madlanga.blastradius.domain.incident` | `com.madlanga.blastradius.incident.domain` | DOMAIN |
| `IncidentStatus` | `com.madlanga.blastradius.domain.incident` | `com.madlanga.blastradius.incident.domain` | DOMAIN |
| `ObservedState` | `com.madlanga.blastradius.domain.incident` | `com.madlanga.blastradius.incident.domain` | DOMAIN |
| `OriginAssessment` | `com.madlanga.blastradius.domain.incident` | `com.madlanga.blastradius.incident.domain` | DOMAIN |
| `PersistedIncident` | `com.madlanga.blastradius.domain.incident` | `com.madlanga.blastradius.incident.domain` | DOMAIN |
| `SeverityLevel` | `com.madlanga.blastradius.domain.incident` | `com.madlanga.blastradius.incident.domain` | DOMAIN |
| `ComponentNode` | `com.madlanga.blastradius.domain.topology` | `com.madlanga.blastradius.topology.domain` | DOMAIN |
| `ComponentType` | `com.madlanga.blastradius.domain.topology` | `com.madlanga.blastradius.topology.domain` | DOMAIN |
| `DependencyEdge` | `com.madlanga.blastradius.domain.topology` | `com.madlanga.blastradius.topology.domain` | DOMAIN |
| `DependencyTopology` | `com.madlanga.blastradius.domain.topology` | `com.madlanga.blastradius.topology.domain` | DOMAIN |
| `DeterministicGraphEngine` | `com.madlanga.blastradius.domain.topology` | `com.madlanga.blastradius.topology.domain` | DOMAIN |
| `GraphAnalysisResult` | `com.madlanga.blastradius.domain.topology` | `com.madlanga.blastradius.topology.domain` | DOMAIN |
| `ImpactClassification` | `com.madlanga.blastradius.domain.topology` | `com.madlanga.blastradius.topology.domain` | DOMAIN |
| `RetainedTopology` | `com.madlanga.blastradius.domain.topology` | `com.madlanga.blastradius.topology.domain` | DOMAIN |
| `TheoreticalImpact` | `com.madlanga.blastradius.domain.topology` | `com.madlanga.blastradius.topology.domain` | DOMAIN |
| `AiDiagnosisPort` | `com.madlanga.blastradius.ports` | `com.madlanga.blastradius.diagnosis.application.port` | PORT |
| `DependencyTopologyProvider` | `com.madlanga.blastradius.ports` | `com.madlanga.blastradius.topology.application.port` | PORT |
| `FailureExperimentProvider` | `com.madlanga.blastradius.ports` | `com.madlanga.blastradius.incident.application.port` | PORT |
| `IncidentRepository` | `com.madlanga.blastradius.ports` | `com.madlanga.blastradius.incident.application.port` | PORT |
| `RuntimeSpanSource` | `com.madlanga.blastradius.ports` | `com.madlanga.blastradius.topology.application.port` | PORT |
| `TelemetryProvider` | `com.madlanga.blastradius.ports` | `com.madlanga.blastradius.telemetry.application.port` | PORT |
| `TopologyStore` | `com.madlanga.blastradius.ports` | `com.madlanga.blastradius.topology.application.port` | PORT |
| `BuiltInRedactionRules` | `com.madlanga.blastradius.sanitization` | `com.madlanga.blastradius.shared.sanitization` | SANITIZATION |
| `RedactionPlaceholders` | `com.madlanga.blastradius.sanitization` | `com.madlanga.blastradius.shared.sanitization` | SANITIZATION |
| `RedactionRule` | `com.madlanga.blastradius.sanitization` | `com.madlanga.blastradius.shared.sanitization` | SANITIZATION |
| `TelemetrySanitizer` | `com.madlanga.blastradius.sanitization` | `com.madlanga.blastradius.shared.sanitization` | SANITIZATION |
| `AiDiagnosisService` | `com.madlanga.blastradius.service` | `com.madlanga.blastradius.diagnosis.application` | APPLICATION SERVICE |
| `DiagnosisContextFactory` | `com.madlanga.blastradius.service` | `com.madlanga.blastradius.diagnosis.application` | APPLICATION SERVICE |
| `IncidentAnalysisService` | `com.madlanga.blastradius.service` | `com.madlanga.blastradius.incident.application` | APPLICATION SERVICE |
| `IncidentLifecycleService` | `com.madlanga.blastradius.service` | `com.madlanga.blastradius.lifecycle.application` | APPLICATION SERVICE |
| `StoredAnalysisDiagnosisContextMapper` | `com.madlanga.blastradius.service` | `com.madlanga.blastradius.diagnosis.application` | APPLICATION SERVICE |

## Test and helper references

Tests move beside the capability/layer they verify. `scripts/BootstrapCapturedTopology.java` imports follow the production moves; it remains an offline lab helper. No Stage-runner source changes.

## Validation policy

The user will run Stage 1–2 locally; this turn uses Maven validation only. The retained stages of the historical completed suite passed 10/10. Fresh Stage results must not be claimed for this capability refactor.

## Final result

The engine now has six real capability roots: incident, topology, telemetry, diagnosis, lifecycle and shared. No global domain/service/ports/adapters/API/config/sanitization directories remain. There is no top-level experiment capability. The root `BlastRadiusApplication` remains the Spring entry point.

Moved 89 production Java files and 29 existing tests; the root application/startup test remain in place. All class names are preserved. The inventory above lists every original/target package. The complete package/source tree is in [PROJECT_STRUCTURE.md](../PROJECT_STRUCTURE.md).

Removed 11 `.gitkeep` files: seven placeholder-only packages (`controller`, `dto`, `exception`, `domain.correlation`, `domain.graph`, `domain.model`, `domain.severity`) and four redundant markers in populated AI/persistence/topology/service packages. The capability moves also emptied and removed the previous global layout. No empty source directories or actual placeholder files remain. Generated Maven annotation-output directories may reappear; they are not repository packages or runtime mounts.

## Experiment workflow preserved

- GET/POST analysis still accepts `experimentId`.
- `FailureExperimentProvider` supplies the local catalogue's `postgres-outage-local` metadata.
- `IncidentAnalysisService` preserves the declared-origin check and expected/observed containment assessment.
- `ExperimentAssessment` remains in incident JSON and stored snapshots.
- Both diagnosis context mappers preserve the optional containment data.
- Existing feature and error-classification tests remain unchanged apart from package/import references.
- Frontend/scheduler/Stage runners do not select this optional path, but that does not make it dead behavior.

## Maven and source verification

| Increment | Result |
|---|---|
| Shared | `clean verify`, 192 passed |
| Telemetry | `clean verify`, 192 passed |
| Topology | `clean verify`, 192 passed |
| Diagnosis | `clean verify`, 192 passed |
| Incident/containment/API | `clean verify`, 192 passed after fixing an implicit lifecycle import |
| Lifecycle | `clean verify`, 192 passed |
| Final, including architecture checks | `clean verify`, **195 passed**, zero failures/errors/skips |
| Offline topology helper | Compiled with Java 21 against the final module test classpath |

The three new architecture checks verify package ownership/directory matching, Java/domain-only domain dependencies, and application/API independence from infrastructure/JDBC implementations. They add no runtime dependency. An initial assertion-overload compile error in the new check was corrected before the final green build.

All 90 original production files, 30 original test files and the offline helper were compared with `cdc48d8`, allowing only package/import/qualified-name changes. No other executable content changed. Root Spring component scanning remains unchanged and the application context test passes.

Frontend, mock services, Stage runners, Docker configuration, resource settings, thresholds, JSON fields, endpoint paths, JDBC queries and Flyway migrations have no diff from the seven-commit baseline. No JPA introduced. The actual module Maven POM was used with Java 21; temporary proxy/CA/Maven cache settings remain outside Git.

## Stage 1–2 handoff

Per the user's instruction to use Maven only, the Stage runners and Docker rebuild were not run for this capability refactor. The retained stages of the historical completed baseline are Stage 1 6/6 and Stage 2 4/4: **10 / 10**. Fresh capability-refactor Stage results are **pending the user's local validation**, not claimed as passed. The earlier interrupted experiment-removal runs do not validate this final capability-preserving code.

Rebuild the API/stack locally and follow [E2E_VALIDATION.md](E2E_VALIDATION.md), including the stage pauses, tracing recovery and final health/ACTIVE-incident checks. No assertions or runners were weakened.

## Git and remaining debt

Logical commits are appended after the original seven; no squash, rewrite, merge or push. They cover placeholder cleanup, the audit, each of the six capability moves, architecture checks and current documentation. Review with `git log --oneline cdc48d8..HEAD` and `git diff cdc48d8..HEAD --stat`.

Remaining behavior-sensitive debt: incident analysis still combines orchestration and evidence/origin policy; recovery policy remains inside the scheduler; controllers query application ports and expose established domain records as JSON; some error classification uses message prefixes; topology discovery still uses a helper constructed without a telemetry provider; per-provider timeout controls and multi-application/enterprise integration need separate work. Source/import checks do not replace Stage runtime validation.

# Capability-first package audit and plan

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

The user will run Stage 1–4 locally; this turn uses Maven validation only. The historical completed suite was 18 / 18. Fresh Stage results must not be claimed for this capability refactor.

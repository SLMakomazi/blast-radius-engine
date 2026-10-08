# 3. Architecture — every file and its connections

Read [README.md](README.md) for the system story and [SETUP.md](SETUP.md) to run it. This inventory covers every maintained tracked file plus the new visualization and presentation files; generated dependencies, build output, private `.env` files and Git internals are excluded.

## Connection map

```text
PaymentController → PaymentService → CustomerClient
  → CustomerController → CustomerService → DocumentClient
    → DocumentController → DocumentService → DocumentRepository → monitored postgres

LocalTelemetryProvider → Loki / Prometheus / Tempo / Actuator
TopologyService → RuntimeSpanProvider + TraceTopologyService + TopologyRepository
IncidentAnalysisService → TelemetryProvider + DependencyTopologyProvider
  → BlastRadiusGraphService + IncidentSeverityService → IncidentAnalysis
IncidentLifecycleScheduler → IncidentLifecycleService → IncidentRepository → diagnostic postgres
IncidentHistoryController → stored incident → React dashboard
  → DiagnosisService → GeminiDiagnosisProvider or DeterministicDiagnosisProvider
```

The backend is grouped by capability: incident, topology, telemetry, diagnosis, lifecycle and shared. Inside each, the current checkout uses Spring-oriented controller/service/model/provider/repository folders only where needed. `src/main` is runtime code; `src/test` holds checks and isolated inputs; `resources` holds settings, migrations and fixtures.

Dependency edges point from a caller to what it needs. Blast-radius paths run from a failed dependency toward its callers. The frontend draws those recorded paths; it does not connect adjacent cards or invent topology. Missing telemetry stays uncertain, and historical snapshots do not establish live health.

## File inventory

Each entry has a brief responsibility and links to direct collaborators or the build/runtime file that consumes it. Tests and supporting assets have their own entries. Parent directory headings show exactly where a file belongs.


### `Repository root/`

| File | What it does and where it connects |
|---|---|
| [.env.example](.env.example) | Supplies synthetic local settings and optional AI configuration that Compose reads from a private .env copy. Connects to [docker-compose.yml](docker-compose.yml). |
| [.gitignore](.gitignore) | Keeps credentials, generated Java and frontend output, local topology and editor files out of commits. It has no runtime service call. |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Maps every maintained repository file to its responsibility and concrete connections. Connects to [README.md](README.md), [PRESENTATION.md](PRESENTATION.md). |
| [LICENSE](LICENSE) | Defines the repository’s licensing terms for people using or distributing its source. It has no runtime service call. |
| [PRESENTATION.md](PRESENTATION.md) | Provides a short manager-facing story followed by one speaking note for every repository file. Connects to [README.md](README.md), [ARCHITECTURE.md](ARCHITECTURE.md). |
| [README.md](README.md) | Introduces the system, integration boundaries, topology, dashboard and the two-stage validation model. Connects to [SETUP.md](SETUP.md), [ARCHITECTURE.md](ARCHITECTURE.md), [PRESENTATION.md](PRESENTATION.md). |
| [SETUP.md](SETUP.md) | Guides the next operator through configuration, startup, readiness, testing and the visual demonstration. Connects to [docker-compose.yml](docker-compose.yml), [run-phase11-e2e.py](scripts/run-phase11-e2e.py), [run-degradation-e2e.py](scripts/run-degradation-e2e.py). |

### `blast-radius-api/`

The Spring Boot engine owns deterministic analysis, incident history, retained topology, provider adapters and optional explanation.

| File | What it does and where it connects |
|---|---|
| [.dockerignore](blast-radius-api/.dockerignore) | Excludes generated output and local-only files from this module’s Docker build context. Connects to [Dockerfile](blast-radius-api/Dockerfile). |
| [Dockerfile](blast-radius-api/Dockerfile) | Builds and verifies this Java module with Maven, then packages its runtime image for Compose. Connects to [docker-compose.yml](docker-compose.yml). |
| [pom.xml](blast-radius-api/pom.xml) | Defines this Java module’s dependencies, Java version and Maven build/test configuration. Connects to [Dockerfile](blast-radius-api/Dockerfile). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/`

| File | What it does and where it connects |
|---|---|
| [BlastRadiusApplication.java](blast-radius-api/src/main/java/com/madlanga/blastradius/BlastRadiusApplication.java) | Starts Spring Boot and discovers the engine’s feature components. Spring discovers this module’s components using [application.yml](blast-radius-api/src/main/resources/application.yml). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/config/`

| File | What it does and where it connects |
|---|---|
| [DiagnosisConfig.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/config/DiagnosisConfig.java) | Wires the request mapper, optional Gemini provider and deterministic fallback into DiagnosisService. Connects to [DeterministicDiagnosisProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/provider/DeterministicDiagnosisProvider.java), [DiagnosisProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/provider/DiagnosisProvider.java). |
| [GeminiProperties.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/config/GeminiProperties.java) | Binds the advisory model, fallback models, API endpoint, key and timeout settings. Connects to [DiagnosisConfig.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/config/DiagnosisConfig.java), [GeminiDiagnosisProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/provider/GeminiDiagnosisProvider.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/dto/`

| File | What it does and where it connects |
|---|---|
| [DiagnosisRequest.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/dto/DiagnosisRequest.java) | Carries the sanitized origin, impact, severity, evidence and coverage supplied to diagnosis providers. Connects to [DiagnosisRequestMapper.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/mapper/DiagnosisRequestMapper.java), [DeterministicDiagnosisProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/provider/DeterministicDiagnosisProvider.java). |
| [DiagnosisResponse.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/dto/DiagnosisResponse.java) | Carries the advisory summary, probable cause, recommended actions, limitations and provider information returned to the dashboard. Connects to [DeterministicDiagnosisProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/provider/DeterministicDiagnosisProvider.java), [DiagnosisProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/provider/DiagnosisProvider.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/mapper/`

| File | What it does and where it connects |
|---|---|
| [DiagnosisRequestMapper.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/mapper/DiagnosisRequestMapper.java) | Builds diagnosis requests from a current analysis or persisted JSON snapshot, sanitizing free text again before provider use. Connects to [DiagnosisRequest.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/dto/DiagnosisRequest.java), [EvidenceSignal.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/EvidenceSignal.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/provider/`

| File | What it does and where it connects |
|---|---|
| [DeterministicDiagnosisProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/provider/DeterministicDiagnosisProvider.java) | Produces an evidence-based explanation without an external AI call and serves as the final fallback. Connects to [DiagnosisProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/provider/DiagnosisProvider.java), [DiagnosisRequest.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/dto/DiagnosisRequest.java). |
| [DiagnosisProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/provider/DiagnosisProvider.java) | Defines the provider-neutral operation for explaining a diagnosis request. Connects to [DiagnosisRequest.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/dto/DiagnosisRequest.java), [DiagnosisResponse.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/dto/DiagnosisResponse.java). |
| [GeminiDiagnosisProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/provider/GeminiDiagnosisProvider.java) | Sends sanitized diagnosis context to Gemini and handles response parsing, bounded retries and configured model fallback. Connects to [DiagnosisProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/provider/DiagnosisProvider.java), [GeminiProperties.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/config/GeminiProperties.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/service/`

| File | What it does and where it connects |
|---|---|
| [DiagnosisService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/service/DiagnosisService.java) | Coordinates advisory diagnosis and returns the deterministic fallback when the primary provider fails. Connects to [DiagnosisProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/provider/DiagnosisProvider.java), [DiagnosisRequest.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/dto/DiagnosisRequest.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/incident/controller/`

| File | What it does and where it connects |
|---|---|
| [IncidentController.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/controller/IncidentController.java) | Exposes topology and analysis HTTP endpoints, validates request windows and delegates persisted analysis to the lifecycle service. Connects to [IncidentAnalysisService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/service/IncidentAnalysisService.java), [IncidentLifecycleService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/lifecycle/service/IncidentLifecycleService.java). |
| [IncidentHistoryController.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/controller/IncidentHistoryController.java) | Exposes incident history, detail, explicit resolution and advisory diagnosis to dashboard clients. Connects to [DiagnosisService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/service/DiagnosisService.java), [IncidentRepository.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/repository/IncidentRepository.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/incident/dto/`

| File | What it does and where it connects |
|---|---|
| [AnalyzeIncidentRequest.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/dto/AnalyzeIncidentRequest.java) | Carries the application, environment, analysis window and optional origin hint from the HTTP caller. Connects to [IncidentController.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/controller/IncidentController.java), [IncidentControllerTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/incident/controller/IncidentControllerTest.java). |
| [ErrorResponse.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/dto/ErrorResponse.java) | Defines the error code and safe message returned by the incident HTTP boundary. Connects to [IncidentController.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/controller/IncidentController.java), [IncidentHistoryController.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/controller/IncidentHistoryController.java). |
| [IncidentResponse.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/dto/IncidentResponse.java) | Defines the persisted incident fields and analysis snapshot returned to the frontend. Connects to [IncidentHistoryController.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/controller/IncidentHistoryController.java). |
| [ResolveIncidentRequest.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/dto/ResolveIncidentRequest.java) | Carries an optional resolution timestamp for the incident resolution endpoint. Connects to [IncidentHistoryController.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/controller/IncidentHistoryController.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/`

| File | What it does and where it connects |
|---|---|
| [ComponentImpact.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/ComponentImpact.java) | Describes one component’s origin, observed, theoretical, unknown or unexpected impact, with its path and evidence. Connects to [EvidenceSignal.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/EvidenceSignal.java). |
| [EvidenceSignal.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/EvidenceSignal.java) | Records a timestamped observation linking a component, evidence family, readable signal and source identifier. Connects to [DiagnosisRequestMapper.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/mapper/DiagnosisRequestMapper.java), [ComponentImpact.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/ComponentImpact.java). |
| [IncidentAnalysis.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/IncidentAnalysis.java) | Collects the deterministic analysis window, origin, impacts, timeline, severity, coverage and warnings. Connects to [ComponentImpact.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/ComponentImpact.java), [EvidenceSignal.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/EvidenceSignal.java). |
| [IncidentSeverity.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/IncidentSeverity.java) | Carries the calculated severity level, numeric score and reasons. Connects to [IncidentResponse.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/dto/IncidentResponse.java), [IncidentAnalysis.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/IncidentAnalysis.java). |
| [OriginAssessment.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/OriginAssessment.java) | Carries the selected likely origin, confidence, evidence score and supporting observations. Connects to [EvidenceSignal.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/EvidenceSignal.java). |
| [PersistedIncident.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/PersistedIncident.java) | Represents stored incident identity, lifecycle timestamps, origin, severity and the serialized analysis snapshot. Connects to [IncidentSeverity.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/IncidentSeverity.java), [OriginAssessment.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/OriginAssessment.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/incident/repository/`

| File | What it does and where it connects |
|---|---|
| [IncidentRepository.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/repository/IncidentRepository.java) | Defines incident lookup, history queries and persistence independently of SQL details. Connects to [PersistedIncident.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/PersistedIncident.java). |
| [JdbcIncidentRepository.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/repository/JdbcIncidentRepository.java) | Implements incident queries and PostgreSQL persistence, including the stored JSON analysis snapshot. Connects to [IncidentRepository.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/repository/IncidentRepository.java), [IncidentSeverity.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/IncidentSeverity.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/incident/service/`

| File | What it does and where it connects |
|---|---|
| [IncidentAnalysisService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/service/IncidentAnalysisService.java) | Correlates telemetry with topology, assesses a likely origin and combines graph impact with observed evidence and severity. Connects to [IncidentSeverityService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/service/IncidentSeverityService.java), [TelemetryProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/TelemetryProvider.java). |
| [IncidentSeverityService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/service/IncidentSeverityService.java) | Calculates deterministic severity from origin evidence and actual propagation rather than treating every possible impact as confirmed. Connects to [ComponentImpact.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/ComponentImpact.java), [EvidenceSignal.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/EvidenceSignal.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/lifecycle/scheduler/`

| File | What it does and where it connects |
|---|---|
| [IncidentLifecycleScheduler.java](blast-radius-api/src/main/java/com/madlanga/blastradius/lifecycle/scheduler/IncidentLifecycleScheduler.java) | Periodically detects failures and requires consecutive fully covered healthy windows before automatically resolving an incident. Connects to [IncidentRepository.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/repository/IncidentRepository.java), [IncidentAnalysisService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/service/IncidentAnalysisService.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/lifecycle/service/`

| File | What it does and where it connects |
|---|---|
| [IncidentLifecycleService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/lifecycle/service/IncidentLifecycleService.java) | Creates or updates the active incident for an origin, preserves incident history and handles resolution through the repository. Connects to [IncidentRepository.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/repository/IncidentRepository.java), [IncidentAnalysisService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/service/IncidentAnalysisService.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/shared/config/`

| File | What it does and where it connects |
|---|---|
| [OpenApiConfig.java](blast-radius-api/src/main/java/com/madlanga/blastradius/shared/config/OpenApiConfig.java) | Configures the engine’s generated OpenAPI metadata for HTTP API consumers. It has no runtime service call. |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/shared/sanitization/`

| File | What it does and where it connects |
|---|---|
| [RedactionRule.java](blast-radius-api/src/main/java/com/madlanga/blastradius/shared/sanitization/RedactionRule.java) | Defines a replaceable policy for identifying and redacting sensitive values. Connects to [TelemetrySanitizer.java](blast-radius-api/src/main/java/com/madlanga/blastradius/shared/sanitization/TelemetrySanitizer.java). |
| [TelemetrySanitizer.java](blast-radius-api/src/main/java/com/madlanga/blastradius/shared/sanitization/TelemetrySanitizer.java) | Applies built-in and pluggable redaction rules to attributes and free text before normalized evidence is stored, displayed or sent to AI. Connects to [RedactionRule.java](blast-radius-api/src/main/java/com/madlanga/blastradius/shared/sanitization/RedactionRule.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/config/`

| File | What it does and where it connects |
|---|---|
| [TelemetryConfig.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/config/TelemetryConfig.java) | Wires telemetry HTTP access and binds Loki, Prometheus, Tempo and Actuator settings. Connects to [ActuatorHealthAdapter.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/health/ActuatorHealthAdapter.java), [LokiLogAdapter.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/loki/LokiLogAdapter.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/`

| File | What it does and where it connects |
|---|---|
| [CoverageStatus.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/CoverageStatus.java) | Defines whether an evidence family is available, partial, unavailable or unsupported; it is not a service health score. Connects to [TelemetryBundle.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/TelemetryBundle.java). |
| [EvidenceFamily.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/EvidenceFamily.java) | Names the four evidence families: logs, metrics, traces and health. Connects to [EvidenceProvenance.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/EvidenceProvenance.java), [ActuatorHealthAdapter.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/health/ActuatorHealthAdapter.java). |
| [EvidenceProvenance.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/EvidenceProvenance.java) | Preserves the provider, collection time, family and bounded source reference for an observation. Connects to [EvidenceFamily.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/EvidenceFamily.java). |
| [HealthEvidence.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/HealthEvidence.java) | Represents a component health observation with its state, timestamp, endpoint details and provenance. Connects to [CoverageStatus.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/CoverageStatus.java), [EvidenceProvenance.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/EvidenceProvenance.java). |
| [HealthState.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/HealthState.java) | Defines provider-neutral health states used in normalized health observations. Connects to [HealthEvidence.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/HealthEvidence.java), [ActuatorHealthAdapter.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/health/ActuatorHealthAdapter.java). |
| [LogEvidence.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/LogEvidence.java) | Represents a sanitized log event, preserving component identity, time, severity and trace or correlation identifiers. Connects to [EvidenceProvenance.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/EvidenceProvenance.java). |
| [MetricEvidence.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/MetricEvidence.java) | Represents a timestamped numerical measurement with its name, unit, component and sanitized dimensions. Connects to [EvidenceProvenance.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/EvidenceProvenance.java). |
| [SpanEvidence.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/SpanEvidence.java) | Represents one request or dependency-call span with timing, error status, parent, peer and provenance. Connects to [EvidenceProvenance.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/EvidenceProvenance.java), [SpanKind.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/SpanKind.java). |
| [SpanKind.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/SpanKind.java) | Distinguishes request roles so a server error is not automatically mistaken for an outbound dependency call. Connects to [IncidentAnalysisService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/service/IncidentAnalysisService.java), [SpanEvidence.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/SpanEvidence.java). |
| [SpanStatus.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/SpanStatus.java) | Defines normalized span success, failure and unset status independently of a tracing vendor. Connects to [SpanEvidence.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/SpanEvidence.java), [TempoTraceAdapter.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/tempo/TempoTraceAdapter.java). |
| [TelemetryBundle.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/TelemetryBundle.java) | Groups normalized evidence, family coverage and warnings for one analysis request. Connects to [HealthEvidence.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/HealthEvidence.java), [LogEvidence.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/LogEvidence.java). |
| [TelemetryCoverage.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/TelemetryCoverage.java) | Records availability separately for each evidence family and exposes whether the window is fully covered. Connects to [CoverageStatus.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/CoverageStatus.java), [TelemetryBundle.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/TelemetryBundle.java). |
| [TelemetryQuery.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/TelemetryQuery.java) | Carries application scope, environment, time window and optional component, correlation or trace filters. Connects to [IncidentAnalysisService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/service/IncidentAnalysisService.java), [LocalTelemetryProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/LocalTelemetryProvider.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/`

| File | What it does and where it connects |
|---|---|
| [LocalTelemetryProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/LocalTelemetryProvider.java) | Combines four telemetry adapters while isolating provider failures so one missing source does not suppress the others. Connects to [TelemetryProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/TelemetryProvider.java), [ActuatorHealthAdapter.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/health/ActuatorHealthAdapter.java). |
| [TelemetryProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/TelemetryProvider.java) | Defines how incident analysis obtains sanitized, normalized evidence and coverage from an external telemetry source. Connects to [TelemetryBundle.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/TelemetryBundle.java), [TelemetryQuery.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/TelemetryQuery.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/health/`

| File | What it does and where it connects |
|---|---|
| [ActuatorHealthAdapter.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/health/ActuatorHealthAdapter.java) | Probes configured service health endpoints and maps responses or unreachable probes into health evidence and coverage. Connects to [TelemetrySanitizer.java](blast-radius-api/src/main/java/com/madlanga/blastradius/shared/sanitization/TelemetrySanitizer.java), [CoverageStatus.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/CoverageStatus.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/loki/`

| File | What it does and where it connects |
|---|---|
| [LokiLogAdapter.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/loki/LokiLogAdapter.java) | Queries Loki log streams, parses their provider-specific shape and produces sanitized LogEvidence. Connects to [TelemetrySanitizer.java](blast-radius-api/src/main/java/com/madlanga/blastradius/shared/sanitization/TelemetrySanitizer.java), [CoverageStatus.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/CoverageStatus.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/prometheus/`

| File | What it does and where it connects |
|---|---|
| [PrometheusMetricsAdapter.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/prometheus/PrometheusMetricsAdapter.java) | Queries configured Prometheus measurement series and converts samples into MetricEvidence with preserved timestamps. Connects to [TelemetrySanitizer.java](blast-radius-api/src/main/java/com/madlanga/blastradius/shared/sanitization/TelemetrySanitizer.java), [CoverageStatus.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/CoverageStatus.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/tempo/`

| File | What it does and where it connects |
|---|---|
| [TempoTraceAdapter.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/tempo/TempoTraceAdapter.java) | Searches Tempo and retrieves traces, mapping OTLP spans and peer identities into normalized SpanEvidence. Connects to [TelemetrySanitizer.java](blast-radius-api/src/main/java/com/madlanga/blastradius/shared/sanitization/TelemetrySanitizer.java), [CoverageStatus.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/CoverageStatus.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/topology/config/`

| File | What it does and where it connects |
|---|---|
| [TopologyConfig.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/config/TopologyConfig.java) | Connects Tempo span acquisition to topology learning, file persistence and scheduled refresh for the configured application. Connects to [TempoTraceAdapter.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/tempo/TempoTraceAdapter.java), [FileTopologyRepository.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/repository/FileTopologyRepository.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/`

| File | What it does and where it connects |
|---|---|
| [BlastRadiusResult.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/BlastRadiusResult.java) | Holds the graph origin and deterministically ordered theoretical impact results. Connects to [ComponentNode.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/ComponentNode.java), [TheoreticalImpact.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/TheoreticalImpact.java). |
| [ComponentNode.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/ComponentNode.java) | Defines a canonical component identity, type, technology and metadata in the dependency graph. Connects to [ComponentType.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/ComponentType.java). |
| [ComponentType.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/ComponentType.java) | Distinguishes kinds of topology components such as services and databases. Connects to [ComponentNode.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/ComponentNode.java), [RetainedTopology.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/RetainedTopology.java). |
| [DependencyEdge.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/DependencyEdge.java) | Stores a directed relationship: the dependent needs the dependency. Connects to [IncidentAnalysisService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/service/IncidentAnalysisService.java), [DependencyTopology.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/DependencyTopology.java). |
| [DependencyTopology.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/DependencyTopology.java) | Groups the canonical nodes and directed dependencies for an application and environment. Connects to [ComponentNode.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/ComponentNode.java), [DependencyEdge.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/DependencyEdge.java). |
| [RetainedTopology.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/RetainedTopology.java) | Stores learned graph relationships and observation timestamps across requests and restarts without turning old knowledge into current incident evidence. Connects to [ComponentType.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/ComponentType.java). |
| [TheoreticalImpact.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/TheoreticalImpact.java) | Describes a reachable dependent, its minimum distance and path from a possible failure origin. Connects to [ComponentNode.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/ComponentNode.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/topology/provider/`

| File | What it does and where it connects |
|---|---|
| [DependencyTopologyProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/provider/DependencyTopologyProvider.java) | Defines the topology contract through which incident analysis can use local discovery or a future enterprise architecture source. Connects to [DependencyTopology.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/DependencyTopology.java). |
| [RuntimeSpanProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/provider/RuntimeSpanProvider.java) | Supplies normalized spans for topology discovery separately from the incident’s full telemetry bundle. Connects to [SpanEvidence.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/SpanEvidence.java), [TelemetryQuery.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/TelemetryQuery.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/topology/repository/`

| File | What it does and where it connects |
|---|---|
| [FileTopologyRepository.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/repository/FileTopologyRepository.java) | Loads and atomically replaces retained topology files, scoped by application and environment. Connects to [TopologyRepository.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/repository/TopologyRepository.java), [RetainedTopology.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/RetainedTopology.java). |
| [TopologyRepository.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/repository/TopologyRepository.java) | Defines loading and saving retained topology without coupling learning to a file format. Connects to [RetainedTopology.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/RetainedTopology.java). |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/topology/service/`

| File | What it does and where it connects |
|---|---|
| [BlastRadiusGraphService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/service/BlastRadiusGraphService.java) | Traverses dependencies in reverse to calculate affected dependents, stable shortest paths and distances while handling branches and cycles. Connects to [BlastRadiusResult.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/BlastRadiusResult.java), [ComponentNode.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/ComponentNode.java). |
| [TopologyService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/service/TopologyService.java) | Merges new span observations with retained knowledge, rejects invalid scope or timestamps and expires stale nodes and relationships. Connects to [DependencyTopologyProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/provider/DependencyTopologyProvider.java), [RuntimeSpanProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/provider/RuntimeSpanProvider.java). |
| [TraceTopologyService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/service/TraceTopologyService.java) | Discovers component relationships from parent-child spans and explicit peers without inventing dependencies from technology names alone. Connects to [TelemetryProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/TelemetryProvider.java), [DependencyTopologyProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/provider/DependencyTopologyProvider.java). |

### `blast-radius-api/src/main/resources/`

| File | What it does and where it connects |
|---|---|
| [application.yml](blast-radius-api/src/main/resources/application.yml) | Configures diagnostic persistence, telemetry endpoints, topology retention, lifecycle windows and optional diagnosis settings. Connects to [docker-compose.yml](docker-compose.yml). |

### `blast-radius-api/src/main/resources/db/migration/`

| File | What it does and where it connects |
|---|---|
| [V1__create_incidents.sql](blast-radius-api/src/main/resources/db/migration/V1__create_incidents.sql) | Creates the diagnostic incidents table used by JdbcIncidentRepository. Connects to [JdbcIncidentRepository.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/repository/JdbcIncidentRepository.java). |
| [V2__enforce_single_active_incident.sql](blast-radius-api/src/main/resources/db/migration/V2__enforce_single_active_incident.sql) | Adds the database constraint that prevents duplicate active incidents for the same application, environment and origin. Connects to [JdbcIncidentRepository.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/repository/JdbcIncidentRepository.java). |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/`

| File | What it does and where it connects |
|---|---|
| [ArchitectureBoundaryTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/ArchitectureBoundaryTest.java) | Checks that Java package placement and feature dependency boundaries match the repository’s architecture rules. It has no runtime service call. |
| [BlastRadiusApplicationTests.java](blast-radius-api/src/test/java/com/madlanga/blastradius/BlastRadiusApplicationTests.java) | Checks that the Spring application context starts with the test configuration. It has no runtime service call. |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/diagnosis/mapper/`

| File | What it does and where it connects |
|---|---|
| [DiagnosisRequestMapperStoredJsonTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/diagnosis/mapper/DiagnosisRequestMapperStoredJsonTest.java) | Checks that stored analysis JSON maps directly to advisory context and sensitive free text is redacted again. Connects to [DiagnosisRequest.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/dto/DiagnosisRequest.java), [DiagnosisRequestMapper.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/mapper/DiagnosisRequestMapper.java). |
| [DiagnosisRequestMapperTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/diagnosis/mapper/DiagnosisRequestMapperTest.java) | Checks that advisory context is derived only from deterministic analysis. Connects to [DiagnosisRequest.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/dto/DiagnosisRequest.java), [DiagnosisRequestMapper.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/mapper/DiagnosisRequestMapper.java). |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/diagnosis/provider/`

| File | What it does and where it connects |
|---|---|
| [GeminiDiagnosisProviderTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/diagnosis/provider/GeminiDiagnosisProviderTest.java) | Exercises response parsing, transient failures, retries, fallback models and safe provider errors against a controlled HTTP server. Connects to [GeminiDiagnosisProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/provider/GeminiDiagnosisProvider.java), [GeminiProperties.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/config/GeminiProperties.java). |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/diagnosis/service/`

| File | What it does and where it connects |
|---|---|
| [DiagnosisServiceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/diagnosis/service/DiagnosisServiceTest.java) | Checks that provider failures fall back to deterministic diagnosis without breaking incident analysis. Connects to [DeterministicDiagnosisProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/provider/DeterministicDiagnosisProvider.java), [DiagnosisProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/provider/DiagnosisProvider.java). |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/incident/controller/`

| File | What it does and where it connects |
|---|---|
| [IncidentControllerTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/incident/controller/IncidentControllerTest.java) | Checks analysis request defaults, invalid time windows, origin validation and HTTP error responses. Connects to [IncidentController.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/controller/IncidentController.java), [IncidentAnalysisService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/service/IncidentAnalysisService.java). |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/incident/service/`

| File | What it does and where it connects |
|---|---|
| [IncidentAnalysisServiceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/incident/service/IncidentAnalysisServiceTest.java) | Checks likely-origin selection, observed versus possible impact, partial coverage, unexpected evidence and counter-based degradation. Connects to [IncidentAnalysisService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/service/IncidentAnalysisService.java), [TelemetryProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/TelemetryProvider.java). |
| [IncidentSeverityServiceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/incident/service/IncidentSeverityServiceTest.java) | Checks severity thresholds and ensures theoretical propagation alone does not inflate severity. Connects to [IncidentSeverityService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/service/IncidentSeverityService.java), [ComponentImpact.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/ComponentImpact.java). |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/lifecycle/service/`

| File | What it does and where it connects |
|---|---|
| [IncidentLifecycleServiceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/lifecycle/service/IncidentLifecycleServiceTest.java) | Checks active-incident persistence, explicit resolution and retention of peak severity and history. Connects to [IncidentRepository.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/repository/IncidentRepository.java), [IncidentAnalysisService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/service/IncidentAnalysisService.java). |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/shared/sanitization/`

| File | What it does and where it connects |
|---|---|
| [TelemetrySanitizerTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/shared/sanitization/TelemetrySanitizerTest.java) | Checks secret and sensitive-value redaction in structured attributes and free text. Connects to [RedactionRule.java](blast-radius-api/src/main/java/com/madlanga/blastradius/shared/sanitization/RedactionRule.java), [TelemetrySanitizer.java](blast-radius-api/src/main/java/com/madlanga/blastradius/shared/sanitization/TelemetrySanitizer.java). |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/model/`

| File | What it does and where it connects |
|---|---|
| [EvidenceProvenanceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/model/EvidenceProvenanceTest.java) | Checks provenance validation, stable fields and equality. Connects to [EvidenceFamily.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/EvidenceFamily.java), [EvidenceProvenance.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/EvidenceProvenance.java). |
| [HealthEvidenceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/model/HealthEvidenceTest.java) | Checks health states, timestamps, details and the distinction between observations and missing data. Connects to [EvidenceFamily.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/EvidenceFamily.java), [EvidenceProvenance.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/EvidenceProvenance.java). |
| [LogEvidenceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/model/LogEvidenceTest.java) | Checks log identity, timestamp, correlation fields and immutable sanitized attributes. Connects to [EvidenceFamily.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/EvidenceFamily.java), [EvidenceProvenance.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/EvidenceProvenance.java). |
| [MetricEvidenceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/model/MetricEvidenceTest.java) | Checks measurement timestamps, dimensions, units and immutable evidence fields. Connects to [EvidenceFamily.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/EvidenceFamily.java), [EvidenceProvenance.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/EvidenceProvenance.java). |
| [SpanEvidenceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/model/SpanEvidenceTest.java) | Checks trace relationships, duration, error representation and dependency-call information. Connects to [EvidenceFamily.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/EvidenceFamily.java), [EvidenceProvenance.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/EvidenceProvenance.java). |
| [TelemetryBundleTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/model/TelemetryBundleTest.java) | Checks evidence aggregation, warnings and coverage without interpreting absent evidence as healthy. Connects to [CoverageStatus.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/CoverageStatus.java), [EvidenceFamily.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/EvidenceFamily.java). |
| [TelemetryQueryTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/model/TelemetryQueryTest.java) | Checks scope requirements, optional filters and valid time-window boundaries. Connects to [TelemetryQuery.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/TelemetryQuery.java). |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/`

| File | What it does and where it connects |
|---|---|
| [LocalTelemetryProviderLiveIT.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/LocalTelemetryProviderLiveIT.java) | Queries a running local lab to verify normalized evidence from actual telemetry providers; this is an opt-in live test. Connects to [TelemetryProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/TelemetryProvider.java), [CoverageStatus.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/CoverageStatus.java). |
| [LocalTelemetryProviderTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/LocalTelemetryProviderTest.java) | Checks aggregation and isolation when one or more telemetry providers fail. Connects to [LocalTelemetryProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/LocalTelemetryProvider.java), [TelemetryProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/TelemetryProvider.java). |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/health/`

| File | What it does and where it connects |
|---|---|
| [ActuatorHealthAdapterTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/health/ActuatorHealthAdapterTest.java) | Checks health normalization, unreachable probes, component filtering and provenance. Connects to [ActuatorHealthAdapter.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/health/ActuatorHealthAdapter.java), [TelemetrySanitizer.java](blast-radius-api/src/main/java/com/madlanga/blastradius/shared/sanitization/TelemetrySanitizer.java). |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/loki/`

| File | What it does and where it connects |
|---|---|
| [LokiLogAdapterTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/loki/LokiLogAdapterTest.java) | Checks real-shaped log parsing, correlation fields, redaction and unavailable-provider handling. Connects to [LokiLogAdapter.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/loki/LokiLogAdapter.java), [TelemetrySanitizer.java](blast-radius-api/src/main/java/com/madlanga/blastradius/shared/sanitization/TelemetrySanitizer.java). |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/prometheus/`

| File | What it does and where it connects |
|---|---|
| [PrometheusMetricsAdapterTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/prometheus/PrometheusMetricsAdapterTest.java) | Checks measurement series mapping, original sample timestamps and label sanitization. Connects to [PrometheusMetricsAdapter.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/prometheus/PrometheusMetricsAdapter.java), [TelemetrySanitizer.java](blast-radius-api/src/main/java/com/madlanga/blastradius/shared/sanitization/TelemetrySanitizer.java). |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/tempo/`

| File | What it does and where it connects |
|---|---|
| [CapturedTempoRegressionTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/tempo/CapturedTempoRegressionTest.java) | Uses captured trace fixtures to check retained topology, peerless failures and exclusion of stale evidence. Connects to [IncidentAnalysisService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/service/IncidentAnalysisService.java), [TempoTraceAdapter.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/tempo/TempoTraceAdapter.java). |
| [TempoTraceAdapterTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/tempo/TempoTraceAdapterTest.java) | Checks OTLP parsing, trace ID normalization, span error status and incomplete retrieval handling. Connects to [TempoTraceAdapter.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/tempo/TempoTraceAdapter.java), [TelemetrySanitizer.java](blast-radius-api/src/main/java/com/madlanga/blastradius/shared/sanitization/TelemetrySanitizer.java). |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/topology/model/`

| File | What it does and where it connects |
|---|---|
| [TopologyModelTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/topology/model/TopologyModelTest.java) | Checks graph collection immutability, self-dependency rejection and impact path consistency. Connects to [ComponentNode.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/ComponentNode.java), [ComponentType.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/ComponentType.java). |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/topology/service/`

| File | What it does and where it connects |
|---|---|
| [BlastRadiusGraphServiceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/topology/service/BlastRadiusGraphServiceTest.java) | Checks chain, branching, cycle, duplicate and disconnected graph behavior with stable shortest paths. Connects to [BlastRadiusGraphService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/service/BlastRadiusGraphService.java), [BlastRadiusResult.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/BlastRadiusResult.java). |
| [TopologyServiceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/topology/service/TopologyServiceTest.java) | Checks durable learning, restart behavior, expiry, scope separation and corrupt-store handling. Connects to [FileTopologyRepository.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/repository/FileTopologyRepository.java), [BlastRadiusGraphService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/service/BlastRadiusGraphService.java). |
| [TraceTopologyServiceLiveIT.java](blast-radius-api/src/test/java/com/madlanga/blastradius/topology/service/TraceTopologyServiceLiveIT.java) | Checks discovery of the canonical dependency chain from real Tempo evidence in the running lab. Connects to [TelemetryProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/TelemetryProvider.java), [BlastRadiusGraphService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/service/BlastRadiusGraphService.java). |
| [TraceTopologyServiceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/topology/service/TraceTopologyServiceTest.java) | Checks parent-child and database-peer discovery and dependency deduplication. Connects to [BlastRadiusGraphService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/service/BlastRadiusGraphService.java), [TraceTopologyService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/service/TraceTopologyService.java). |

### `blast-radius-api/src/test/resources/`

| File | What it does and where it connects |
|---|---|
| [application.yml](blast-radius-api/src/test/resources/application.yml) | Supplies test-only Spring settings so this module’s tests can run without its normal production database and scheduling configuration. Connects to [pom.xml](blast-radius-api/pom.xml). |

### `blast-radius-api/src/test/resources/tempo/`

| File | What it does and where it connects |
|---|---|
| [failed-connection.json](blast-radius-api/src/test/resources/tempo/failed-connection.json) | Provides a captured failed-connection trace for offline Tempo and topology regression tests; it is not live incident evidence. Connects to [pom.xml](blast-radius-api/pom.xml). |
| [healthy-database.json](blast-radius-api/src/test/resources/tempo/healthy-database.json) | Provides a captured healthy database trace for offline discovery and retention regression tests; it is not proof of current health. Connects to [pom.xml](blast-radius-api/pom.xml). |

### `Repository root/`

| File | What it does and where it connects |
|---|---|
| [docker-compose.yml](docker-compose.yml) | Wires the 12 local services, dependency startup order, localhost ports, credentials, resource limits and persistent volumes. Connects to [.env.example](.env.example), [Dockerfile](blast-radius-api/Dockerfile), [Dockerfile](frontend/Dockerfile). |

### `frontend/`

The browser reads existing incident APIs; visualization translates snapshots but never changes the backend’s conclusions.

| File | What it does and where it connects |
|---|---|
| [.dockerignore](frontend/.dockerignore) | Excludes generated output and local-only files from this module’s Docker build context. Connects to [Dockerfile](frontend/Dockerfile). |
| [.env.example](frontend/.env.example) | Documents the frontend API base URL; an empty value uses the Nginx same-origin proxy in Compose. Connects to [nginx.conf](frontend/nginx.conf). |
| [Dockerfile](frontend/Dockerfile) | Builds the Vite bundle and serves it with an unprivileged Nginx image and the API proxy configuration. Connects to [docker-compose.yml](docker-compose.yml). |
| [index.html](frontend/index.html) | Provides the root HTML element and loads the React entry point. Connects to [main.jsx](frontend/src/main.jsx). |
| [nginx.conf](frontend/nginx.conf) | Serves the built frontend and proxies /api requests to blast-radius-api on the internal Compose network. Connects to [docker-compose.yml](docker-compose.yml). |
| [package-lock.json](frontend/package-lock.json) | Locks frontend dependency versions for repeatable npm ci installs and container builds. Connects to [package.json](frontend/package.json). |
| [package.json](frontend/package.json) | Defines the React, Vite and icon dependencies and frontend build commands. Connects to [package-lock.json](frontend/package-lock.json), [Dockerfile](frontend/Dockerfile). |

### `frontend/src/`

| File | What it does and where it connects |
|---|---|
| [ServiceMap.jsx](frontend/src/ServiceMap.jsx) | Renders recorded dependency branches, selectable explanations, impact and evidence charts, and the local copy-handoff action. Connects to [incident-visuals.js](frontend/src/incident-visuals.js), [styles.css](frontend/src/styles.css). |
| [incident-visuals.js](frontend/src/incident-visuals.js) | Converts backend snapshots into cautious status labels, plain-language explanations, real path edges and timestamped chart buckets. Connects to [ServiceMap.jsx](frontend/src/ServiceMap.jsx), [incident-visuals.test.js](frontend/src/incident-visuals.test.js). |
| [incident-visuals.test.js](frontend/src/incident-visuals.test.js) | Checks that visualization logic preserves uncertainty, branching, historical status, evidence counts and searchable symptoms. Connects to [incident-visuals.js](frontend/src/incident-visuals.js). |
| [main.jsx](frontend/src/main.jsx) | Loads and polls incident REST data, preserves selection and diagnosis state, and composes the dashboard and evidence filters. Connects to [ServiceMap.jsx](frontend/src/ServiceMap.jsx), [incident-visuals.js](frontend/src/incident-visuals.js), [nginx.conf](frontend/nginx.conf). |
| [styles.css](frontend/src/styles.css) | Styles the responsive dashboard, evidence panels, status colors and motion-aware risk indicator. Connects to [main.jsx](frontend/src/main.jsx), [ServiceMap.jsx](frontend/src/ServiceMap.jsx). |

### `infrastructure/observability/logging/`

| File | What it does and where it connects |
|---|---|
| [loki.yml](infrastructure/observability/logging/loki.yml) | Configures the local Loki log receiver, storage and retention used by the Collector and LokiLogAdapter. Connects to [docker-compose.yml](docker-compose.yml). |

### `infrastructure/observability/otel/`

| File | What it does and where it connects |
|---|---|
| [collector.yml](infrastructure/observability/otel/collector.yml) | Receives service telemetry, applies privacy processing and exports logs to Loki and traces to Tempo. Connects to [docker-compose.yml](docker-compose.yml). |
| [javaagent.properties](infrastructure/observability/otel/javaagent.properties) | Configures service instrumentation and OTLP export to the Collector while limiting sensitive capture. Connects to [docker-compose.yml](docker-compose.yml). |

### `infrastructure/observability/prometheus/`

| File | What it does and where it connects |
|---|---|
| [prometheus.yml](infrastructure/observability/prometheus/prometheus.yml) | Defines the local scrape targets and interval for service measurements consumed by PrometheusMetricsAdapter. Connects to [docker-compose.yml](docker-compose.yml). |

### `infrastructure/observability/tracing/`

| File | What it does and where it connects |
|---|---|
| [tempo.yml](infrastructure/observability/tracing/tempo.yml) | Configures the local trace receiver, storage and query service used by TempoTraceAdapter. Connects to [docker-compose.yml](docker-compose.yml). |

### `mock-services/customer-service/`

| File | What it does and where it connects |
|---|---|
| [.dockerignore](mock-services/customer-service/.dockerignore) | Excludes generated output and local-only files from this module’s Docker build context. Connects to [Dockerfile](mock-services/customer-service/Dockerfile). |
| [Dockerfile](mock-services/customer-service/Dockerfile) | Builds and verifies this Java module with Maven, then packages its runtime image for Compose. Connects to [docker-compose.yml](docker-compose.yml). |
| [pom.xml](mock-services/customer-service/pom.xml) | Defines this Java module’s dependencies, Java version and Maven build/test configuration. Connects to [Dockerfile](mock-services/customer-service/Dockerfile). |

### `mock-services/customer-service/src/main/java/com/madlanga/lab/customer/`

| File | What it does and where it connects |
|---|---|
| [CustomerApplication.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/CustomerApplication.java) | Starts the independently deployable synthetic customer application. Spring discovers this module’s components using [application.yml](mock-services/customer-service/src/main/resources/application.yml). |

### `mock-services/customer-service/src/main/java/com/madlanga/lab/customer/client/`

| File | What it does and where it connects |
|---|---|
| [DocumentClient.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/client/DocumentClient.java) | Calls document-service from customer-service and translates downstream errors into DownstreamException. Connects to [DocumentReceipt.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/DocumentReceipt.java), [DownstreamRequest.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/DownstreamRequest.java). |

### `mock-services/customer-service/src/main/java/com/madlanga/lab/customer/config/`

| File | What it does and where it connects |
|---|---|
| [CorrelationIdFilter.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/config/CorrelationIdFilter.java) | Validates or generates a business correlation ID and makes it available to request logs and downstream calls. It has no runtime service call. |
| [HttpClientConfig.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/config/HttpClientConfig.java) | Configures the service’s outbound HTTP client with timeouts and observation support for downstream requests. It has no runtime service call. |

### `mock-services/customer-service/src/main/java/com/madlanga/lab/customer/controller/`

| File | What it does and where it connects |
|---|---|
| [CustomerController.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/controller/CustomerController.java) | Accepts customer validation HTTP requests and delegates to CustomerService. Connects to [CustomerService.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/service/CustomerService.java), [CustomerRequest.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/CustomerRequest.java). |

### `mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/`

| File | What it does and where it connects |
|---|---|
| [ApiError.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/ApiError.java) | Defines the sanitized code, message and correlation information returned for a synthetic service failure. Connects to [ApiExceptionHandler.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/exception/ApiExceptionHandler.java). |
| [CustomerRequest.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/CustomerRequest.java) | Validates the incoming customer and document fields before CustomerController delegates the request. Connects to [CustomerController.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/controller/CustomerController.java), [CustomerService.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/service/CustomerService.java). |
| [CustomerValidation.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/CustomerValidation.java) | Carries the customer validation outcome and related document result between customer and payment services. Connects to [DocumentReceipt.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/DocumentReceipt.java). |
| [DocumentReceipt.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/DocumentReceipt.java) | Represents the document identifier and receipt returned along the synthetic service chain. Connects to [DocumentClient.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/client/DocumentClient.java), [CustomerValidation.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/CustomerValidation.java). |
| [DownstreamRequest.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/DownstreamRequest.java) | Carries the fields sent to the next service in the synthetic request chain. Connects to [DocumentClient.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/client/DocumentClient.java), [CustomerService.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/service/CustomerService.java). |

### `mock-services/customer-service/src/main/java/com/madlanga/lab/customer/exception/`

| File | What it does and where it connects |
|---|---|
| [ApiExceptionHandler.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/exception/ApiExceptionHandler.java) | Translates validation, downstream or persistence failures into sanitized HTTP errors appropriate to its synthetic service. Connects to [ApiError.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/ApiError.java), [DownstreamException.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/exception/DownstreamException.java). |
| [DownstreamException.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/exception/DownstreamException.java) | Carries a downstream-call failure to the service’s HTTP exception handler without exposing the raw remote response. Connects to [DocumentClient.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/client/DocumentClient.java), [ApiExceptionHandler.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/exception/ApiExceptionHandler.java). |

### `mock-services/customer-service/src/main/java/com/madlanga/lab/customer/service/`

| File | What it does and where it connects |
|---|---|
| [CustomerService.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/service/CustomerService.java) | Performs synthetic customer validation and obtains a document receipt through DocumentClient. Connects to [DocumentClient.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/client/DocumentClient.java), [CustomerRequest.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/CustomerRequest.java). |

### `mock-services/customer-service/src/main/resources/`

| File | What it does and where it connects |
|---|---|
| [application.yml](mock-services/customer-service/src/main/resources/application.yml) | Configures this synthetic service’s port, health and metrics exposure, logging and downstream or database connection. Connects to [docker-compose.yml](docker-compose.yml). |

### `mock-services/customer-service/src/test/java/com/madlanga/lab/customer/`

| File | What it does and where it connects |
|---|---|
| [CustomerApplicationTests.java](mock-services/customer-service/src/test/java/com/madlanga/lab/customer/CustomerApplicationTests.java) | Checks customer endpoints, validation, correlation propagation and controlled downstream failures. It has no runtime service call. |

### `mock-services/customer-service/src/test/resources/mockito-extensions/`

| File | What it does and where it connects |
|---|---|
| [org.mockito.plugins.MockMaker](mock-services/customer-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker) | Selects the Mockito test mock implementation used by this service’s test suite. Connects to [pom.xml](mock-services/customer-service/pom.xml). |

### `mock-services/document-service/`

| File | What it does and where it connects |
|---|---|
| [.dockerignore](mock-services/document-service/.dockerignore) | Excludes generated output and local-only files from this module’s Docker build context. Connects to [Dockerfile](mock-services/document-service/Dockerfile). |
| [Dockerfile](mock-services/document-service/Dockerfile) | Builds and verifies this Java module with Maven, then packages its runtime image for Compose. Connects to [docker-compose.yml](docker-compose.yml). |
| [pom.xml](mock-services/document-service/pom.xml) | Defines this Java module’s dependencies, Java version and Maven build/test configuration. Connects to [Dockerfile](mock-services/document-service/Dockerfile). |

### `mock-services/document-service/src/main/java/com/madlanga/lab/document/`

| File | What it does and where it connects |
|---|---|
| [DocumentApplication.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/DocumentApplication.java) | Starts the independently deployable synthetic document application. Spring discovers this module’s components using [application.yml](mock-services/document-service/src/main/resources/application.yml). |

### `mock-services/document-service/src/main/java/com/madlanga/lab/document/config/`

| File | What it does and where it connects |
|---|---|
| [CorrelationIdFilter.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/config/CorrelationIdFilter.java) | Validates or generates a business correlation ID and makes it available to request logs and downstream calls. It has no runtime service call. |

### `mock-services/document-service/src/main/java/com/madlanga/lab/document/controller/`

| File | What it does and where it connects |
|---|---|
| [DocumentController.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/controller/DocumentController.java) | Accepts document HTTP requests and delegates to DocumentService. Connects to [DocumentService.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/service/DocumentService.java), [DocumentReceipt.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/dto/DocumentReceipt.java). |
| [FaultInjectionController.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/controller/FaultInjectionController.java) | Exposes the local lab endpoint for inspecting, setting and resetting document-service fault modes. Connects to [FaultConfig.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/fault/FaultConfig.java), [FaultInjectionService.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/fault/FaultInjectionService.java). |

### `mock-services/document-service/src/main/java/com/madlanga/lab/document/dto/`

| File | What it does and where it connects |
|---|---|
| [ApiError.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/dto/ApiError.java) | Defines the sanitized code, message and correlation information returned for a synthetic service failure. Connects to [ApiExceptionHandler.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/exception/ApiExceptionHandler.java). |
| [DocumentReceipt.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/dto/DocumentReceipt.java) | Represents the document identifier and receipt returned along the synthetic service chain. Connects to [DocumentController.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/controller/DocumentController.java), [DocumentRepository.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/persistence/DocumentRepository.java). |
| [DocumentRequest.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/dto/DocumentRequest.java) | Validates the document fields before DocumentController asks the service to persist a receipt. Connects to [DocumentController.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/controller/DocumentController.java), [DocumentService.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/service/DocumentService.java). |

### `mock-services/document-service/src/main/java/com/madlanga/lab/document/exception/`

| File | What it does and where it connects |
|---|---|
| [ApiExceptionHandler.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/exception/ApiExceptionHandler.java) | Translates validation, downstream or persistence failures into sanitized HTTP errors appropriate to its synthetic service. Connects to [ApiError.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/dto/ApiError.java), [SyntheticFaultException.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/fault/SyntheticFaultException.java). |

### `mock-services/document-service/src/main/java/com/madlanga/lab/document/fault/`

| File | What it does and where it connects |
|---|---|
| [FaultConfig.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/fault/FaultConfig.java) | Bounds the selected synthetic fault mode, delay and failure cadence before injection. Connects to [FaultMode.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/fault/FaultMode.java). |
| [FaultInjectionService.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/fault/FaultInjectionService.java) | Applies bounded in-process lab faults while keeping the service running, and resets the configuration on request. Connects to [FaultConfig.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/fault/FaultConfig.java), [FaultMode.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/fault/FaultMode.java). |
| [FaultMode.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/fault/FaultMode.java) | Names healthy, continuous error, intermittent error, latency and database-connectivity lab modes. Connects to [FaultConfig.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/fault/FaultConfig.java), [FaultInjectionService.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/fault/FaultInjectionService.java). |
| [SyntheticFaultException.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/fault/SyntheticFaultException.java) | Signals an intentional application fault to the mock service’s error handler. Connects to [ApiExceptionHandler.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/exception/ApiExceptionHandler.java), [FaultInjectionService.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/fault/FaultInjectionService.java). |

### `mock-services/document-service/src/main/java/com/madlanga/lab/document/persistence/`

| File | What it does and where it connects |
|---|---|
| [DocumentRepository.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/persistence/DocumentRepository.java) | Uses JDBC to store and retrieve synthetic documents in the monitored PostgreSQL database. Connects to [DocumentReceipt.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/dto/DocumentReceipt.java). |

### `mock-services/document-service/src/main/java/com/madlanga/lab/document/service/`

| File | What it does and where it connects |
|---|---|
| [DocumentService.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/service/DocumentService.java) | Applies the lab fault control and then persists a synthetic document through DocumentRepository. Connects to [DocumentReceipt.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/dto/DocumentReceipt.java), [DocumentRequest.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/dto/DocumentRequest.java). |

### `mock-services/document-service/src/main/resources/`

| File | What it does and where it connects |
|---|---|
| [application.yml](mock-services/document-service/src/main/resources/application.yml) | Configures this synthetic service’s port, health and metrics exposure, logging and downstream or database connection. Connects to [docker-compose.yml](docker-compose.yml). |

### `mock-services/document-service/src/main/resources/db/migration/`

| File | What it does and where it connects |
|---|---|
| [V1__create_synthetic_documents.sql](mock-services/document-service/src/main/resources/db/migration/V1__create_synthetic_documents.sql) | Creates the synthetic document table used by DocumentRepository in the monitored database. Connects to [DocumentRepository.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/persistence/DocumentRepository.java). |

### `mock-services/document-service/src/test/java/com/madlanga/lab/document/`

| File | What it does and where it connects |
|---|---|
| [DocumentApplicationTests.java](mock-services/document-service/src/test/java/com/madlanga/lab/document/DocumentApplicationTests.java) | Checks document validation, health exposure, migration startup and real document persistence in the test database. Connects to [DocumentFailureTests.java](mock-services/document-service/src/test/java/com/madlanga/lab/document/DocumentFailureTests.java). |
| [DocumentFailureTests.java](mock-services/document-service/src/test/java/com/madlanga/lab/document/DocumentFailureTests.java) | Checks that a simulated database connectivity failure returns a sanitized HTTP 503. Connects to [DocumentRepository.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/persistence/DocumentRepository.java), [DocumentApplicationTests.java](mock-services/document-service/src/test/java/com/madlanga/lab/document/DocumentApplicationTests.java). |
| [FaultInjectionServiceTest.java](mock-services/document-service/src/test/java/com/madlanga/lab/document/FaultInjectionServiceTest.java) | Checks each retained degradation mode, bounds, failure cadence and reset to healthy lab behavior. Connects to [FaultConfig.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/fault/FaultConfig.java), [FaultInjectionService.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/fault/FaultInjectionService.java). |

### `mock-services/document-service/src/test/resources/`

| File | What it does and where it connects |
|---|---|
| [application-test.yml](mock-services/document-service/src/test/resources/application-test.yml) | Supplies test-only Spring settings so this module’s tests can run without its normal production database and scheduling configuration. Connects to [pom.xml](mock-services/document-service/pom.xml). |

### `mock-services/document-service/src/test/resources/mockito-extensions/`

| File | What it does and where it connects |
|---|---|
| [org.mockito.plugins.MockMaker](mock-services/document-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker) | Selects the Mockito test mock implementation used by this service’s test suite. Connects to [pom.xml](mock-services/document-service/pom.xml). |

### `mock-services/payment-service/`

| File | What it does and where it connects |
|---|---|
| [.dockerignore](mock-services/payment-service/.dockerignore) | Excludes generated output and local-only files from this module’s Docker build context. Connects to [Dockerfile](mock-services/payment-service/Dockerfile). |
| [Dockerfile](mock-services/payment-service/Dockerfile) | Builds and verifies this Java module with Maven, then packages its runtime image for Compose. Connects to [docker-compose.yml](docker-compose.yml). |
| [pom.xml](mock-services/payment-service/pom.xml) | Defines this Java module’s dependencies, Java version and Maven build/test configuration. Connects to [Dockerfile](mock-services/payment-service/Dockerfile). |

### `mock-services/payment-service/src/main/java/com/madlanga/lab/payment/`

| File | What it does and where it connects |
|---|---|
| [PaymentApplication.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/PaymentApplication.java) | Starts the independently deployable synthetic payment application. Spring discovers this module’s components using [application.yml](mock-services/payment-service/src/main/resources/application.yml). |

### `mock-services/payment-service/src/main/java/com/madlanga/lab/payment/client/`

| File | What it does and where it connects |
|---|---|
| [CustomerClient.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/client/CustomerClient.java) | Calls customer-service from payment-service and translates downstream errors into DownstreamException. Connects to [CustomerValidation.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/CustomerValidation.java), [DownstreamRequest.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/DownstreamRequest.java). |

### `mock-services/payment-service/src/main/java/com/madlanga/lab/payment/config/`

| File | What it does and where it connects |
|---|---|
| [CorrelationIdFilter.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/config/CorrelationIdFilter.java) | Validates or generates a business correlation ID and makes it available to request logs and downstream calls. It has no runtime service call. |
| [HttpClientConfig.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/config/HttpClientConfig.java) | Configures the service’s outbound HTTP client with timeouts and observation support for downstream requests. It has no runtime service call. |

### `mock-services/payment-service/src/main/java/com/madlanga/lab/payment/controller/`

| File | What it does and where it connects |
|---|---|
| [PaymentController.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/controller/PaymentController.java) | Accepts synthetic payment HTTP requests and delegates to PaymentService. Connects to [PaymentService.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/service/PaymentService.java), [PaymentRequest.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/PaymentRequest.java). |

### `mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/`

| File | What it does and where it connects |
|---|---|
| [ApiError.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/ApiError.java) | Defines the sanitized code, message and correlation information returned for a synthetic service failure. Connects to [ApiExceptionHandler.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/exception/ApiExceptionHandler.java). |
| [CustomerValidation.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/CustomerValidation.java) | Carries the customer validation outcome and related document result between customer and payment services. Connects to [DocumentReceipt.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/DocumentReceipt.java). |
| [DocumentReceipt.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/DocumentReceipt.java) | Represents the document identifier and receipt returned along the synthetic service chain. Connects to [CustomerValidation.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/CustomerValidation.java), [PaymentResult.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/PaymentResult.java). |
| [DownstreamRequest.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/DownstreamRequest.java) | Carries the fields sent to the next service in the synthetic request chain. Connects to [CustomerClient.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/client/CustomerClient.java), [PaymentService.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/service/PaymentService.java). |
| [PaymentRequest.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/PaymentRequest.java) | Validates the incoming synthetic payment fields before PaymentController invokes business handling. Connects to [PaymentController.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/controller/PaymentController.java), [PaymentService.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/service/PaymentService.java). |
| [PaymentResult.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/PaymentResult.java) | Carries the payment outcome and downstream validation information back to the caller. Connects to [DocumentReceipt.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/DocumentReceipt.java). |

### `mock-services/payment-service/src/main/java/com/madlanga/lab/payment/exception/`

| File | What it does and where it connects |
|---|---|
| [ApiExceptionHandler.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/exception/ApiExceptionHandler.java) | Translates validation, downstream or persistence failures into sanitized HTTP errors appropriate to its synthetic service. Connects to [ApiError.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/ApiError.java), [DownstreamException.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/exception/DownstreamException.java). |
| [DownstreamException.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/exception/DownstreamException.java) | Carries a downstream-call failure to the service’s HTTP exception handler without exposing the raw remote response. Connects to [CustomerClient.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/client/CustomerClient.java), [ApiExceptionHandler.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/exception/ApiExceptionHandler.java). |

### `mock-services/payment-service/src/main/java/com/madlanga/lab/payment/service/`

| File | What it does and where it connects |
|---|---|
| [PaymentService.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/service/PaymentService.java) | Processes a synthetic payment by requesting customer validation through CustomerClient. Connects to [CustomerClient.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/client/CustomerClient.java), [DownstreamRequest.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/DownstreamRequest.java). |

### `mock-services/payment-service/src/main/resources/`

| File | What it does and where it connects |
|---|---|
| [application.yml](mock-services/payment-service/src/main/resources/application.yml) | Configures this synthetic service’s port, health and metrics exposure, logging and downstream or database connection. Connects to [docker-compose.yml](docker-compose.yml). |

### `mock-services/payment-service/src/test/java/com/madlanga/lab/payment/`

| File | What it does and where it connects |
|---|---|
| [PaymentApplicationTests.java](mock-services/payment-service/src/test/java/com/madlanga/lab/payment/PaymentApplicationTests.java) | Checks payment endpoints, validation, correlation propagation and controlled downstream failures. It has no runtime service call. |

### `mock-services/payment-service/src/test/resources/mockito-extensions/`

| File | What it does and where it connects |
|---|---|
| [org.mockito.plugins.MockMaker](mock-services/payment-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker) | Selects the Mockito test mock implementation used by this service’s test suite. Connects to [pom.xml](mock-services/payment-service/pom.xml). |

### `scripts/`

These runners validate outage detection and degradation telemetry against the running lab without weakening evidence requirements.

| File | What it does and where it connects |
|---|---|
| [run-phase11-e2e.py](scripts/run-phase11-e2e.py) | Runs six hard-failure cases against the live lab, stopping and restoring services while checking detection, propagation and guarded recovery. Connects to [docker-compose.yml](docker-compose.yml). |
| [run-degradation-e2e.py](scripts/run-degradation-e2e.py) | Runs four degradation cases using lab fault controls while asserting real LOG/METRIC/TRACE evidence, no new degradation-only outage and successful requests after reset. Connects to [FaultInjectionController.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/controller/FaultInjectionController.java). |

### `traffic-generator/`

This small Python process continuously exercises the monitored request chain.

| File | What it does and where it connects |
|---|---|
| [.dockerignore](traffic-generator/.dockerignore) | Excludes generated output and local-only files from this module’s Docker build context. Connects to [Dockerfile](traffic-generator/Dockerfile). |
| [Dockerfile](traffic-generator/Dockerfile) | Packages the synthetic Python traffic process for execution by Compose. Connects to [docker-compose.yml](docker-compose.yml). |
| [test_traffic.py](traffic-generator/test_traffic.py) | Checks traffic settings, error handling, synthetic requests and controlled shutdown behavior. Connects to [traffic.py](traffic-generator/traffic.py). |
| [traffic.py](traffic-generator/traffic.py) | Sends synthetic payments at a configured cadence so the full service chain produces real runtime telemetry. Connects to [docker-compose.yml](docker-compose.yml). |

## Outage-evidence lifecycle additions

- `incident/model/EvidenceSignal.java` includes explicit symptom, potential, directly unavailable and directly available kinds plus provenance/collection time. Only typed direct unavailable health evidence is an outage trigger.
- `telemetry/model/EvidenceIdentity.java` stabilizes log, metric and trace observation identities for window overlap deduplication.
- `lifecycle/service/IncidentEvidenceAccumulator.java` appends evidence without deleting older history, tracks evidence versions and explicit collection limits, and separately retains the latest origin recovery proof after caps.
- `lifecycle/service/IncidentLifecycleService.java` locks the incident scope for creation/update/recovery and rejects analysis windows older than the accepted evaluation. The isolated CI suite exercises cross-connection PostgreSQL advisory-lock serialization; live container recovery remains unvalidated.
- `lifecycle/scheduler/IncidentLifecycleScheduler.java` requires typed origin availability, guarded positive recovery and avoids treating residual HTTP errors as ongoing outages.
- `diagnosis/service/DiagnosisArchive.java` archives explicitly requested AI outputs and the exact input snapshot/version. `db/migration/V3__diagnosis_evidence_snapshots.sql` creates that persistent archive without modifying existing incident rows.
- `topology/config/ConfiguredTopology.java`, `application-demo.yml` and `infrastructure/monitoring/application-ledger.yml` separate demo-specific endpoints/dependencies from generic topology analysis.
- `frontend/src/incident-visuals.js` uses backend incident classification and typed direct-health evidence; the incident list and selected detail poll silently while preserving explicit selection.
- `.github/workflows/validate-outage.yml` runs Maven verification and frontend tests/build on the feature branch. It does not replace live PostgreSQL migration or outage validation.

The engine intentionally does not automatically delete old incident or diagnosis records; collection limits are **not** a complete global retention policy. Continuous collection cannot retroactively reconstruct telemetry lost during collector downtime. Runtime integration, migration safety and concurrency must be validated in an isolated approved environment before deployment.

### PostgreSQL CI integration

`blast-radius-api/src/test/java/com/madlanga/blastradius/incident/repository/JdbcIncidentRepositoryPostgresTest.java` runs only when `CI_PG_URL` is supplied. It applies Flyway V1–V3 against an isolated CI PostgreSQL 17 service and checks incident JSONB round-tripping, diagnosis evidence archive tables, the unique ACTIVE-origin index, recovery followed by a new incident, and advisory-lock serialization across separate JDBC transactions. The [validated CI run](https://github.com/SLMakomazi/blast-radius-engine/actions/runs/37756041645) passed 223 backend tests and 14 frontend tests; live Docker fault injection remains unexecuted for this branch.

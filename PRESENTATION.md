# 4. Presentation — what to open and what to say

Use the first eight stops for a short manager demonstration. The file-by-file notes below are a reference for questions, not a requirement to read two hundred files aloud; each file has one short speaking note. Start the lab using [SETUP.md](SETUP.md) before the meeting.

## The short presentation

| Order / open | Say this | Show this |
|---|---|---|
| 1. [README](README.md), “How other systems connect” | “We connect a system through its dependency map and its runtime evidence. Both must use the same service identities so we can explain which failure belongs to which component.” | Topology and telemetry boundaries; state that enterprise onboarding and notification delivery still need integration. |
| 2. [Compose](docker-compose.yml) | “This lab has a payment-to-customer-to-document-to-database chain. The engine has a separate database so monitoring can continue when the application database fails.” | The two database services and synthetic application chain. |
| 3. [TopologyService](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/service/TopologyService.java) | “We learn dependencies from request journeys and remember them across restarts. Remembering a connection tells us what could be affected; it does not prove a current outage.” | Retained observations and expiry. |
| 4. [IncidentAnalysisService](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/service/IncidentAnalysisService.java) | “The engine compares that map with current evidence to identify a likely origin and separate confirmed impact from risk. The confidence and severity come from repeatable backend rules.” | Origin assessment, component impact and coverage. |
| 5. [Dashboard](http://localhost:5173) / [ServiceMap](frontend/src/ServiceMap.jsx) | “Red means unavailable; amber means failure signals; pulsing green is possible impact, not proven health. Select a service and we explain what was observed and which dependency connects it to the problem.” | A saved incident, branching arrows, service search and the explanation panel. |
| 6. [Dashboard](http://localhost:5173) evidence section | “The chart shows when supporting observations were recorded. These are not request-volume statistics; each explanation still links back to a technical observation that an engineer can inspect.” | Select a chart legend, expand one observation, then copy a handoff summary without sending it. |
| 7. [IncidentLifecycleScheduler](blast-radius-api/src/main/java/com/madlanga/blastradius/lifecycle/scheduler/IncidentLifecycleScheduler.java) | “The incident stays open until consecutive covered windows support recovery. Missing telemetry does not turn a service green or close an incident.” | Compare ACTIVE and RESOLVED history; resolved cards are historical, not a live uptime promise. |
| 8. [DiagnosisService](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/service/DiagnosisService.java) and [SETUP](SETUP.md) | “AI can explain sanitized findings, but it does not decide the blast radius. Our acceptance target is six hard-failure checks plus four degraded-but-running checks, using real telemetry.” | Optional diagnosis and the two-stage commands; distinguish recorded results from a fresh run. |

Close with: “This makes impact and evidence understandable to the response team. Connecting our real systems, service ownership and approved notification channels is the next integration step.”

## File-by-file speaking notes

Open the linked file when someone asks about it. Folder headings match the repository; [ARCHITECTURE.md](ARCHITECTURE.md) adds its collaborator links.


### `Repository root/`

| Open | Say |
|---|---|
| [.env.example](.env.example) | “This file supplies synthetic local settings and optional AI configuration that Compose reads from a private .env copy.” |
| [.gitignore](.gitignore) | “This file keeps credentials, generated Java and frontend output, local topology and editor files out of commits.” |
| [ARCHITECTURE.md](ARCHITECTURE.md) | “This file maps every maintained repository file to its responsibility and concrete connections.” |
| [LICENSE](LICENSE) | “This file defines the repository’s licensing terms for people using or distributing its source.” |
| [PRESENTATION.md](PRESENTATION.md) | “This file provides a short manager-facing story followed by one speaking note for every repository file.” |
| [README.md](README.md) | “This file introduces the system, integration boundaries, topology, dashboard and the two-stage validation model.” |
| [SETUP.md](SETUP.md) | “This file guides the next operator through configuration, startup, readiness, testing and the visual demonstration.” |

### `blast-radius-api/`

| Open | Say |
|---|---|
| [.dockerignore](blast-radius-api/.dockerignore) | “This file excludes generated output and local-only files from this module’s Docker build context.” |
| [Dockerfile](blast-radius-api/Dockerfile) | “This file builds and verifies this Java module with Maven, then packages its runtime image for Compose.” |
| [pom.xml](blast-radius-api/pom.xml) | “This file defines this Java module’s dependencies, Java version and Maven build/test configuration.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/`

| Open | Say |
|---|---|
| [BlastRadiusApplication.java](blast-radius-api/src/main/java/com/madlanga/blastradius/BlastRadiusApplication.java) | “This file starts Spring Boot and discovers the engine’s feature components.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/config/`

| Open | Say |
|---|---|
| [DiagnosisConfig.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/config/DiagnosisConfig.java) | “This file wires the request mapper, optional Gemini provider and deterministic fallback into DiagnosisService.” |
| [GeminiProperties.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/config/GeminiProperties.java) | “This file binds the advisory model, fallback models, API endpoint, key and timeout settings.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/dto/`

| Open | Say |
|---|---|
| [DiagnosisRequest.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/dto/DiagnosisRequest.java) | “This file carries the sanitized origin, impact, severity, evidence and coverage supplied to diagnosis providers.” |
| [DiagnosisResponse.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/dto/DiagnosisResponse.java) | “This file carries the advisory summary, probable cause, recommended actions, limitations and provider information returned to the dashboard.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/mapper/`

| Open | Say |
|---|---|
| [DiagnosisRequestMapper.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/mapper/DiagnosisRequestMapper.java) | “This file builds diagnosis requests from a current analysis or persisted JSON snapshot, sanitizing free text again before provider use.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/provider/`

| Open | Say |
|---|---|
| [DeterministicDiagnosisProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/provider/DeterministicDiagnosisProvider.java) | “This file produces an evidence-based explanation without an external AI call and serves as the final fallback.” |
| [DiagnosisProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/provider/DiagnosisProvider.java) | “This file defines the provider-neutral operation for explaining a diagnosis request.” |
| [GeminiDiagnosisProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/provider/GeminiDiagnosisProvider.java) | “This file sends sanitized diagnosis context to Gemini and handles response parsing, bounded retries and configured model fallback.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/service/`

| Open | Say |
|---|---|
| [DiagnosisService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/service/DiagnosisService.java) | “This file coordinates advisory diagnosis and returns the deterministic fallback when the primary provider fails.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/incident/controller/`

| Open | Say |
|---|---|
| [IncidentController.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/controller/IncidentController.java) | “This file exposes topology and analysis HTTP endpoints, validates request windows and delegates persisted analysis to the lifecycle service.” |
| [IncidentHistoryController.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/controller/IncidentHistoryController.java) | “This file exposes incident history, detail, explicit resolution and advisory diagnosis to dashboard clients.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/incident/dto/`

| Open | Say |
|---|---|
| [AnalyzeIncidentRequest.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/dto/AnalyzeIncidentRequest.java) | “This file carries the application, environment, analysis window and optional origin hint from the HTTP caller.” |
| [ErrorResponse.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/dto/ErrorResponse.java) | “This file defines the error code and safe message returned by the incident HTTP boundary.” |
| [IncidentResponse.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/dto/IncidentResponse.java) | “This file defines the persisted incident fields and analysis snapshot returned to the frontend.” |
| [ResolveIncidentRequest.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/dto/ResolveIncidentRequest.java) | “This file carries an optional resolution timestamp for the incident resolution endpoint.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/`

| Open | Say |
|---|---|
| [ComponentImpact.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/ComponentImpact.java) | “This file describes one component’s origin, observed, theoretical, unknown or unexpected impact, with its path and evidence.” |
| [EvidenceSignal.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/EvidenceSignal.java) | “This file records a timestamped observation linking a component, evidence family, readable signal and source identifier.” |
| [IncidentAnalysis.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/IncidentAnalysis.java) | “This file collects the deterministic analysis window, origin, impacts, timeline, severity, coverage and warnings.” |
| [IncidentSeverity.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/IncidentSeverity.java) | “This file carries the calculated severity level, numeric score and reasons.” |
| [OriginAssessment.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/OriginAssessment.java) | “This file carries the selected likely origin, confidence, evidence score and supporting observations.” |
| [PersistedIncident.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/model/PersistedIncident.java) | “This file represents stored incident identity, lifecycle timestamps, origin, severity and the serialized analysis snapshot.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/incident/repository/`

| Open | Say |
|---|---|
| [IncidentRepository.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/repository/IncidentRepository.java) | “This file defines incident lookup, history queries and persistence independently of SQL details.” |
| [JdbcIncidentRepository.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/repository/JdbcIncidentRepository.java) | “This file implements incident queries and PostgreSQL persistence, including the stored JSON analysis snapshot.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/incident/service/`

| Open | Say |
|---|---|
| [IncidentAnalysisService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/service/IncidentAnalysisService.java) | “This file correlates telemetry with topology, assesses a likely origin and combines graph impact with observed evidence and severity.” |
| [IncidentSeverityService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/incident/service/IncidentSeverityService.java) | “This file calculates deterministic severity from origin evidence and actual propagation rather than treating every possible impact as confirmed.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/lifecycle/scheduler/`

| Open | Say |
|---|---|
| [IncidentLifecycleScheduler.java](blast-radius-api/src/main/java/com/madlanga/blastradius/lifecycle/scheduler/IncidentLifecycleScheduler.java) | “This file periodically detects failures and requires consecutive fully covered healthy windows before automatically resolving an incident.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/lifecycle/service/`

| Open | Say |
|---|---|
| [IncidentLifecycleService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/lifecycle/service/IncidentLifecycleService.java) | “This file creates or updates the active incident for an origin, preserves incident history and handles resolution through the repository.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/shared/config/`

| Open | Say |
|---|---|
| [OpenApiConfig.java](blast-radius-api/src/main/java/com/madlanga/blastradius/shared/config/OpenApiConfig.java) | “This file configures the engine’s generated OpenAPI metadata for HTTP API consumers.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/shared/sanitization/`

| Open | Say |
|---|---|
| [RedactionRule.java](blast-radius-api/src/main/java/com/madlanga/blastradius/shared/sanitization/RedactionRule.java) | “This file defines a replaceable policy for identifying and redacting sensitive values.” |
| [TelemetrySanitizer.java](blast-radius-api/src/main/java/com/madlanga/blastradius/shared/sanitization/TelemetrySanitizer.java) | “This file applies built-in and pluggable redaction rules to attributes and free text before normalized evidence is stored, displayed or sent to AI.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/config/`

| Open | Say |
|---|---|
| [TelemetryConfig.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/config/TelemetryConfig.java) | “This file wires telemetry HTTP access and binds Loki, Prometheus, Tempo and Actuator settings.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/`

| Open | Say |
|---|---|
| [CoverageStatus.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/CoverageStatus.java) | “This file defines whether an evidence family is available, partial, unavailable or unsupported; it is not a service health score.” |
| [EvidenceFamily.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/EvidenceFamily.java) | “This file names the four evidence families: logs, metrics, traces and health.” |
| [EvidenceProvenance.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/EvidenceProvenance.java) | “This file preserves the provider, collection time, family and bounded source reference for an observation.” |
| [HealthEvidence.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/HealthEvidence.java) | “This file represents a component health observation with its state, timestamp, endpoint details and provenance.” |
| [HealthState.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/HealthState.java) | “This file defines provider-neutral health states used in normalized health observations.” |
| [LogEvidence.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/LogEvidence.java) | “This file represents a sanitized log event, preserving component identity, time, severity and trace or correlation identifiers.” |
| [MetricEvidence.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/MetricEvidence.java) | “This file represents a timestamped numerical measurement with its name, unit, component and sanitized dimensions.” |
| [SpanEvidence.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/SpanEvidence.java) | “This file represents one request or dependency-call span with timing, error status, parent, peer and provenance.” |
| [SpanKind.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/SpanKind.java) | “This file distinguishes request roles so a server error is not automatically mistaken for an outbound dependency call.” |
| [SpanStatus.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/SpanStatus.java) | “This file defines normalized span success, failure and unset status independently of a tracing vendor.” |
| [TelemetryBundle.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/TelemetryBundle.java) | “This file groups normalized evidence, family coverage and warnings for one analysis request.” |
| [TelemetryCoverage.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/TelemetryCoverage.java) | “This file records availability separately for each evidence family and exposes whether the window is fully covered.” |
| [TelemetryQuery.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/model/TelemetryQuery.java) | “This file carries application scope, environment, time window and optional component, correlation or trace filters.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/`

| Open | Say |
|---|---|
| [LocalTelemetryProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/LocalTelemetryProvider.java) | “This file combines four telemetry adapters while isolating provider failures so one missing source does not suppress the others.” |
| [TelemetryProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/TelemetryProvider.java) | “This file defines how incident analysis obtains sanitized, normalized evidence and coverage from an external telemetry source.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/health/`

| Open | Say |
|---|---|
| [ActuatorHealthAdapter.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/health/ActuatorHealthAdapter.java) | “This file probes configured service health endpoints and maps responses or unreachable probes into health evidence and coverage.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/loki/`

| Open | Say |
|---|---|
| [LokiLogAdapter.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/loki/LokiLogAdapter.java) | “This file queries Loki log streams, parses their provider-specific shape and produces sanitized LogEvidence.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/prometheus/`

| Open | Say |
|---|---|
| [PrometheusMetricsAdapter.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/prometheus/PrometheusMetricsAdapter.java) | “This file queries configured Prometheus measurement series and converts samples into MetricEvidence with preserved timestamps.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/tempo/`

| Open | Say |
|---|---|
| [TempoTraceAdapter.java](blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/tempo/TempoTraceAdapter.java) | “This file searches Tempo and retrieves traces, mapping OTLP spans and peer identities into normalized SpanEvidence.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/topology/config/`

| Open | Say |
|---|---|
| [TopologyConfig.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/config/TopologyConfig.java) | “This file connects Tempo span acquisition to topology learning, file persistence and scheduled refresh for the configured application.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/`

| Open | Say |
|---|---|
| [BlastRadiusResult.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/BlastRadiusResult.java) | “This file holds the graph origin and deterministically ordered theoretical impact results.” |
| [ComponentNode.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/ComponentNode.java) | “This file defines a canonical component identity, type, technology and metadata in the dependency graph.” |
| [ComponentType.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/ComponentType.java) | “This file distinguishes kinds of topology components such as services and databases.” |
| [DependencyEdge.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/DependencyEdge.java) | “This file stores a directed relationship: the dependent needs the dependency.” |
| [DependencyTopology.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/DependencyTopology.java) | “This file groups the canonical nodes and directed dependencies for an application and environment.” |
| [RetainedTopology.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/RetainedTopology.java) | “This file stores learned graph relationships and observation timestamps across requests and restarts without turning old knowledge into current incident evidence.” |
| [TheoreticalImpact.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/model/TheoreticalImpact.java) | “This file describes a reachable dependent, its minimum distance and path from a possible failure origin.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/topology/provider/`

| Open | Say |
|---|---|
| [DependencyTopologyProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/provider/DependencyTopologyProvider.java) | “This file defines the topology contract through which incident analysis can use local discovery or a future enterprise architecture source.” |
| [RuntimeSpanProvider.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/provider/RuntimeSpanProvider.java) | “This file supplies normalized spans for topology discovery separately from the incident’s full telemetry bundle.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/topology/repository/`

| Open | Say |
|---|---|
| [FileTopologyRepository.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/repository/FileTopologyRepository.java) | “This file loads and atomically replaces retained topology files, scoped by application and environment.” |
| [TopologyRepository.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/repository/TopologyRepository.java) | “This file defines loading and saving retained topology without coupling learning to a file format.” |

### `blast-radius-api/src/main/java/com/madlanga/blastradius/topology/service/`

| Open | Say |
|---|---|
| [BlastRadiusGraphService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/service/BlastRadiusGraphService.java) | “This file traverses dependencies in reverse to calculate affected dependents, stable shortest paths and distances while handling branches and cycles.” |
| [TopologyService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/service/TopologyService.java) | “This file merges new span observations with retained knowledge, rejects invalid scope or timestamps and expires stale nodes and relationships.” |
| [TraceTopologyService.java](blast-radius-api/src/main/java/com/madlanga/blastradius/topology/service/TraceTopologyService.java) | “This file discovers component relationships from parent-child spans and explicit peers without inventing dependencies from technology names alone.” |

### `blast-radius-api/src/main/resources/`

| Open | Say |
|---|---|
| [application.yml](blast-radius-api/src/main/resources/application.yml) | “This file configures diagnostic persistence, telemetry endpoints, topology retention, lifecycle windows and optional diagnosis settings.” |

### `blast-radius-api/src/main/resources/db/migration/`

| Open | Say |
|---|---|
| [V1__create_incidents.sql](blast-radius-api/src/main/resources/db/migration/V1__create_incidents.sql) | “This file creates the diagnostic incidents table used by JdbcIncidentRepository.” |
| [V2__enforce_single_active_incident.sql](blast-radius-api/src/main/resources/db/migration/V2__enforce_single_active_incident.sql) | “This file adds the database constraint that prevents duplicate active incidents for the same application, environment and origin.” |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/`

| Open | Say |
|---|---|
| [ArchitectureBoundaryTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/ArchitectureBoundaryTest.java) | “This file checks that Java package placement and feature dependency boundaries match the repository’s architecture rules.” |
| [BlastRadiusApplicationTests.java](blast-radius-api/src/test/java/com/madlanga/blastradius/BlastRadiusApplicationTests.java) | “This file checks that the Spring application context starts with the test configuration.” |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/diagnosis/mapper/`

| Open | Say |
|---|---|
| [DiagnosisRequestMapperStoredJsonTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/diagnosis/mapper/DiagnosisRequestMapperStoredJsonTest.java) | “This file checks that stored analysis JSON maps directly to advisory context and sensitive free text is redacted again.” |
| [DiagnosisRequestMapperTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/diagnosis/mapper/DiagnosisRequestMapperTest.java) | “This file checks that advisory context is derived only from deterministic analysis.” |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/diagnosis/provider/`

| Open | Say |
|---|---|
| [GeminiDiagnosisProviderTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/diagnosis/provider/GeminiDiagnosisProviderTest.java) | “This file exercises response parsing, transient failures, retries, fallback models and safe provider errors against a controlled HTTP server.” |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/diagnosis/service/`

| Open | Say |
|---|---|
| [DiagnosisServiceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/diagnosis/service/DiagnosisServiceTest.java) | “This file checks that provider failures fall back to deterministic diagnosis without breaking incident analysis.” |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/incident/controller/`

| Open | Say |
|---|---|
| [IncidentControllerTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/incident/controller/IncidentControllerTest.java) | “This file checks analysis request defaults, invalid time windows, origin validation and HTTP error responses.” |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/incident/service/`

| Open | Say |
|---|---|
| [IncidentAnalysisServiceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/incident/service/IncidentAnalysisServiceTest.java) | “This file checks likely-origin selection, observed versus possible impact, partial coverage, unexpected evidence and counter-based degradation.” |
| [IncidentSeverityServiceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/incident/service/IncidentSeverityServiceTest.java) | “This file checks severity thresholds and ensures theoretical propagation alone does not inflate severity.” |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/lifecycle/service/`

| Open | Say |
|---|---|
| [IncidentLifecycleServiceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/lifecycle/service/IncidentLifecycleServiceTest.java) | “This file checks active-incident persistence, explicit resolution and retention of peak severity and history.” |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/shared/sanitization/`

| Open | Say |
|---|---|
| [TelemetrySanitizerTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/shared/sanitization/TelemetrySanitizerTest.java) | “This file checks secret and sensitive-value redaction in structured attributes and free text.” |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/model/`

| Open | Say |
|---|---|
| [EvidenceProvenanceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/model/EvidenceProvenanceTest.java) | “This file checks provenance validation, stable fields and equality.” |
| [HealthEvidenceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/model/HealthEvidenceTest.java) | “This file checks health states, timestamps, details and the distinction between observations and missing data.” |
| [LogEvidenceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/model/LogEvidenceTest.java) | “This file checks log identity, timestamp, correlation fields and immutable sanitized attributes.” |
| [MetricEvidenceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/model/MetricEvidenceTest.java) | “This file checks measurement timestamps, dimensions, units and immutable evidence fields.” |
| [SpanEvidenceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/model/SpanEvidenceTest.java) | “This file checks trace relationships, duration, error representation and dependency-call information.” |
| [TelemetryBundleTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/model/TelemetryBundleTest.java) | “This file checks evidence aggregation, warnings and coverage without interpreting absent evidence as healthy.” |
| [TelemetryQueryTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/model/TelemetryQueryTest.java) | “This file checks scope requirements, optional filters and valid time-window boundaries.” |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/`

| Open | Say |
|---|---|
| [LocalTelemetryProviderLiveIT.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/LocalTelemetryProviderLiveIT.java) | “This file queries a running local lab to verify normalized evidence from actual telemetry providers; this is an opt-in live test.” |
| [LocalTelemetryProviderTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/LocalTelemetryProviderTest.java) | “This file checks aggregation and isolation when one or more telemetry providers fail.” |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/health/`

| Open | Say |
|---|---|
| [ActuatorHealthAdapterTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/health/ActuatorHealthAdapterTest.java) | “This file checks health normalization, unreachable probes, component filtering and provenance.” |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/loki/`

| Open | Say |
|---|---|
| [LokiLogAdapterTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/loki/LokiLogAdapterTest.java) | “This file checks real-shaped log parsing, correlation fields, redaction and unavailable-provider handling.” |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/prometheus/`

| Open | Say |
|---|---|
| [PrometheusMetricsAdapterTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/prometheus/PrometheusMetricsAdapterTest.java) | “This file checks measurement series mapping, original sample timestamps and label sanitization.” |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/tempo/`

| Open | Say |
|---|---|
| [CapturedTempoRegressionTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/tempo/CapturedTempoRegressionTest.java) | “This file uses captured trace fixtures to check retained topology, peerless failures and exclusion of stale evidence.” |
| [TempoTraceAdapterTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/telemetry/provider/tempo/TempoTraceAdapterTest.java) | “This file checks OTLP parsing, trace ID normalization, span error status and incomplete retrieval handling.” |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/topology/model/`

| Open | Say |
|---|---|
| [TopologyModelTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/topology/model/TopologyModelTest.java) | “This file checks graph collection immutability, self-dependency rejection and impact path consistency.” |

### `blast-radius-api/src/test/java/com/madlanga/blastradius/topology/service/`

| Open | Say |
|---|---|
| [BlastRadiusGraphServiceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/topology/service/BlastRadiusGraphServiceTest.java) | “This file checks chain, branching, cycle, duplicate and disconnected graph behavior with stable shortest paths.” |
| [TopologyServiceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/topology/service/TopologyServiceTest.java) | “This file checks durable learning, restart behavior, expiry, scope separation and corrupt-store handling.” |
| [TraceTopologyServiceLiveIT.java](blast-radius-api/src/test/java/com/madlanga/blastradius/topology/service/TraceTopologyServiceLiveIT.java) | “This file checks discovery of the canonical dependency chain from real Tempo evidence in the running lab.” |
| [TraceTopologyServiceTest.java](blast-radius-api/src/test/java/com/madlanga/blastradius/topology/service/TraceTopologyServiceTest.java) | “This file checks parent-child and database-peer discovery and dependency deduplication.” |

### `blast-radius-api/src/test/resources/`

| Open | Say |
|---|---|
| [application.yml](blast-radius-api/src/test/resources/application.yml) | “This file supplies test-only Spring settings so this module’s tests can run without its normal production database and scheduling configuration.” |

### `blast-radius-api/src/test/resources/tempo/`

| Open | Say |
|---|---|
| [failed-connection.json](blast-radius-api/src/test/resources/tempo/failed-connection.json) | “This file provides a captured failed-connection trace for offline Tempo and topology regression tests; it is not live incident evidence.” |
| [healthy-database.json](blast-radius-api/src/test/resources/tempo/healthy-database.json) | “This file provides a captured healthy database trace for offline discovery and retention regression tests; it is not proof of current health.” |

### `Repository root/`

| Open | Say |
|---|---|
| [docker-compose.yml](docker-compose.yml) | “This file wires the 12 local services, dependency startup order, localhost ports, credentials, resource limits and persistent volumes.” |

### `frontend/`

| Open | Say |
|---|---|
| [.dockerignore](frontend/.dockerignore) | “This file excludes generated output and local-only files from this module’s Docker build context.” |
| [.env.example](frontend/.env.example) | “This file documents the frontend API base URL; an empty value uses the Nginx same-origin proxy in Compose.” |
| [Dockerfile](frontend/Dockerfile) | “This file builds the Vite bundle and serves it with an unprivileged Nginx image and the API proxy configuration.” |
| [index.html](frontend/index.html) | “This file provides the root HTML element and loads the React entry point.” |
| [nginx.conf](frontend/nginx.conf) | “This file serves the built frontend and proxies /api requests to blast-radius-api on the internal Compose network.” |
| [package-lock.json](frontend/package-lock.json) | “This file locks frontend dependency versions for repeatable npm ci installs and container builds.” |
| [package.json](frontend/package.json) | “This file defines the React, Vite and icon dependencies and frontend build commands.” |

### `frontend/src/`

| Open | Say |
|---|---|
| [ServiceMap.jsx](frontend/src/ServiceMap.jsx) | “This file renders recorded dependency branches, selectable explanations, impact and evidence charts, and the local copy-handoff action.” |
| [incident-visuals.js](frontend/src/incident-visuals.js) | “This file converts backend snapshots into cautious status labels, plain-language explanations, real path edges and timestamped chart buckets.” |
| [incident-visuals.test.js](frontend/src/incident-visuals.test.js) | “This file checks that visualization logic preserves uncertainty, branching, historical status, evidence counts and searchable symptoms.” |
| [main.jsx](frontend/src/main.jsx) | “This file loads and polls incident REST data, preserves selection and diagnosis state, and composes the dashboard and evidence filters.” |
| [styles.css](frontend/src/styles.css) | “This file styles the responsive dashboard, evidence panels, status colors and motion-aware risk indicator.” |

### `infrastructure/observability/logging/`

| Open | Say |
|---|---|
| [loki.yml](infrastructure/observability/logging/loki.yml) | “This file configures the local Loki log receiver, storage and retention used by the Collector and LokiLogAdapter.” |

### `infrastructure/observability/otel/`

| Open | Say |
|---|---|
| [collector.yml](infrastructure/observability/otel/collector.yml) | “This file receives service telemetry, applies privacy processing and exports logs to Loki and traces to Tempo.” |
| [javaagent.properties](infrastructure/observability/otel/javaagent.properties) | “This file configures service instrumentation and OTLP export to the Collector while limiting sensitive capture.” |

### `infrastructure/observability/prometheus/`

| Open | Say |
|---|---|
| [prometheus.yml](infrastructure/observability/prometheus/prometheus.yml) | “This file defines the local scrape targets and interval for service measurements consumed by PrometheusMetricsAdapter.” |

### `infrastructure/observability/tracing/`

| Open | Say |
|---|---|
| [tempo.yml](infrastructure/observability/tracing/tempo.yml) | “This file configures the local trace receiver, storage and query service used by TempoTraceAdapter.” |

### `mock-services/customer-service/`

| Open | Say |
|---|---|
| [.dockerignore](mock-services/customer-service/.dockerignore) | “This file excludes generated output and local-only files from this module’s Docker build context.” |
| [Dockerfile](mock-services/customer-service/Dockerfile) | “This file builds and verifies this Java module with Maven, then packages its runtime image for Compose.” |
| [pom.xml](mock-services/customer-service/pom.xml) | “This file defines this Java module’s dependencies, Java version and Maven build/test configuration.” |

### `mock-services/customer-service/src/main/java/com/madlanga/lab/customer/`

| Open | Say |
|---|---|
| [CustomerApplication.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/CustomerApplication.java) | “This file starts the independently deployable synthetic customer application.” |

### `mock-services/customer-service/src/main/java/com/madlanga/lab/customer/client/`

| Open | Say |
|---|---|
| [DocumentClient.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/client/DocumentClient.java) | “This file calls document-service from customer-service and translates downstream errors into DownstreamException.” |

### `mock-services/customer-service/src/main/java/com/madlanga/lab/customer/config/`

| Open | Say |
|---|---|
| [CorrelationIdFilter.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/config/CorrelationIdFilter.java) | “This file validates or generates a business correlation ID and makes it available to request logs and downstream calls.” |
| [HttpClientConfig.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/config/HttpClientConfig.java) | “This file configures the service’s outbound HTTP client with timeouts and observation support for downstream requests.” |

### `mock-services/customer-service/src/main/java/com/madlanga/lab/customer/controller/`

| Open | Say |
|---|---|
| [CustomerController.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/controller/CustomerController.java) | “This file accepts customer validation HTTP requests and delegates to CustomerService.” |

### `mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/`

| Open | Say |
|---|---|
| [ApiError.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/ApiError.java) | “This file defines the sanitized code, message and correlation information returned for a synthetic service failure.” |
| [CustomerRequest.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/CustomerRequest.java) | “This file validates the incoming customer and document fields before CustomerController delegates the request.” |
| [CustomerValidation.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/CustomerValidation.java) | “This file carries the customer validation outcome and related document result between customer and payment services.” |
| [DocumentReceipt.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/DocumentReceipt.java) | “This file represents the document identifier and receipt returned along the synthetic service chain.” |
| [DownstreamRequest.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/DownstreamRequest.java) | “This file carries the fields sent to the next service in the synthetic request chain.” |

### `mock-services/customer-service/src/main/java/com/madlanga/lab/customer/exception/`

| Open | Say |
|---|---|
| [ApiExceptionHandler.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/exception/ApiExceptionHandler.java) | “This file translates validation, downstream or persistence failures into sanitized HTTP errors appropriate to its synthetic service.” |
| [DownstreamException.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/exception/DownstreamException.java) | “This file carries a downstream-call failure to the service’s HTTP exception handler without exposing the raw remote response.” |

### `mock-services/customer-service/src/main/java/com/madlanga/lab/customer/service/`

| Open | Say |
|---|---|
| [CustomerService.java](mock-services/customer-service/src/main/java/com/madlanga/lab/customer/service/CustomerService.java) | “This file performs synthetic customer validation and obtains a document receipt through DocumentClient.” |

### `mock-services/customer-service/src/main/resources/`

| Open | Say |
|---|---|
| [application.yml](mock-services/customer-service/src/main/resources/application.yml) | “This file configures this synthetic service’s port, health and metrics exposure, logging and downstream or database connection.” |

### `mock-services/customer-service/src/test/java/com/madlanga/lab/customer/`

| Open | Say |
|---|---|
| [CustomerApplicationTests.java](mock-services/customer-service/src/test/java/com/madlanga/lab/customer/CustomerApplicationTests.java) | “This file checks customer endpoints, validation, correlation propagation and controlled downstream failures.” |

### `mock-services/customer-service/src/test/resources/mockito-extensions/`

| Open | Say |
|---|---|
| [org.mockito.plugins.MockMaker](mock-services/customer-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker) | “This file selects the Mockito test mock implementation used by this service’s test suite.” |

### `mock-services/document-service/`

| Open | Say |
|---|---|
| [.dockerignore](mock-services/document-service/.dockerignore) | “This file excludes generated output and local-only files from this module’s Docker build context.” |
| [Dockerfile](mock-services/document-service/Dockerfile) | “This file builds and verifies this Java module with Maven, then packages its runtime image for Compose.” |
| [pom.xml](mock-services/document-service/pom.xml) | “This file defines this Java module’s dependencies, Java version and Maven build/test configuration.” |

### `mock-services/document-service/src/main/java/com/madlanga/lab/document/`

| Open | Say |
|---|---|
| [DocumentApplication.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/DocumentApplication.java) | “This file starts the independently deployable synthetic document application.” |

### `mock-services/document-service/src/main/java/com/madlanga/lab/document/config/`

| Open | Say |
|---|---|
| [CorrelationIdFilter.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/config/CorrelationIdFilter.java) | “This file validates or generates a business correlation ID and makes it available to request logs and downstream calls.” |

### `mock-services/document-service/src/main/java/com/madlanga/lab/document/controller/`

| Open | Say |
|---|---|
| [DocumentController.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/controller/DocumentController.java) | “This file accepts document HTTP requests and delegates to DocumentService.” |
| [FaultInjectionController.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/controller/FaultInjectionController.java) | “This file exposes the local lab endpoint for inspecting, setting and resetting document-service fault modes.” |

### `mock-services/document-service/src/main/java/com/madlanga/lab/document/dto/`

| Open | Say |
|---|---|
| [ApiError.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/dto/ApiError.java) | “This file defines the sanitized code, message and correlation information returned for a synthetic service failure.” |
| [DocumentReceipt.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/dto/DocumentReceipt.java) | “This file represents the document identifier and receipt returned along the synthetic service chain.” |
| [DocumentRequest.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/dto/DocumentRequest.java) | “This file validates the document fields before DocumentController asks the service to persist a receipt.” |

### `mock-services/document-service/src/main/java/com/madlanga/lab/document/exception/`

| Open | Say |
|---|---|
| [ApiExceptionHandler.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/exception/ApiExceptionHandler.java) | “This file translates validation, downstream or persistence failures into sanitized HTTP errors appropriate to its synthetic service.” |

### `mock-services/document-service/src/main/java/com/madlanga/lab/document/fault/`

| Open | Say |
|---|---|
| [FaultConfig.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/fault/FaultConfig.java) | “This file bounds the selected synthetic fault mode, delay and failure cadence before injection.” |
| [FaultInjectionService.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/fault/FaultInjectionService.java) | “This file applies bounded in-process lab faults while keeping the service running, and resets the configuration on request.” |
| [FaultMode.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/fault/FaultMode.java) | “This file names healthy, continuous error, intermittent error, latency and database-connectivity lab modes.” |
| [SyntheticFaultException.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/fault/SyntheticFaultException.java) | “This file signals an intentional application fault to the mock service’s error handler.” |

### `mock-services/document-service/src/main/java/com/madlanga/lab/document/persistence/`

| Open | Say |
|---|---|
| [DocumentRepository.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/persistence/DocumentRepository.java) | “This file uses JDBC to store and retrieve synthetic documents in the monitored PostgreSQL database.” |

### `mock-services/document-service/src/main/java/com/madlanga/lab/document/service/`

| Open | Say |
|---|---|
| [DocumentService.java](mock-services/document-service/src/main/java/com/madlanga/lab/document/service/DocumentService.java) | “This file applies the lab fault control and then persists a synthetic document through DocumentRepository.” |

### `mock-services/document-service/src/main/resources/`

| Open | Say |
|---|---|
| [application.yml](mock-services/document-service/src/main/resources/application.yml) | “This file configures this synthetic service’s port, health and metrics exposure, logging and downstream or database connection.” |

### `mock-services/document-service/src/main/resources/db/migration/`

| Open | Say |
|---|---|
| [V1__create_synthetic_documents.sql](mock-services/document-service/src/main/resources/db/migration/V1__create_synthetic_documents.sql) | “This file creates the synthetic document table used by DocumentRepository in the monitored database.” |

### `mock-services/document-service/src/test/java/com/madlanga/lab/document/`

| Open | Say |
|---|---|
| [DocumentApplicationTests.java](mock-services/document-service/src/test/java/com/madlanga/lab/document/DocumentApplicationTests.java) | “This file checks document validation, health exposure, migration startup and real document persistence in the test database.” |
| [DocumentFailureTests.java](mock-services/document-service/src/test/java/com/madlanga/lab/document/DocumentFailureTests.java) | “This file checks that a simulated database connectivity failure returns a sanitized HTTP 503.” |
| [FaultInjectionServiceTest.java](mock-services/document-service/src/test/java/com/madlanga/lab/document/FaultInjectionServiceTest.java) | “This file checks each retained degradation mode, bounds, failure cadence and reset to healthy lab behavior.” |

### `mock-services/document-service/src/test/resources/`

| Open | Say |
|---|---|
| [application-test.yml](mock-services/document-service/src/test/resources/application-test.yml) | “This file supplies test-only Spring settings so this module’s tests can run without its normal production database and scheduling configuration.” |

### `mock-services/document-service/src/test/resources/mockito-extensions/`

| Open | Say |
|---|---|
| [org.mockito.plugins.MockMaker](mock-services/document-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker) | “This file selects the Mockito test mock implementation used by this service’s test suite.” |

### `mock-services/payment-service/`

| Open | Say |
|---|---|
| [.dockerignore](mock-services/payment-service/.dockerignore) | “This file excludes generated output and local-only files from this module’s Docker build context.” |
| [Dockerfile](mock-services/payment-service/Dockerfile) | “This file builds and verifies this Java module with Maven, then packages its runtime image for Compose.” |
| [pom.xml](mock-services/payment-service/pom.xml) | “This file defines this Java module’s dependencies, Java version and Maven build/test configuration.” |

### `mock-services/payment-service/src/main/java/com/madlanga/lab/payment/`

| Open | Say |
|---|---|
| [PaymentApplication.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/PaymentApplication.java) | “This file starts the independently deployable synthetic payment application.” |

### `mock-services/payment-service/src/main/java/com/madlanga/lab/payment/client/`

| Open | Say |
|---|---|
| [CustomerClient.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/client/CustomerClient.java) | “This file calls customer-service from payment-service and translates downstream errors into DownstreamException.” |

### `mock-services/payment-service/src/main/java/com/madlanga/lab/payment/config/`

| Open | Say |
|---|---|
| [CorrelationIdFilter.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/config/CorrelationIdFilter.java) | “This file validates or generates a business correlation ID and makes it available to request logs and downstream calls.” |
| [HttpClientConfig.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/config/HttpClientConfig.java) | “This file configures the service’s outbound HTTP client with timeouts and observation support for downstream requests.” |

### `mock-services/payment-service/src/main/java/com/madlanga/lab/payment/controller/`

| Open | Say |
|---|---|
| [PaymentController.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/controller/PaymentController.java) | “This file accepts synthetic payment HTTP requests and delegates to PaymentService.” |

### `mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/`

| Open | Say |
|---|---|
| [ApiError.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/ApiError.java) | “This file defines the sanitized code, message and correlation information returned for a synthetic service failure.” |
| [CustomerValidation.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/CustomerValidation.java) | “This file carries the customer validation outcome and related document result between customer and payment services.” |
| [DocumentReceipt.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/DocumentReceipt.java) | “This file represents the document identifier and receipt returned along the synthetic service chain.” |
| [DownstreamRequest.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/DownstreamRequest.java) | “This file carries the fields sent to the next service in the synthetic request chain.” |
| [PaymentRequest.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/PaymentRequest.java) | “This file validates the incoming synthetic payment fields before PaymentController invokes business handling.” |
| [PaymentResult.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/PaymentResult.java) | “This file carries the payment outcome and downstream validation information back to the caller.” |

### `mock-services/payment-service/src/main/java/com/madlanga/lab/payment/exception/`

| Open | Say |
|---|---|
| [ApiExceptionHandler.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/exception/ApiExceptionHandler.java) | “This file translates validation, downstream or persistence failures into sanitized HTTP errors appropriate to its synthetic service.” |
| [DownstreamException.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/exception/DownstreamException.java) | “This file carries a downstream-call failure to the service’s HTTP exception handler without exposing the raw remote response.” |

### `mock-services/payment-service/src/main/java/com/madlanga/lab/payment/service/`

| Open | Say |
|---|---|
| [PaymentService.java](mock-services/payment-service/src/main/java/com/madlanga/lab/payment/service/PaymentService.java) | “This file processes a synthetic payment by requesting customer validation through CustomerClient.” |

### `mock-services/payment-service/src/main/resources/`

| Open | Say |
|---|---|
| [application.yml](mock-services/payment-service/src/main/resources/application.yml) | “This file configures this synthetic service’s port, health and metrics exposure, logging and downstream or database connection.” |

### `mock-services/payment-service/src/test/java/com/madlanga/lab/payment/`

| Open | Say |
|---|---|
| [PaymentApplicationTests.java](mock-services/payment-service/src/test/java/com/madlanga/lab/payment/PaymentApplicationTests.java) | “This file checks payment endpoints, validation, correlation propagation and controlled downstream failures.” |

### `mock-services/payment-service/src/test/resources/mockito-extensions/`

| Open | Say |
|---|---|
| [org.mockito.plugins.MockMaker](mock-services/payment-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker) | “This file selects the Mockito test mock implementation used by this service’s test suite.” |

### `scripts/`

| Open | Say |
|---|---|
| [run-phase11-e2e.py](scripts/run-phase11-e2e.py) | “This file runs six Stage 1 cases against the live lab, stopping and restoring services while checking detection, propagation and guarded recovery.” |
| [run-stage2-e2e.py](scripts/run-stage2-e2e.py) | “This file runs four Stage 2 degradation cases using lab fault controls while asserting real evidence and same-incident recovery.” |

### `traffic-generator/`

| Open | Say |
|---|---|
| [.dockerignore](traffic-generator/.dockerignore) | “This file excludes generated output and local-only files from this module’s Docker build context.” |
| [Dockerfile](traffic-generator/Dockerfile) | “This file packages the synthetic Python traffic process for execution by Compose.” |
| [test_traffic.py](traffic-generator/test_traffic.py) | “This file checks traffic settings, error handling, synthetic requests and controlled shutdown behavior.” |
| [traffic.py](traffic-generator/traffic.py) | “This file sends synthetic payments at a configured cadence so the full service chain produces real runtime telemetry.” |

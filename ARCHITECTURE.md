# Repository Architecture — Folder and File Guide

This document is the code map for the MadlangaAI Blast Radius Engine. It explains what each tracked source/configuration file is responsible for and how it connects to the rest of the system.

## Root

- `.env.example` — template for local environment values, including optional AI configuration. Docker Compose reads the corresponding local `.env`.
- `.gitignore` — prevents generated files, secrets and build output from being committed.
- `LICENSE` — repository licence.
- `docker-compose.yml` — assembles the complete local lab: API, dashboard, three synthetic services, two PostgreSQL databases, traffic generator and observability stack. It is the main runtime wiring file.
- `README.md` — system overview and entry point.
- `SETUP.md` — clone/build/run/test handover.
- `ARCHITECTURE.md` — this file-by-file map.
- `PRESENTATION_GUIDE.md` — short manager-facing walkthrough.

## blast-radius-api/

The production-shaped Spring Boot engine. It is organized by capability first and layer second.

- `blast-radius-api/.dockerignore` — excludes unnecessary files from the API Docker build context.
- `blast-radius-api/Dockerfile` — builds/runs the Spring Boot API container.
- `blast-radius-api/pom.xml` — Maven dependencies/plugins for Spring Boot, JDBC/Flyway, HTTP/JSON, testing and related API concerns.
- `BlastRadiusApplication.java` — Spring Boot entry point; component scanning discovers the capability packages below.

### diagnosis

The diagnosis capability is packaged by feature. Its core types live directly under `diagnosis/`, while external provider implementations live under `diagnosis/provider/`.

- `DiagnosisResponse.java` — provider-neutral diagnosis result returned to the application/API.
- `DiagnosisRequest.java` — bounded, provider-neutral input model sent to diagnosis providers.
- `DiagnosisProvider.java` — provider contract used by the diagnosis service.
- `DiagnosisService.java` — orchestrates advisory diagnosis and deterministic fallback without calculating blast radius.
- `DiagnosisRequestMapper.java` — builds diagnosis context from either a fresh `IncidentAnalysis` or a persisted sanitized incident snapshot.
- `GeminiProperties.java` — binds Gemini-specific configuration.
- `DiagnosisConfig.java` — Spring wiring for the mapper, providers and diagnosis service.
- `provider/DeterministicDiagnosisProvider.java` — local deterministic fallback used when external AI is disabled or unavailable.
- `provider/GeminiDiagnosisProvider.java` — Gemini HTTP integration, bounded retry/fallback-model handling and response mapping.

### incident

**API**
- `IncidentController.java` — HTTP entry point for analysis/diagnosis operations; delegates to application services rather than performing graph/telemetry logic itself.
- `IncidentHistoryController.java` — HTTP access to persisted ACTIVE/RESOLVED incident history and resolution operations.
- `api/dto/AnalyzeIncidentRequest.java` — request contract for explicit incident analysis.
- `api/dto/ErrorResponse.java` — stable HTTP error payload.
- `api/dto/IncidentResponse.java` — API representation of persisted incident data.
- `api/dto/ResolveIncidentRequest.java` — transport contract for a manual resolution request.

**Application**
- `IncidentAnalysisService.java` — central orchestration use case. It obtains topology and telemetry through ports, runs deterministic graph/origin/severity logic, builds evidence/coverage and returns the incident analysis consumed by lifecycle/API/diagnosis.
- `application/port/IncidentRepository.java` — persistence contract used by incident/lifecycle code; JDBC is hidden behind this interface.

**Domain**
- `ComponentImpact.java` — impact of the incident on one component, including observed/theoretical classification data.
- `OriginAssessment.Confidence.java` — bounded confidence classification used for origin assessment.
- `EvidenceSignal.java` — domain representation of an evidence signal attached to the incident.
- `IncidentAnalysis.java` — aggregate analysis result joining origin, impacts, evidence, coverage and deterministic severity.
- `IncidentSeverity.java` — deterministic severity result.
- `IncidentSeverityService.java` — pure business rule that converts incident evidence/impact into deterministic severity.
- `PersistedIncident.Status.java` — persisted lifecycle state such as ACTIVE/RESOLVED.
- `ComponentImpact.State.java` — identifies whether component impact was observed or only theoretical.
- `OriginAssessment.java` — likely origin plus confidence/evidence.
- `PersistedIncident.java` — domain representation stored/retrieved through `IncidentRepository`.
- `IncidentSeverity.Level.java` — named severity levels used with the numeric score.


**Infrastructure**
- `infrastructure/persistence/JdbcIncidentRepository.java` — JDBC implementation of `IncidentRepository`; reads/writes the diagnostic PostgreSQL database.

### lifecycle

- `application/IncidentLifecycleService.java` — creates/updates one ACTIVE incident from analysis and applies recovery rules without duplicating the incident.
- `infrastructure/IncidentLifecycleScheduler.java` — Spring scheduled entry point. It periodically invokes analysis/lifecycle logic for the configured local application/environment and drives proactive detection/recovery.

### shared

- `config/OpenApiConfig.java` — OpenAPI metadata/configuration for the REST service.
- `sanitization/TelemetrySanitizer.java` — standard sensitive-data patterns to remove before persistence/display/AI use.
- `sanitization/TelemetrySanitizer.java` — stable replacement values used when redaction occurs.
- `sanitization/RedactionRule.java` — one redaction rule abstraction.
- `sanitization/TelemetrySanitizer.java` — applies redaction rules to telemetry before evidence leaves the trusted collection boundary.

### telemetry

**Application port**
- `application/port/TelemetryProvider.java` — provider-neutral contract used by incident analysis to request logs, metrics, traces and health without depending on Loki/Prometheus/Tempo.

**Domain**
- `CoverageStatus.java` — availability/coverage state used to distinguish missing evidence from healthy evidence.
- `EvidenceFamily.java` — LOG/METRIC/TRACE/HEALTH family classification.
- `EvidenceProvenance.java` — source/timestamp/provenance attached to normalized evidence.
- `HealthEvidence.java` — normalized health evidence.
- `HealthState.java` — health-state classification.
- `LogEvidence.java` — normalized log evidence.
- `MetricEvidence.java` — normalized metric evidence.
- `SpanEvidence.java` — normalized distributed-trace span evidence.
- `SpanKind.java` — normalized span kind.
- `SpanStatus.java` — normalized trace status.
- `TelemetryBundle.java` — combined evidence returned by `TelemetryProvider`.
- `TelemetryCoverage.java` — describes which evidence families were available for a query; lifecycle uses this to avoid unsafe recovery.
- `TelemetryQuery.java` — provider-neutral component/time-window query.

**Infrastructure**
- `LocalTelemetryProvider.java` — composes the local Loki, Prometheus, Tempo and Actuator adapters into the provider-neutral `TelemetryBundle`.
- `config/TelemetryConfig.java` — Spring wiring for telemetry adapters.
- `health/ActuatorHealthAdapter.java` — queries service health endpoints and converts responses to `HealthEvidence`.
- `health/ActuatorHealthProperties.java` — configured health endpoints/component mapping.
- `health/ActuatorHealthResponse.java` — infrastructure DTO for Actuator responses.
- `loki/LokiLogAdapter.java` — queries Loki and normalizes matching log lines.
- `loki/LokiLogLine.java` — parsed Loki log-line model used inside the adapter.
- `loki/LokiProperties.java` — Loki URL/query configuration.
- `loki/LokiResponse.java` — Loki HTTP response DTO.
- `prometheus/PrometheusMetricsAdapter.java` — queries Prometheus for failure/degradation signals and normalizes metrics.
- `prometheus/PrometheusProperties.java` — Prometheus query/configuration properties.
- `prometheus/PrometheusResponse.java` — Prometheus HTTP response DTO.
- `tempo/TempoProperties.java` — Tempo endpoint/query configuration.
- `tempo/TempoResponse.java` — trace-detail DTO returned by Tempo.
- `tempo/TempoSearchResponse.java` — Tempo trace-search DTO.
- `tempo/TempoTraceAdapter.java` — discovers/searches traces and converts spans to `SpanEvidence`; it also supports runtime topology discovery.

### topology

**Application ports**
- `application/port/DependencyTopologyProvider.java` — supplies the canonical dependency graph used by incident analysis.
- `application/port/RuntimeSpanSource.java` — boundary through which topology discovery can obtain runtime spans.
- `application/port/TopologyStore.java` — persistence boundary for retained topology snapshots.

**Domain**
- `ComponentNode.java` — one canonical component in the dependency graph.
- `ComponentType.java` — component category such as service/database.
- `DependencyEdge.java` — directed caller/dependency relationship.
- `DependencyTopology.java` — complete graph of nodes and edges.
- `DeterministicGraphEngine.java` — traverses the graph from an origin to calculate theoretical blast radius and propagation depth.
- `GraphAnalysisResult.java` — result of deterministic graph traversal.
- `ImpactClassification.java` — graph/impact classification used during analysis.
- `RetainedTopology.java` — topology plus retention metadata.
- `TheoreticalImpact.java` — one component's potential impact according to topology.

**Infrastructure**
- `RetainedTopologyProvider.java` — serves a retained topology to the application and coordinates refresh/persistence.
- `TraceDiscoveredTopologyProvider.java` — derives dependency relationships from runtime trace spans obtained through `RuntimeSpanSource`.
- `config/TopologyConfig.java` — Spring configuration for topology providers/store.
- `persistence/FileTopologyStore.java` — local filesystem implementation of `TopologyStore`.

### API resources and migrations

- `src/main/resources/application.yml` — central Spring configuration: database, telemetry endpoints, topology, lifecycle scheduling, AI and local application/environment settings.
- `db/migration/V1__create_incidents.sql` — Flyway baseline creating incident persistence structures.
- `db/migration/V2__enforce_single_active_incident.sql` — database protection enforcing the intended single-active-incident identity rule.

### API tests

- `ArchitectureBoundaryTest.java` — guards capability/layer boundaries so future code cannot casually reintroduce global service/adapter architecture.
- `BlastRadiusApplicationTests.java` — Spring application/context smoke tests.
- `diagnosis/DiagnosisServiceTest.java` — verifies diagnosis orchestration and fallback behavior.
- `diagnosis/DiagnosisRequestMapperTest.java` — verifies mapping from live incident analysis to diagnosis context.
- `diagnosis/DiagnosisRequestMapperStoredJsonTest.java` — verifies persisted snapshot reconstruction and re-sanitization.
- `diagnosis/provider/GeminiDiagnosisProviderTest.java` — tests Gemini request/response/retry/failure behavior.
- `incident/controller/IncidentControllerTest.java` — verifies HTTP analysis/diagnosis contract and error mapping.
- `incident/service/IncidentAnalysisServiceTest.java` — tests orchestration across topology, telemetry and deterministic rules.
- `incident/model/IncidentSeverityServiceTest.java` — verifies deterministic severity rules.
- `lifecycle/application/IncidentLifecycleServiceTest.java` — verifies ACTIVE reuse and recovery transitions.
- `shared/sanitization/TelemetrySanitizerTest.java` — verifies sensitive telemetry is redacted.
- `telemetry/model/EvidenceProvenanceTest.java`, `HealthEvidenceTest.java`, `LogEvidenceTest.java`, `MetricEvidenceTest.java`, `SpanEvidenceTest.java`, `TelemetryBundleTest.java`, `TelemetryQueryTest.java` — verify provider-neutral evidence validation and behavior.
- `telemetry/provider/LocalTelemetryProviderTest.java` — verifies composition of local telemetry families.
- `telemetry/provider/LocalTelemetryProviderLiveIT.java` — integration check against live local observability backends.
- `telemetry/provider/health/ActuatorHealthAdapterTest.java` — verifies health normalization.
- `telemetry/provider/loki/LokiLogAdapterTest.java` — verifies Loki parsing/query behavior.
- `telemetry/provider/prometheus/PrometheusMetricsAdapterTest.java` — verifies metric query/normalization.
- `telemetry/provider/tempo/CapturedTempoRegressionTest.java` — regression test using captured Tempo payloads.
- `telemetry/provider/tempo/TempoTraceAdapterTest.java` — verifies Tempo search/trace normalization.
- `topology/domain/DeterministicGraphEngineTest.java` — verifies deterministic graph traversal.
- `topology/domain/TopologyDomainTest.java` — verifies topology invariants.
- `topology/infrastructure/RetainedTopologyProviderTest.java` — verifies retained topology behavior.
- `topology/infrastructure/TraceDiscoveredTopologyProviderLiveIT.java` — live integration check for trace-discovered topology.
- `topology/infrastructure/TraceDiscoveredTopologyProviderTest.java` — unit tests for trace-to-topology mapping.
- `src/test/resources/application.yml` — test-specific Spring configuration.
- `src/test/resources/tempo/failed-connection.json` — captured trace fixture representing a failed dependency connection.
- `src/test/resources/tempo/healthy-database.json` — captured healthy database trace fixture.

## frontend/

- `.dockerignore` — trims frontend Docker context.
- `.env.example` — frontend environment template, primarily API endpoint configuration.
- `Dockerfile` — builds/serves the React/Vite dashboard.
- `index.html` — Vite HTML entry point.
- `nginx.conf` — serves the built frontend and routes browser traffic as configured.
- `package.json` — frontend scripts/dependencies.
- `package-lock.json` — pinned npm dependency graph.
- `src/main.jsx` — dashboard application: fetches incident data, presents Stage 1/Stage 2 views and requests optional diagnosis.
- `src/styles.css` — dashboard styling.

## infrastructure/observability/

- `logging/loki.yml` — Loki local storage/server configuration; receives logs from the collector and is queried by `LokiLogAdapter`.
- `otel/collector.yml` — OpenTelemetry Collector pipelines routing application telemetry to Loki/Prometheus/Tempo.
- `otel/javaagent.properties` — shared OpenTelemetry Java-agent settings used by the synthetic Java services.
- `prometheus/prometheus.yml` — Prometheus scrape/query configuration consumed by `PrometheusMetricsAdapter`.
- `tracing/tempo.yml` — Tempo trace backend configuration consumed by `TempoTraceAdapter`.

## mock-services/payment-service/

This is the top of the synthetic business request chain.

- `.dockerignore`, `Dockerfile`, `pom.xml` — container/build definition.
- `PaymentApplication.java` — Spring Boot entry point.
- `client/CustomerClient.java` — HTTP client from payment-service to customer-service; creates the first downstream dependency edge visible in traces.
- `config/CorrelationIdFilter.java` — propagates/creates correlation IDs for cross-service evidence.
- `config/HttpClientConfig.java` — HTTP client timeout/configuration.
- `controller/PaymentController.java` — receives synthetic payment requests from the traffic generator.
- `dto/ApiError.java` — payment API error payload.
- `dto/CustomerValidation.java` — downstream customer validation response.
- `dto/DocumentReceipt.java` — downstream document result carried through the chain.
- `dto/DownstreamRequest.java` — request sent to customer-service.
- `dto/PaymentRequest.java` — inbound payment request.
- `dto/PaymentResult.java` — successful payment response.
- `exception/ApiExceptionHandler.java` — maps payment exceptions to HTTP errors/log evidence.
- `exception/DownstreamException.java` — represents customer-service call failures.
- `service/PaymentService.java` — business flow that calls `CustomerClient` and returns a payment result.
- `src/main/resources/application.yml` — port, downstream URL, actuator and telemetry settings.
- `PaymentApplicationTests.java` — payment service behavior tests.
- `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` — Mockito test-engine configuration.

## mock-services/customer-service/

This is the middle service in the synthetic chain.

- `.dockerignore`, `Dockerfile`, `pom.xml` — container/build definition.
- `CustomerApplication.java` — Spring Boot entry point.
- `client/DocumentClient.java` — HTTP client from customer-service to document-service; forms the second service dependency.
- `config/CorrelationIdFilter.java` — correlation-ID propagation.
- `config/HttpClientConfig.java` — downstream timeout/client configuration; relevant to the Stage 2 latency scenario.
- `controller/CustomerController.java` — receives validation requests from payment-service.
- `dto/ApiError.java` — API error payload.
- `dto/CustomerRequest.java` — customer validation request.
- `dto/CustomerValidation.java` — validation result returned upstream.
- `dto/DocumentReceipt.java` — document result returned by document-service.
- `dto/DownstreamRequest.java` — document-service request.
- `exception/ApiExceptionHandler.java` — HTTP error mapping.
- `exception/DownstreamException.java` — document-service call failure.
- `service/CustomerService.java` — customer flow that calls `DocumentClient`.
- `src/main/resources/application.yml` — service/downstream/telemetry settings.
- `CustomerApplicationTests.java` — customer behavior tests.
- `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` — Mockito configuration.

## mock-services/document-service/

This is the bottom application service and the only synthetic service that talks directly to the monitored PostgreSQL database.

- `.dockerignore`, `Dockerfile`, `pom.xml` — container/build definition.
- `DocumentApplication.java` — Spring Boot entry point.
- `config/CorrelationIdFilter.java` — correlation-ID propagation.
- `controller/DocumentController.java` — receives document operations from customer-service.
- `controller/FaultInjectionController.java` — localhost lab endpoint used by Stage 2 to enable/reset bounded degradation.
- `dto/ApiError.java` — API error payload.
- `dto/DocumentReceipt.java` — successful document response.
- `dto/DocumentRequest.java` — inbound document request.
- `exception/ApiExceptionHandler.java` — HTTP error mapping, including synthetic failures.
- `fault/FaultConfig.java` — current fault-mode configuration.
- `fault/FaultInjectionService.java` — applies configured latency/error/database degradation before/around normal document work.
- `fault/FaultMode.java` — allowed synthetic modes.
- `fault/SyntheticFaultException.java` — explicit lab failure exception.
- `persistence/DocumentRepository.java` — JDBC access to the monitored PostgreSQL database; database failure here produces real downstream consequences.
- `service/DocumentService.java` — document flow connecting fault injection and persistence.
- `src/main/resources/application.yml` — DB, actuator and telemetry configuration.
- `db/migration/V1__create_synthetic_documents.sql` — Flyway schema for synthetic documents.
- `DocumentApplicationTests.java` — normal document behavior tests.
- `DocumentFailureTests.java` — failure-path tests.
- `FaultInjectionServiceTest.java` — Stage 2 fault-mode unit tests.
- `src/test/resources/application-test.yml` — isolated document test configuration.
- `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` — Mockito configuration.

## traffic-generator/

- `.dockerignore` — Docker build exclusions.
- `Dockerfile` — packages the Python generator.
- `traffic.py` — continuously posts synthetic payment requests so failures produce real cross-service telemetry without manual traffic.
- `test_traffic.py` — verifies traffic-generator request/payload behavior.

## scripts/

- `run-phase11-e2e.py` — Stage 1 acceptance runner: healthy baseline, hard outages, stable incident identity, recovery and partial-observability protection; expected 6/6.
- `run-stage2-e2e.py` — Stage 2 runner: HTTP 500, intermittent errors, latency and DB-connectivity degradation while services stay running; expected 4/4.

## fixtures/


## How the main files connect

```text
traffic.py
   -> PaymentController -> PaymentService -> CustomerClient
   -> CustomerController -> CustomerService -> DocumentClient
   -> DocumentController -> DocumentService -> DocumentRepository -> postgres

Java agents / service logs / actuator
   -> OpenTelemetry Collector / Prometheus
   -> Loki + Tempo + Prometheus + health endpoints
   -> LocalTelemetryProvider
   -> TelemetryProvider
   -> IncidentAnalysisService

Tempo spans
   -> TempoTraceAdapter / RuntimeSpanSource
   -> TraceDiscoveredTopologyProvider
   -> TopologyStore / RetainedTopologyProvider
   -> DependencyTopologyProvider
   -> IncidentAnalysisService

IncidentAnalysisService
   -> DeterministicGraphEngine
   -> IncidentSeverityService
   -> IncidentAnalysis
   -> IncidentLifecycleService
   -> JdbcIncidentRepository -> blast-radius-db
   -> IncidentHistoryController -> frontend/src/main.jsx

Persisted/fresh IncidentAnalysis
   -> DiagnosisRequestMapper
   -> DiagnosisService -> DiagnosisProvider
   -> DeterministicDiagnosisProvider or GeminiDiagnosisProvider
```

That flow is the architectural spine of the repository.

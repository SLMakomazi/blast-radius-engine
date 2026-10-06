# Project structure

Four Java projects build independently: the Blast Radius API and the three synthetic monitored services. Each has its own Maven POM, Dockerfile, resources and tests. The frontend uses React/Vite; traffic and acceptance runners use Python.

## Repository areas

| Area | Responsibility |
|---|---|
| `blast-radius-api` | Deterministic incident analysis, lifecycle, REST and optional advisory diagnosis |
| `mock-services` | Small payment/customer/document monitored applications and local faults |
| `frontend` | Incident history/details, Stage 1–4 views, polling and on-demand diagnosis |
| `traffic-generator` | Continuous synthetic traffic and its tests |
| `infrastructure/observability` | Collector, Java agent, Loki, Prometheus and Tempo settings |
| `scripts` | Stage 1–4 acceptance, observability/business probes and offline captured-topology helper |
| `fixtures/payment-request.json` | Active synthetic request data |
| `docs` | Architecture, integration, decisions, developer guidance, audit and validation results |

## Main Java packages

| Package | Responsibility |
|---|---|
| `domain.evidence` | Normalized telemetry, coverage, scope and provenance |
| `domain.topology` | Components, edges, retained knowledge and pure graph traversal |
| `domain.incident` | Origin, impact, status and pure severity rules |
| `domain.experiment` | Expected impact and pure containment assessment |
| `domain.diagnosis` | Provider-neutral advisory diagnosis contracts |
| `service` | Application use cases: analysis, lifecycle, diagnosis and context mapping |
| `ports` | Seven external contracts |
| `adapters.telemetry` | Four local provider adapters and the composite telemetry provider |
| `adapters.topology` | Runtime discovery and retained topology acquisition |
| `adapters.persistence` | JDBC incident history and file topology storage |
| `adapters.ai` | Gemini, deterministic fallback and provider wiring |
| `adapters.experiment` | Local experiment metadata (not fault injection) |
| `adapters.scheduling` | Proactive lifecycle entry point and guarded recovery |
| `api` / `api.dto` | HTTP controllers, handlers, OpenAPI metadata and transport records |
| `config` | Shared HTTP and topology wiring |
| `sanitization` | Redaction policies and telemetry sanitizer |

Tests follow their responsibility or class under test. Provider response models remain internal to their adapter. Mock services keep their existing simple controller/service/dto/exception/client/config/persistence/fault structure; they do not share the Blast Radius domain.

## Common changes

| Change | Start here |
|---|---|
| Potential impact and paths | `domain/topology/DeterministicGraphEngine.java` |
| Origin/evidence correlation | `service/IncidentAnalysisService.java` |
| Severity | `domain/incident/IncidentSeverityCalculator.java` |
| Detection/recovery | `adapters/scheduling/IncidentLifecycleMonitor.java`, `service/IncidentLifecycleService.java` |
| New telemetry/topology provider | Matching `ports` contract and a new adapter |
| AI provider | `ports/AiDiagnosisPort.java`, `adapters/ai` |
| API data | `api/dto` |
| Diagnostic schema | `blast-radius-api/src/main/resources/db/migration` |
| Lab failure semantics | `mock-services/document-service/.../fault` |

Runtime databases/topology live in Compose volumes. Maven `target`, frontend build output and Python bytecode are generated data, not source. Empty placeholder-only future directories have been removed; no runtime assets were removed.

For placement rules see [DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md). For the original inventory see [ARCHITECTURAL_AUDIT.md](docs/ARCHITECTURAL_AUDIT.md).

## Complete source tree after cleanup

```text
├── .env.example
├── .gitignore
├── ABOUT.md
├── CODEX.md
├── LICENSE
├── PROJECT_STRUCTURE.md
├── README.md
├── SETUP.md
├── blast-radius-api/
│   ├── .dockerignore
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   └── com/
│       │   │       └── madlanga/
│       │   │           └── blastradius/
│       │   │               ├── BlastRadiusApplication.java
│       │   │               ├── adapters/
│       │   │               │   ├── ai/
│       │   │               │   │   ├── .gitkeep
│       │   │               │   │   ├── AiDiagnosisConfiguration.java
│       │   │               │   │   ├── DeterministicDiagnosisAdapter.java
│       │   │               │   │   ├── GeminiDiagnosisAdapter.java
│       │   │               │   │   └── GeminiProperties.java
│       │   │               │   ├── experiment/
│       │   │               │   │   └── LocalFailureExperimentProvider.java
│       │   │               │   ├── persistence/
│       │   │               │   │   ├── .gitkeep
│       │   │               │   │   ├── FileTopologyStore.java
│       │   │               │   │   └── JdbcIncidentRepository.java
│       │   │               │   ├── scheduling/
│       │   │               │   │   └── IncidentLifecycleMonitor.java
│       │   │               │   ├── telemetry/
│       │   │               │   │   ├── LocalTelemetryProvider.java
│       │   │               │   │   ├── health/
│       │   │               │   │   │   ├── ActuatorHealthAdapter.java
│       │   │               │   │   │   ├── ActuatorHealthProperties.java
│       │   │               │   │   │   └── ActuatorHealthResponse.java
│       │   │               │   │   ├── loki/
│       │   │               │   │   │   ├── LokiLogAdapter.java
│       │   │               │   │   │   ├── LokiLogLine.java
│       │   │               │   │   │   ├── LokiProperties.java
│       │   │               │   │   │   └── LokiResponse.java
│       │   │               │   │   ├── prometheus/
│       │   │               │   │   │   ├── PrometheusMetricsAdapter.java
│       │   │               │   │   │   ├── PrometheusProperties.java
│       │   │               │   │   │   └── PrometheusResponse.java
│       │   │               │   │   └── tempo/
│       │   │               │   │       ├── TempoProperties.java
│       │   │               │   │       ├── TempoResponse.java
│       │   │               │   │       ├── TempoSearchResponse.java
│       │   │               │   │       └── TempoTraceAdapter.java
│       │   │               │   └── topology/
│       │   │               │       ├── .gitkeep
│       │   │               │       ├── RetainedTopologyProvider.java
│       │   │               │       └── TraceDiscoveredTopologyProvider.java
│       │   │               ├── api/
│       │   │               │   ├── BlastRadiusController.java
│       │   │               │   ├── IncidentHistoryController.java
│       │   │               │   ├── OpenApiConfiguration.java
│       │   │               │   └── dto/
│       │   │               │       ├── AnalyzeIncidentRequest.java
│       │   │               │       ├── ErrorResponse.java
│       │   │               │       ├── IncidentResponse.java
│       │   │               │       └── ResolveIncidentRequest.java
│       │   │               ├── config/
│       │   │               │   ├── TelemetryAdapterConfig.java
│       │   │               │   └── TopologyConfig.java
│       │   │               ├── controller/
│       │   │               │   └── .gitkeep
│       │   │               ├── domain/
│       │   │               │   ├── correlation/
│       │   │               │   │   └── .gitkeep
│       │   │               │   ├── diagnosis/
│       │   │               │   │   ├── AiDiagnosis.java
│       │   │               │   │   └── DiagnosisContext.java
│       │   │               │   ├── evidence/
│       │   │               │   │   ├── CoverageStatus.java
│       │   │               │   │   ├── EvidenceFamily.java
│       │   │               │   │   ├── EvidenceProvenance.java
│       │   │               │   │   ├── HealthEvidence.java
│       │   │               │   │   ├── HealthState.java
│       │   │               │   │   ├── LogEvidence.java
│       │   │               │   │   ├── MetricEvidence.java
│       │   │               │   │   ├── SpanEvidence.java
│       │   │               │   │   ├── SpanKind.java
│       │   │               │   │   ├── SpanStatus.java
│       │   │               │   │   ├── TelemetryBundle.java
│       │   │               │   │   ├── TelemetryCoverage.java
│       │   │               │   │   └── TelemetryQuery.java
│       │   │               │   ├── experiment/
│       │   │               │   │   ├── ContainmentStatus.java
│       │   │               │   │   ├── ExperimentAssessment.java
│       │   │               │   │   ├── FailureExperiment.java
│       │   │               │   │   └── FailureExperimentAssessmentService.java
│       │   │               │   ├── graph/
│       │   │               │   │   └── .gitkeep
│       │   │               │   ├── incident/
│       │   │               │   │   ├── ComponentImpact.java
│       │   │               │   │   ├── ConfidenceLevel.java
│       │   │               │   │   ├── EvidenceSignal.java
│       │   │               │   │   ├── IncidentAnalysis.java
│       │   │               │   │   ├── IncidentSeverity.java
│       │   │               │   │   ├── IncidentSeverityCalculator.java
│       │   │               │   │   ├── IncidentStatus.java
│       │   │               │   │   ├── ObservedState.java
│       │   │               │   │   ├── OriginAssessment.java
│       │   │               │   │   ├── PersistedIncident.java
│       │   │               │   │   └── SeverityLevel.java
│       │   │               │   ├── model/
│       │   │               │   │   └── .gitkeep
│       │   │               │   ├── severity/
│       │   │               │   │   └── .gitkeep
│       │   │               │   └── topology/
│       │   │               │       ├── ComponentNode.java
│       │   │               │       ├── ComponentType.java
│       │   │               │       ├── DependencyEdge.java
│       │   │               │       ├── DependencyTopology.java
│       │   │               │       ├── DeterministicGraphEngine.java
│       │   │               │       ├── GraphAnalysisResult.java
│       │   │               │       ├── ImpactClassification.java
│       │   │               │       ├── RetainedTopology.java
│       │   │               │       └── TheoreticalImpact.java
│       │   │               ├── dto/
│       │   │               │   └── .gitkeep
│       │   │               ├── exception/
│       │   │               │   └── .gitkeep
│       │   │               ├── ports/
│       │   │               │   ├── AiDiagnosisPort.java
│       │   │               │   ├── DependencyTopologyProvider.java
│       │   │               │   ├── FailureExperimentProvider.java
│       │   │               │   ├── IncidentRepository.java
│       │   │               │   ├── RuntimeSpanSource.java
│       │   │               │   ├── TelemetryProvider.java
│       │   │               │   └── TopologyStore.java
│       │   │               ├── sanitization/
│       │   │               │   ├── BuiltInRedactionRules.java
│       │   │               │   ├── RedactionPlaceholders.java
│       │   │               │   ├── RedactionRule.java
│       │   │               │   └── TelemetrySanitizer.java
│       │   │               └── service/
│       │   │                   ├── .gitkeep
│       │   │                   ├── AiDiagnosisService.java
│       │   │                   ├── DiagnosisContextFactory.java
│       │   │                   ├── IncidentAnalysisService.java
│       │   │                   ├── IncidentLifecycleService.java
│       │   │                   └── StoredAnalysisDiagnosisContextMapper.java
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/
│       │           └── migration/
│       │               ├── V1__create_incidents.sql
│       │               └── V2__enforce_single_active_incident.sql
│       └── test/
│           ├── java/
│           │   └── com/
│           │       └── madlanga/
│           │           └── blastradius/
│           │               ├── BlastRadiusApplicationTests.java
│           │               ├── adapters/
│           │               │   ├── ai/
│           │               │   │   └── GeminiDiagnosisAdapterTest.java
│           │               │   ├── telemetry/
│           │               │   │   ├── LocalTelemetryProviderLiveIT.java
│           │               │   │   ├── LocalTelemetryProviderTest.java
│           │               │   │   ├── health/
│           │               │   │   │   └── ActuatorHealthAdapterTest.java
│           │               │   │   ├── loki/
│           │               │   │   │   └── LokiLogAdapterTest.java
│           │               │   │   ├── prometheus/
│           │               │   │   │   └── PrometheusMetricsAdapterTest.java
│           │               │   │   └── tempo/
│           │               │   │       ├── CapturedTempoRegressionTest.java
│           │               │   │       └── TempoTraceAdapterTest.java
│           │               │   └── topology/
│           │               │       ├── RetainedTopologyProviderTest.java
│           │               │       ├── TraceDiscoveredTopologyProviderLiveIT.java
│           │               │       └── TraceDiscoveredTopologyProviderTest.java
│           │               ├── api/
│           │               │   └── BlastRadiusControllerTest.java
│           │               ├── domain/
│           │               │   ├── evidence/
│           │               │   │   ├── EvidenceProvenanceTest.java
│           │               │   │   ├── HealthEvidenceTest.java
│           │               │   │   ├── LogEvidenceTest.java
│           │               │   │   ├── MetricEvidenceTest.java
│           │               │   │   ├── SpanEvidenceTest.java
│           │               │   │   ├── TelemetryBundleTest.java
│           │               │   │   └── TelemetryQueryTest.java
│           │               │   ├── experiment/
│           │               │   │   └── FailureExperimentAssessmentServiceTest.java
│           │               │   ├── incident/
│           │               │   │   └── IncidentSeverityCalculatorTest.java
│           │               │   └── topology/
│           │               │       ├── DeterministicGraphEngineTest.java
│           │               │       └── TopologyDomainTest.java
│           │               ├── sanitization/
│           │               │   └── TelemetrySanitizerTest.java
│           │               └── service/
│           │                   ├── AiDiagnosisServiceTest.java
│           │                   ├── DiagnosisContextFactoryTest.java
│           │                   ├── IncidentAnalysisServiceTest.java
│           │                   ├── IncidentLifecycleServiceTest.java
│           │                   └── StoredAnalysisDiagnosisContextMapperTest.java
│           └── resources/
│               ├── application.yml
│               └── tempo/
│                   ├── README.md
│                   ├── failed-connection.json
│                   └── healthy-database.json
├── docker-compose.yml
├── docs/
│   ├── ARCHITECTURAL_AUDIT.md
│   ├── ARCHITECTURE.md
│   ├── DECISIONS.md
│   ├── DEVELOPER_GUIDE.md
│   ├── E2E_VALIDATION.md
│   ├── INTEGRATION.md
│   └── REFACTOR_REPORT.md
├── fixtures/
│   └── payment-request.json
├── frontend/
│   ├── .dockerignore
│   ├── .env.example
│   ├── Dockerfile
│   ├── README.md
│   ├── index.html
│   ├── nginx.conf
│   ├── package-lock.json
│   ├── package.json
│   └── src/
│       ├── main.jsx
│       └── styles.css
├── infrastructure/
│   └── observability/
│       ├── logging/
│       │   └── loki.yml
│       ├── otel/
│       │   ├── collector.yml
│       │   └── javaagent.properties
│       ├── prometheus/
│       │   └── prometheus.yml
│       └── tracing/
│           └── tempo.yml
├── mock-services/
│   ├── customer-service/
│   │   ├── .dockerignore
│   │   ├── Dockerfile
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/
│   │       │   │   └── com/
│   │       │   │       └── madlanga/
│   │       │   │           └── lab/
│   │       │   │               └── customer/
│   │       │   │                   ├── CustomerApplication.java
│   │       │   │                   ├── client/
│   │       │   │                   │   └── DocumentClient.java
│   │       │   │                   ├── config/
│   │       │   │                   │   ├── CorrelationIdFilter.java
│   │       │   │                   │   └── HttpClientConfig.java
│   │       │   │                   ├── controller/
│   │       │   │                   │   └── CustomerController.java
│   │       │   │                   ├── dto/
│   │       │   │                   │   ├── ApiError.java
│   │       │   │                   │   ├── CustomerRequest.java
│   │       │   │                   │   ├── CustomerValidation.java
│   │       │   │                   │   ├── DocumentReceipt.java
│   │       │   │                   │   └── DownstreamRequest.java
│   │       │   │                   ├── exception/
│   │       │   │                   │   ├── ApiExceptionHandler.java
│   │       │   │                   │   └── DownstreamException.java
│   │       │   │                   └── service/
│   │       │   │                       └── CustomerService.java
│   │       │   └── resources/
│   │       │       └── application.yml
│   │       └── test/
│   │           ├── java/
│   │           │   └── com/
│   │           │       └── madlanga/
│   │           │           └── lab/
│   │           │               └── customer/
│   │           │                   └── CustomerApplicationTests.java
│   │           └── resources/
│   │               └── mockito-extensions/
│   │                   └── org.mockito.plugins.MockMaker
│   ├── document-service/
│   │   ├── .dockerignore
│   │   ├── Dockerfile
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/
│   │       │   │   └── com/
│   │       │   │       └── madlanga/
│   │       │   │           └── lab/
│   │       │   │               └── document/
│   │       │   │                   ├── DocumentApplication.java
│   │       │   │                   ├── config/
│   │       │   │                   │   └── CorrelationIdFilter.java
│   │       │   │                   ├── controller/
│   │       │   │                   │   ├── DocumentController.java
│   │       │   │                   │   └── FaultInjectionController.java
│   │       │   │                   ├── dto/
│   │       │   │                   │   ├── ApiError.java
│   │       │   │                   │   ├── DocumentReceipt.java
│   │       │   │                   │   └── DocumentRequest.java
│   │       │   │                   ├── exception/
│   │       │   │                   │   └── ApiExceptionHandler.java
│   │       │   │                   ├── fault/
│   │       │   │                   │   ├── FaultConfig.java
│   │       │   │                   │   ├── FaultInjectionService.java
│   │       │   │                   │   ├── FaultMode.java
│   │       │   │                   │   └── SyntheticFaultException.java
│   │       │   │                   ├── persistence/
│   │       │   │                   │   └── DocumentRepository.java
│   │       │   │                   └── service/
│   │       │   │                       └── DocumentService.java
│   │       │   └── resources/
│   │       │       ├── application.yml
│   │       │       └── db/
│   │       │           └── migration/
│   │       │               └── V1__create_synthetic_documents.sql
│   │       └── test/
│   │           ├── java/
│   │           │   └── com/
│   │           │       └── madlanga/
│   │           │           └── lab/
│   │           │               └── document/
│   │           │                   ├── DocumentApplicationTests.java
│   │           │                   ├── DocumentFailureTests.java
│   │           │                   └── FaultInjectionServiceTest.java
│   │           └── resources/
│   │               ├── application-test.yml
│   │               └── mockito-extensions/
│   │                   └── org.mockito.plugins.MockMaker
│   └── payment-service/
│       ├── .dockerignore
│       ├── Dockerfile
│       ├── pom.xml
│       └── src/
│           ├── main/
│           │   ├── java/
│           │   │   └── com/
│           │   │       └── madlanga/
│           │   │           └── lab/
│           │   │               └── payment/
│           │   │                   ├── PaymentApplication.java
│           │   │                   ├── client/
│           │   │                   │   └── CustomerClient.java
│           │   │                   ├── config/
│           │   │                   │   ├── CorrelationIdFilter.java
│           │   │                   │   └── HttpClientConfig.java
│           │   │                   ├── controller/
│           │   │                   │   └── PaymentController.java
│           │   │                   ├── dto/
│           │   │                   │   ├── ApiError.java
│           │   │                   │   ├── CustomerValidation.java
│           │   │                   │   ├── DocumentReceipt.java
│           │   │                   │   ├── DownstreamRequest.java
│           │   │                   │   ├── PaymentRequest.java
│           │   │                   │   └── PaymentResult.java
│           │   │                   ├── exception/
│           │   │                   │   ├── ApiExceptionHandler.java
│           │   │                   │   └── DownstreamException.java
│           │   │                   └── service/
│           │   │                       └── PaymentService.java
│           │   └── resources/
│           │       └── application.yml
│           └── test/
│               ├── java/
│               │   └── com/
│               │       └── madlanga/
│               │           └── lab/
│               │               └── payment/
│               │                   └── PaymentApplicationTests.java
│               └── resources/
│                   └── mockito-extensions/
│                       └── org.mockito.plugins.MockMaker
├── scripts/
│   ├── BootstrapCapturedTopology.java
│   ├── run-phase11-e2e.py
│   ├── run-stage2-e2e.py
│   ├── run-stage3-e2e.py
│   ├── run-stage4-e2e.py
│   ├── verify-observability.py
│   ├── verify-observability.sh
│   └── verify-phase2.py
└── traffic-generator/
    ├── .dockerignore
    ├── Dockerfile
    ├── test_traffic.py
    └── traffic.py
```

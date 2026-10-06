# Project structure

The engine is organized by capability first and layer second. The four Java modules build independently: Blast Radius API and payment/customer/document lab services. React/Vite provides the incident dashboard; Python supplies synthetic traffic and acceptance runners.

## Main Java packages

```text
com.madlanga.blastradius/
├── diagnosis/
│   ├── application/
│   │   └── port
│   ├── domain
│   └── infrastructure
├── incident/
│   ├── api/
│   │   └── dto
│   ├── application/
│   │   └── port
│   ├── domain/
│   │   └── containment
│   └── infrastructure/
│       ├── containment
│       └── persistence
├── lifecycle/
│   ├── application
│   └── infrastructure
├── shared/
│   ├── config
│   └── sanitization
├── telemetry/
│   ├── application/
│   │   └── port
│   ├── domain
│   └── infrastructure/
│       ├── config
│       ├── health
│       ├── loki
│       ├── prometheus
│       └── tempo
└── topology/
    ├── application/
    │   └── port
    ├── domain
    └── infrastructure/
        ├── config
        └── persistence
```

Only packages containing actual code are listed. `telemetry.application` currently contains its acquisition port; it does not need an empty orchestration layer. Lifecycle has application/scheduling code and does not duplicate incident domain concepts. Shared contains sanitization and OpenAPI configuration only.

## Important class ownership

| Class | Package |
|---|---|
| `IncidentAnalysisService` | `incident.application` |
| `IncidentSeverityCalculator` | `incident.domain` |
| `FailureExperimentAssessmentService` | `incident.domain.containment` |
| `JdbcIncidentRepository` | `incident.infrastructure.persistence` |
| `DeterministicGraphEngine` | `topology.domain` |
| `FileTopologyStore` | `topology.infrastructure.persistence` |
| `AiDiagnosisService`, `DiagnosisContextFactory`, `StoredAnalysisDiagnosisContextMapper` | `diagnosis.application` |
| `DeterministicDiagnosisAdapter`, `GeminiDiagnosisAdapter` | `diagnosis.infrastructure` |
| `IncidentLifecycleService` | `lifecycle.application` |
| `IncidentLifecycleMonitor` | `lifecycle.infrastructure` |

Ports live in their owning capability's `application.port`. Controllers/HTTP DTOs live in `incident.api`/`incident.api.dto`. Provider models remain in vendor infrastructure packages. JDBC/Flyway, Docker, observability, scripts, fixtures and frontend assets retain their existing paths. Mock services keep their small controller/service/dto/exception/client/config/persistence/fault structure.

See [DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) for placement rules and [CAPABILITY_REFACTOR.md](docs/CAPABILITY_REFACTOR.md) for every class move, decisions, tests and remaining debt. The earlier audit/report are historical records.

Maven `target`, frontend build output and Python bytecode are generated files. No `.gitkeep` placeholders or empty source packages are needed. Runtime mount/data directories remain Compose-managed.

## Complete source tree

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
│       │   │               ├── diagnosis/
│       │   │               │   ├── application/
│       │   │               │   │   ├── AiDiagnosisService.java
│       │   │               │   │   ├── DiagnosisContextFactory.java
│       │   │               │   │   ├── StoredAnalysisDiagnosisContextMapper.java
│       │   │               │   │   └── port/
│       │   │               │   │       └── AiDiagnosisPort.java
│       │   │               │   ├── domain/
│       │   │               │   │   ├── AiDiagnosis.java
│       │   │               │   │   └── DiagnosisContext.java
│       │   │               │   └── infrastructure/
│       │   │               │       ├── AiDiagnosisConfiguration.java
│       │   │               │       ├── DeterministicDiagnosisAdapter.java
│       │   │               │       ├── GeminiDiagnosisAdapter.java
│       │   │               │       └── GeminiProperties.java
│       │   │               ├── incident/
│       │   │               │   ├── api/
│       │   │               │   │   ├── BlastRadiusController.java
│       │   │               │   │   ├── IncidentHistoryController.java
│       │   │               │   │   └── dto/
│       │   │               │   │       ├── AnalyzeIncidentRequest.java
│       │   │               │   │       ├── ErrorResponse.java
│       │   │               │   │       ├── IncidentResponse.java
│       │   │               │   │       └── ResolveIncidentRequest.java
│       │   │               │   ├── application/
│       │   │               │   │   ├── IncidentAnalysisService.java
│       │   │               │   │   └── port/
│       │   │               │   │       ├── FailureExperimentProvider.java
│       │   │               │   │       └── IncidentRepository.java
│       │   │               │   ├── domain/
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
│       │   │               │   │   ├── SeverityLevel.java
│       │   │               │   │   └── containment/
│       │   │               │   │       ├── ContainmentStatus.java
│       │   │               │   │       ├── ExperimentAssessment.java
│       │   │               │   │       ├── FailureExperiment.java
│       │   │               │   │       └── FailureExperimentAssessmentService.java
│       │   │               │   └── infrastructure/
│       │   │               │       ├── containment/
│       │   │               │       │   └── LocalFailureExperimentProvider.java
│       │   │               │       └── persistence/
│       │   │               │           └── JdbcIncidentRepository.java
│       │   │               ├── lifecycle/
│       │   │               │   ├── application/
│       │   │               │   │   └── IncidentLifecycleService.java
│       │   │               │   └── infrastructure/
│       │   │               │       └── IncidentLifecycleMonitor.java
│       │   │               ├── shared/
│       │   │               │   ├── config/
│       │   │               │   │   └── OpenApiConfiguration.java
│       │   │               │   └── sanitization/
│       │   │               │       ├── BuiltInRedactionRules.java
│       │   │               │       ├── RedactionPlaceholders.java
│       │   │               │       ├── RedactionRule.java
│       │   │               │       └── TelemetrySanitizer.java
│       │   │               ├── telemetry/
│       │   │               │   ├── application/
│       │   │               │   │   └── port/
│       │   │               │   │       └── TelemetryProvider.java
│       │   │               │   ├── domain/
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
│       │   │               │   └── infrastructure/
│       │   │               │       ├── LocalTelemetryProvider.java
│       │   │               │       ├── config/
│       │   │               │       │   └── TelemetryAdapterConfig.java
│       │   │               │       ├── health/
│       │   │               │       │   ├── ActuatorHealthAdapter.java
│       │   │               │       │   ├── ActuatorHealthProperties.java
│       │   │               │       │   └── ActuatorHealthResponse.java
│       │   │               │       ├── loki/
│       │   │               │       │   ├── LokiLogAdapter.java
│       │   │               │       │   ├── LokiLogLine.java
│       │   │               │       │   ├── LokiProperties.java
│       │   │               │       │   └── LokiResponse.java
│       │   │               │       ├── prometheus/
│       │   │               │       │   ├── PrometheusMetricsAdapter.java
│       │   │               │       │   ├── PrometheusProperties.java
│       │   │               │       │   └── PrometheusResponse.java
│       │   │               │       └── tempo/
│       │   │               │           ├── TempoProperties.java
│       │   │               │           ├── TempoResponse.java
│       │   │               │           ├── TempoSearchResponse.java
│       │   │               │           └── TempoTraceAdapter.java
│       │   │               └── topology/
│       │   │                   ├── application/
│       │   │                   │   └── port/
│       │   │                   │       ├── DependencyTopologyProvider.java
│       │   │                   │       ├── RuntimeSpanSource.java
│       │   │                   │       └── TopologyStore.java
│       │   │                   ├── domain/
│       │   │                   │   ├── ComponentNode.java
│       │   │                   │   ├── ComponentType.java
│       │   │                   │   ├── DependencyEdge.java
│       │   │                   │   ├── DependencyTopology.java
│       │   │                   │   ├── DeterministicGraphEngine.java
│       │   │                   │   ├── GraphAnalysisResult.java
│       │   │                   │   ├── ImpactClassification.java
│       │   │                   │   ├── RetainedTopology.java
│       │   │                   │   └── TheoreticalImpact.java
│       │   │                   └── infrastructure/
│       │   │                       ├── RetainedTopologyProvider.java
│       │   │                       ├── TraceDiscoveredTopologyProvider.java
│       │   │                       ├── config/
│       │   │                       │   └── TopologyConfig.java
│       │   │                       └── persistence/
│       │   │                           └── FileTopologyStore.java
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
│           │               ├── ArchitectureBoundaryTest.java
│           │               ├── BlastRadiusApplicationTests.java
│           │               ├── diagnosis/
│           │               │   ├── application/
│           │               │   │   ├── AiDiagnosisServiceTest.java
│           │               │   │   ├── DiagnosisContextFactoryTest.java
│           │               │   │   └── StoredAnalysisDiagnosisContextMapperTest.java
│           │               │   └── infrastructure/
│           │               │       └── GeminiDiagnosisAdapterTest.java
│           │               ├── incident/
│           │               │   ├── api/
│           │               │   │   └── BlastRadiusControllerTest.java
│           │               │   ├── application/
│           │               │   │   └── IncidentAnalysisServiceTest.java
│           │               │   └── domain/
│           │               │       ├── IncidentSeverityCalculatorTest.java
│           │               │       └── containment/
│           │               │           └── FailureExperimentAssessmentServiceTest.java
│           │               ├── lifecycle/
│           │               │   └── application/
│           │               │       └── IncidentLifecycleServiceTest.java
│           │               ├── shared/
│           │               │   └── sanitization/
│           │               │       └── TelemetrySanitizerTest.java
│           │               ├── telemetry/
│           │               │   ├── domain/
│           │               │   │   ├── EvidenceProvenanceTest.java
│           │               │   │   ├── HealthEvidenceTest.java
│           │               │   │   ├── LogEvidenceTest.java
│           │               │   │   ├── MetricEvidenceTest.java
│           │               │   │   ├── SpanEvidenceTest.java
│           │               │   │   ├── TelemetryBundleTest.java
│           │               │   │   └── TelemetryQueryTest.java
│           │               │   └── infrastructure/
│           │               │       ├── LocalTelemetryProviderLiveIT.java
│           │               │       ├── LocalTelemetryProviderTest.java
│           │               │       ├── health/
│           │               │       │   └── ActuatorHealthAdapterTest.java
│           │               │       ├── loki/
│           │               │       │   └── LokiLogAdapterTest.java
│           │               │       ├── prometheus/
│           │               │       │   └── PrometheusMetricsAdapterTest.java
│           │               │       └── tempo/
│           │               │           ├── CapturedTempoRegressionTest.java
│           │               │           └── TempoTraceAdapterTest.java
│           │               └── topology/
│           │                   ├── domain/
│           │                   │   ├── DeterministicGraphEngineTest.java
│           │                   │   └── TopologyDomainTest.java
│           │                   └── infrastructure/
│           │                       ├── RetainedTopologyProviderTest.java
│           │                       ├── TraceDiscoveredTopologyProviderLiveIT.java
│           │                       └── TraceDiscoveredTopologyProviderTest.java
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
│   ├── CAPABILITY_REFACTOR.md
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

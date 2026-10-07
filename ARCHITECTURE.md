# Repository Architecture — Current Code Map

This document describes the current MadlangaAI Blast Radius Engine after the feature-first package refactor.

The central design rule is:

> **Topology calculates potential impact. Runtime telemetry proves observed impact. AI explains sanitized deterministic evidence.**

## 1. Repository layout

```text
blast-radius-engine/
├── blast-radius-api/          Spring Boot blast-radius engine
├── frontend/                  React/Vite dashboard
├── mock-services/
│   ├── payment-service/
│   ├── customer-service/
│   └── document-service/
├── traffic-generator/         continuous synthetic business traffic
├── infrastructure/
│   └── observability/         OTel, Loki, Prometheus and Tempo configuration
├── scripts/
│   ├── run-phase11-e2e.py
│   └── run-stage2-e2e.py
├── fixtures/
├── docker-compose.yml
├── README.md
├── SETUP.md
└── ARCHITECTURE.md
```

The repository intentionally keeps three Markdown documents: README, SETUP and ARCHITECTURE.

## 2. Local runtime architecture

```text
traffic-generator
      |
      v
payment-service
      |
      v
customer-service
      |
      v
document-service
      |
      v
postgres
```

The monitored chain runs beside:

```text
blast-radius-api ---> blast-radius-db
       |
       +-- Loki
       +-- Prometheus
       +-- Tempo
       +-- Actuator health endpoints

frontend ---> blast-radius-api
```

`postgres` is the monitored synthetic application database. `blast-radius-db` is the engine's own incident database.

## 3. Blast Radius API package structure

Production Java root:

```text
blast-radius-api/src/main/java/com/madlanga/blastradius/
├── BlastRadiusApplication.java
├── diagnosis/
│   ├── config/
│   │   ├── DiagnosisConfig.java
│   │   └── GeminiProperties.java
│   ├── dto/
│   │   ├── DiagnosisRequest.java
│   │   └── DiagnosisResponse.java
│   ├── mapper/
│   │   └── DiagnosisRequestMapper.java
│   ├── provider/
│   │   ├── DiagnosisProvider.java
│   │   ├── DeterministicDiagnosisProvider.java
│   │   └── GeminiDiagnosisProvider.java
│   └── service/
│       └── DiagnosisService.java
├── incident/
│   ├── controller/
│   │   ├── IncidentController.java
│   │   └── IncidentHistoryController.java
│   ├── dto/
│   │   ├── AnalyzeIncidentRequest.java
│   │   ├── ErrorResponse.java
│   │   ├── IncidentResponse.java
│   │   └── ResolveIncidentRequest.java
│   ├── model/
│   │   ├── ComponentImpact.java
│   │   ├── EvidenceSignal.java
│   │   ├── IncidentAnalysis.java
│   │   ├── IncidentSeverity.java
│   │   ├── OriginAssessment.java
│   │   └── PersistedIncident.java
│   ├── repository/
│   │   ├── IncidentRepository.java
│   │   └── JdbcIncidentRepository.java
│   └── service/
│       ├── IncidentAnalysisService.java
│       └── IncidentSeverityService.java
├── lifecycle/
│   ├── scheduler/
│   │   └── IncidentLifecycleScheduler.java
│   └── service/
│       └── IncidentLifecycleService.java
├── shared/
│   └── ...
├── telemetry/
│   ├── config/
│   │   └── TelemetryConfig.java
│   ├── model/
│   │   ├── CoverageStatus.java
│   │   ├── EvidenceFamily.java
│   │   ├── EvidenceProvenance.java
│   │   ├── HealthEvidence.java
│   │   ├── HealthState.java
│   │   ├── LogEvidence.java
│   │   ├── MetricEvidence.java
│   │   ├── SpanEvidence.java
│   │   ├── SpanKind.java
│   │   ├── SpanStatus.java
│   │   ├── TelemetryBundle.java
│   │   ├── TelemetryCoverage.java
│   │   └── TelemetryQuery.java
│   └── provider/
│       ├── LocalTelemetryProvider.java
│       ├── TelemetryProvider.java
│       ├── health/ActuatorHealthAdapter.java
│       ├── loki/LokiLogAdapter.java
│       ├── prometheus/PrometheusMetricsAdapter.java
│       └── tempo/TempoTraceAdapter.java
└── topology/
    ├── config/
    │   └── TopologyConfig.java
    ├── model/
    │   ├── BlastRadiusResult.java
    │   ├── ComponentNode.java
    │   ├── ComponentType.java
    │   ├── DependencyEdge.java
    │   ├── DependencyTopology.java
    │   ├── RetainedTopology.java
    │   └── TheoreticalImpact.java
    ├── provider/
    │   ├── DependencyTopologyProvider.java
    │   └── RuntimeSpanProvider.java
    ├── repository/
    │   ├── FileTopologyRepository.java
    │   └── TopologyRepository.java
    └── service/
        ├── BlastRadiusGraphService.java
        ├── TopologyService.java
        └── TraceTopologyService.java
```

The package convention is intentionally familiar Spring naming: `controller`, `service`, `dto`, `model`, `repository`, `provider`, `mapper`, `config` and `scheduler`. A feature only gets folders it actually needs.

The previous `api/application/domain/infrastructure/port` package structure is no longer used.

## 4. Diagnosis

`DiagnosisService` coordinates advisory diagnosis after deterministic incident analysis.

- `DiagnosisRequest` — bounded provider input.
- `DiagnosisResponse` — provider-neutral diagnosis result.
- `DiagnosisRequestMapper` — maps fresh or persisted incident information into diagnosis context.
- `DiagnosisProvider` — replaceable diagnosis boundary.
- `DeterministicDiagnosisProvider` — local fallback.
- `GeminiDiagnosisProvider` — optional Gemini HTTP integration.
- `DiagnosisConfig` / `GeminiProperties` — Spring/provider configuration.

`GeminiDiagnosisProvider` tries the configured primary model first and can continue to configured fallback models for model unavailability, request timeout, transport failure and retryable HTTP failures. Authentication/authorization and malformed-request failures fail the Gemini provider directly. `DiagnosisService` then preserves the deterministic provider as the final application fallback.

Current local defaults:

```text
primary Gemini model   -> gemini-3.5-flash-lite
fallback Gemini model  -> gemini-3.5-flash
provider failure       -> deterministic diagnosis
```

Diagnosis does not own blast-radius calculation or severity.

## 5. Incident

`IncidentAnalysisService` is the main deterministic orchestration service. It:

1. requests telemetry,
2. obtains dependency topology,
3. correlates evidence by component,
4. assesses the likely origin,
5. invokes `BlastRadiusGraphService`,
6. distinguishes observed, theoretical-only and unexpected impact,
7. builds the evidence timeline,
8. invokes `IncidentSeverityService`,
9. returns `IncidentAnalysis`.

Important models:

- `ComponentImpact` contains nested `State`.
- `OriginAssessment` contains nested `Confidence`.
- `IncidentSeverity` contains nested `Level`.
- `PersistedIncident` contains nested `Status`.

These nested enums replaced unnecessary standalone enum files.

Persistence:

- `IncidentRepository` — persistence interface.
- `JdbcIncidentRepository` — JDBC implementation against `blast-radius-db`.

There is no JPA layer in the current engine.

## 6. Lifecycle

- `IncidentLifecycleScheduler` — scheduled trigger for proactive evaluation.
- `IncidentLifecycleService` — creates/updates the ACTIVE incident and applies guarded recovery.

The lifecycle reuses one incident UUID while the same incident remains active. Automatic recovery requires healthy windows and adequate telemetry coverage. Missing telemetry cannot be treated as proof of recovery.

## 7. Topology

Topology represents **potential** failure propagation.

### Models

- `ComponentNode` — canonical component.
- `ComponentType` — component category.
- `DependencyEdge` — directed dependent-to-dependency edge.
- `DependencyTopology` — validated immutable graph.
- `TheoreticalImpact` — affected component, distance, path and nested `Classification`.
- `BlastRadiusResult` — graph traversal result.
- `RetainedTopology` — durable learned snapshot with nested persistence records.

### Providers/repository

- `DependencyTopologyProvider` — supplies topology to consumers.
- `RuntimeSpanProvider` — supplies normalized spans for topology learning.
- `TopologyRepository` — retained-topology persistence contract.
- `FileTopologyRepository` — local durable file implementation.

### Services

- `TraceTopologyService` — derives dependency relationships from normalized spans.
- `TopologyService` — learns/merges retained topology, reloads it and expires stale observations.
- `BlastRadiusGraphService` — deterministic graph traversal.

Stored edges use:

```text
dependent -> dependency

payment-service -> customer-service
customer-service -> document-service
document-service -> postgres
```

Failure propagation is evaluated in reverse:

```text
postgres -> document-service -> customer-service -> payment-service
```

The graph service uses deterministic breadth-first traversal, minimum distance/path and stable lexical tie-breaking.

## 8. Telemetry

`TelemetryProvider` is the normalized evidence boundary. `LocalTelemetryProvider` combines the local adapters.

Models cover:

- evidence provenance/family,
- logs,
- metrics,
- spans,
- health,
- coverage,
- query scope,
- normalized telemetry bundles.

Adapters:

- `LokiLogAdapter` — Loki query/normalization.
- `PrometheusMetricsAdapter` — Prometheus query/normalization.
- `TempoTraceAdapter` — Tempo query/trace normalization.
- `ActuatorHealthAdapter` — Spring Actuator health normalization.

`TelemetryConfig` contains the Spring wiring and adapter property types. Small provider-specific response structures are kept with the adapters rather than spread across standalone DTO files.

## 9. Shared

`shared` contains code that is genuinely cross-feature, including telemetry sanitization/redaction and shared application configuration. It should not become a dumping ground for feature-specific code.

The shared package is the next cleanup/audit area; its behavior must remain compatible with the already-green API and E2E validation.

## 10. Database and migrations

The Blast Radius API uses Spring JDBC and Flyway with PostgreSQL.

The diagnostic database stores incident lifecycle/history independently from the monitored application's PostgreSQL database. Flyway owns the diagnostic schema, including protection for the single-active-incident rule.

## 11. Synthetic services

### payment-service

Receives synthetic payment requests and calls customer-service.

Important areas:

- controller — inbound payment API.
- service — payment flow.
- client — customer-service HTTP dependency.
- dto — request/response contracts.
- exception — downstream/API error mapping.
- config — HTTP client and correlation IDs.

### customer-service

Receives validation requests and calls document-service.

Its downstream timeout is important for Stage 2 latency testing.

### document-service

Calls the monitored PostgreSQL database and hosts the localhost-only Stage 2 fault-injection endpoint.

Important areas:

- `DocumentService` — normal document flow.
- `DocumentRepository` — JDBC database access.
- `FaultInjectionController` — lab fault control.
- `FaultInjectionService` — bounded synthetic degradation.
- `FaultMode` / `FaultConfig` — supported fault state.

## 12. Observability

```text
synthetic Java services
        |
 OpenTelemetry Java agent
        |
 OpenTelemetry Collector
   |        |        |
   v        v        v
 Loki   Prometheus  Tempo
   \        |        /
    \       |       /
     LocalTelemetryProvider
```

Configuration lives under:

```text
infrastructure/observability/
├── logging/loki.yml
├── otel/collector.yml
├── otel/javaagent.properties
├── prometheus/prometheus.yml
└── tracing/tempo.yml
```

## 13. Frontend

`frontend` is a React/Vite dashboard served locally through its container. It reads incident state from the Blast Radius API and can request optional diagnosis. Its five-second polling refresh preserves an explicitly selected incident when that incident remains in the current stage, and diagnosis results are cached by incident for the current browser session. This prevents polling from jumping the operator back to the newest incident or clearing a generated diagnosis. The frontend does not calculate topology, impact or severity.

## 14. Traffic generator

`traffic-generator/traffic.py` continuously sends requests into payment-service so the complete synthetic chain produces runtime telemetry without manual traffic.

## 15. E2E scripts

### `scripts/run-phase11-e2e.py`

Stage 1 / Phase 11 validates:

- healthy baseline,
- PostgreSQL outage,
- document-service outage,
- customer-service outage,
- payment-service outage,
- partial-observability protection,
- ACTIVE -> RESOLVED lifecycle,
- stable incident UUID,
- dependency direction.

Current validated result after the package refactor: **6/6 PASS**.

### `scripts/run-stage2-e2e.py`

Stage 2 keeps document-service running and injects:

- continuous HTTP 500,
- intermittent HTTP 500,
- latency,
- database-connectivity failure.

It requires appropriate runtime evidence families and verifies recovery after the injected fault is reset.

Target: **4/4 PASS**.

## 16. Current test layout

```text
blast-radius-api/src/test/java/com/madlanga/blastradius/
├── ArchitectureBoundaryTest.java
├── BlastRadiusApplicationTests.java
├── diagnosis/
│   ├── mapper/
│   ├── provider/
│   └── service/
├── incident/
│   ├── controller/
│   └── service/
├── lifecycle/
│   └── service/
├── shared/
│   └── sanitization/
├── telemetry/
│   ├── model/
│   └── provider/
└── topology/
    ├── model/
    │   └── TopologyModelTest.java
    └── service/
        ├── BlastRadiusGraphServiceTest.java
        ├── TopologyServiceTest.java
        ├── TraceTopologyServiceLiveIT.java
        └── TraceTopologyServiceTest.java
```

Current Maven validation: **189 tests, 0 failures, 0 errors, 0 skipped**.

## 17. Main execution flow

```text
traffic-generator
   -> PaymentController -> PaymentService -> CustomerClient
   -> CustomerController -> CustomerService -> DocumentClient
   -> DocumentController -> DocumentService -> DocumentRepository -> postgres

runtime telemetry
   -> OTel / Loki / Prometheus / Tempo / Actuator
   -> LocalTelemetryProvider
   -> IncidentAnalysisService

runtime spans
   -> RuntimeSpanProvider
   -> TraceTopologyService
   -> TopologyService
   -> TopologyRepository / FileTopologyRepository
   -> DependencyTopologyProvider
   -> IncidentAnalysisService

IncidentAnalysisService
   -> BlastRadiusGraphService
   -> IncidentSeverityService
   -> IncidentAnalysis
   -> IncidentLifecycleService
   -> JdbcIncidentRepository
   -> IncidentHistoryController
   -> frontend

IncidentAnalysis / persisted incident
   -> DiagnosisRequestMapper
   -> DiagnosisService
   -> DeterministicDiagnosisProvider or GeminiDiagnosisProvider
```

That is the current architectural spine of the repository.

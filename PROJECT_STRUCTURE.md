# Project Structure

This file is a quick map for engineers opening the repository for the first time. It describes where the important code lives and what each area is responsible for.

~~~text
blast-radius-engine/
├── ABOUT.md
├── SETUP.md
├── PROJECT_STRUCTURE.md
├── README.md
├── CODEX.md
├── docker-compose.yml
├── .env.example
├── blast-radius-api/
├── frontend/
├── mock-services/
├── traffic-generator/
├── infrastructure/
├── scripts/
├── fixtures/
└── docs/
~~~

## Root files

| Path | Purpose |
|---|---|
| README.md | Project landing page: purpose, architecture, current capabilities and quick start. |
| ABOUT.md | Product-level explanation of Blast Radius and how real applications integrate with it. |
| SETUP.md | Local prerequisites, startup, testing, outage and recovery walkthrough. |
| PROJECT_STRUCTURE.md | This repository map. |
| CODEX.md | Engineering constraints and architectural rules used while developing the project. |
| docker-compose.yml | Starts the complete local lab: API, UI, mock application, databases and observability stack. |
| .env.example | Safe template for local environment variables. |

## blast-radius-api

The main Spring Boot Blast Radius service.

~~~text
blast-radius-api/
├── Dockerfile
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/madlanga/blastradius/
    │   │   ├── api/
    │   │   ├── adapters/
    │   │   ├── config/
    │   │   ├── domain/
    │   │   ├── ports/
    │   │   ├── sanitization/
    │   │   └── service/
    │   └── resources/
    └── test/
~~~

### api/
REST boundary. Contains analysis/history/diagnosis endpoints, request models and OpenAPI configuration.

Important controllers:
- BlastRadiusController — deterministic blast-radius analysis API.
- IncidentHistoryController — ACTIVE/RESOLVED incident history and on-demand AI diagnosis.
- OpenApiConfiguration — API documentation metadata.

### domain/
Technology-neutral business models. This is the core vocabulary and should not depend on Loki, Prometheus, Tempo, Gemini or the frontend.

Subareas:
- evidence/ — normalized logs, metrics, traces, health, coverage and provenance.
- topology/ — components, dependency edges, paths and theoretical impact.
- incident/ — origin, observed impact, severity, status and persisted incident models.
- diagnosis/ — sanitized AI diagnosis input/output models.
- experiment/ — legacy/local controlled-failure assessment models retained for historical/testing compatibility; not the current product UI.

### ports/
Interfaces between the deterministic core and external systems.

Key ports:
- TelemetryProvider — supplies normalized runtime evidence.
- DependencyTopologyProvider — supplies the application dependency graph.
- IncidentRepository — persists incident lifecycle state.
- AiDiagnosisPort — optional AI explanation boundary.
- TopologyStore / RuntimeSpanSource — runtime topology retention/discovery boundaries.
- FailureExperimentProvider — local/test experiment metadata boundary.

These ports are where future MadlangaAI/enterprise adapters plug in.

### adapters/
Provider-specific implementations.

- telemetry/loki — Loki log queries and normalization.
- telemetry/prometheus — Prometheus metric queries.
- telemetry/tempo — Tempo trace queries and runtime trace evidence.
- telemetry/health — health endpoint collection.
- telemetry/LocalTelemetryProvider — combines all telemetry families behind one port.
- topology — retained/runtime trace topology providers.
- persistence — JDBC incident persistence and file-backed retained topology.
- ai — Gemini adapter/configuration.
- experiment — local controlled-failure fixture/provider.

### service/
Deterministic application logic.

Important classes:
- IncidentAnalysisService — orchestrates telemetry, topology, correlation and final analysis.
- DeterministicGraphEngine — calculates theoretical dependency impact and paths.
- IncidentSeverityCalculator — deterministic incident severity.
- IncidentLifecycleService — creates/updates/resolves persisted incidents.
- IncidentLifecycleMonitor — scheduled proactive detection and guarded automatic recovery.
- AiDiagnosisService — invokes optional diagnosis after deterministic analysis.
- DiagnosisContextFactory — builds sanitized AI context.
- StoredAnalysisDiagnosisContextMapper — rebuilds diagnosis context from persisted analyses.
- FailureExperimentAssessmentService — legacy/local experiment assessment logic.

### sanitization/
Redaction rules applied before evidence reaches persistence, export, logs or AI context.

### config/
Spring configuration for telemetry/topology and other adapters.

### resources/
- application.yml — runtime configuration, telemetry endpoints, lifecycle settings and optional Gemini configuration.
- db/migration/ — Flyway schema for the Blast Radius diagnostic database.

### test/
Unit/integration/regression tests for the API, deterministic engine, telemetry adapters, persistence and lifecycle behavior.

## frontend

React/Vite Phase 10 dashboard.

~~~text
frontend/
├── Dockerfile
├── nginx.conf
├── package.json
├── index.html
└── src/
    ├── main.jsx
    └── styles.css
~~~

- main.jsx — incident list/detail, KPIs, topology/evidence UI, live polling and AI diagnosis action.
- styles.css — responsive dashboard styling.
- nginx.conf — serves the built frontend and proxies /api to blast-radius-api.
- Dockerfile — builds the Vite app and serves it with Nginx.

The frontend displays persisted deterministic results; it does not calculate blast radius itself.

## mock-services

Synthetic monitored application used to prove the engine.

~~~text
mock-services/
├── payment-service/
├── customer-service/
└── document-service/
~~~

Flow:

~~~text
payment-service
      |
customer-service
      |
document-service
      |
postgres
~~~

Each service has its own Maven project, Dockerfile, application configuration and tests.

- payment-service — public synthetic transaction entry point; calls customer-service.
- customer-service — validates synthetic customer/reference data; calls document-service.
- document-service — persists synthetic documents in PostgreSQL.

They are deliberately simple. Their purpose is to generate realistic dependency failures and telemetry, not to represent MadlangaAI business functionality.

## traffic-generator

Continuously sends synthetic requests to payment-service. This provides canary-style traffic so failures can generate evidence without waiting for a human user to report an issue.

## infrastructure

Local platform configuration.

~~~text
infrastructure/
├── database/postgres/
└── observability/
    ├── logging/loki.yml
    ├── prometheus/prometheus.yml
    ├── tracing/tempo.yml
    └── otel/
        ├── collector.yml
        └── javaagent.properties
~~~

- Loki — local log backend.
- Prometheus — metrics collection/query backend.
- Tempo — distributed tracing backend.
- OpenTelemetry Collector — receives/forwards telemetry.
- Java agent properties — automatic instrumentation settings for synthetic Java services.

## scripts

Verification/bootstrap utilities.

- verify-phase2.py — original synthetic business-flow/failure verification.
- verify-observability.py / verify-observability.sh — checks observability evidence.
- BootstrapCapturedTopology.java — utility for topology bootstrap/testing.

These are support/verification utilities, not production Blast Radius runtime code.

## fixtures

Synthetic request/test data used by the local lab and verification scripts.

## docs

Only durable engineering documentation should live here.

- ARCHITECTURE.md — detailed internal architecture and boundaries.
- INTEGRATION.md — MadlangaAI/enterprise integration design and outstanding contracts.
- DECISIONS.md — architectural decision records/history.

For normal onboarding, start with:
1. README.md
2. ABOUT.md
3. SETUP.md
4. PROJECT_STRUCTURE.md

Then use docs/ only when deeper engineering detail is required.

## Runtime data

Some topology and database state is generated at runtime through Docker volumes or configured local data directories. Generated runtime state should not be treated as source code.

## Where to make common changes

| Change | Start here |
|---|---|
| Blast-radius calculation | blast-radius-api/.../service/DeterministicGraphEngine.java |
| Origin/correlation analysis | blast-radius-api/.../service/IncidentAnalysisService.java |
| Severity | blast-radius-api/.../service/IncidentSeverityCalculator.java |
| Automatic detection/recovery | blast-radius-api/.../service/IncidentLifecycleMonitor.java and IncidentLifecycleService.java |
| New telemetry vendor | blast-radius-api/.../ports/TelemetryProvider.java + new adapter |
| New topology source | blast-radius-api/.../ports/DependencyTopologyProvider.java + new adapter |
| AI provider | blast-radius-api/.../ports/AiDiagnosisPort.java + adapters/ai |
| Dashboard | frontend/src/ |
| Local containers | docker-compose.yml |
| Observability configuration | infrastructure/observability/ |
| New database schema | blast-radius-api/src/main/resources/db/migration/ |
| Local test application behavior | mock-services/ |

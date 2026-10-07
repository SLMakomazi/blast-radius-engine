# MadlangaAI Blast Radius Engine

MadlangaAI Blast Radius Engine is a deterministic incident-analysis service for **MadlangaAI Phase 4**. It combines dependency topology with runtime telemetry to identify a likely failure origin, calculate what could be affected, prove what was actually affected, score severity, persist the incident and track recovery.

> **Topology calculates potential impact. Runtime telemetry proves observed impact. AI explains sanitized evidence.**

## Current validation status

The refactored API currently passes:

- Maven: **189 tests, 0 failures, 0 errors, 0 skipped**
- Stage 1 / Phase 11 E2E: **6/6 scenarios passed**
- Stage 2 target: **4/4 degraded-but-running scenarios**

The latest Stage 1 run validated the healthy baseline, PostgreSQL outage, document-service outage, customer-service outage, payment-service outage and partial-observability protection. Incidents reused the same UUID through ACTIVE -> RESOLVED recovery.

## Local proof environment

```text
traffic-generator
      |
payment-service
      |
customer-service
      |
document-service
      |
postgres
```

The lab also runs:

- `blast-radius-api` — Spring Boot analysis/lifecycle API
- `blast-radius-db` — incident persistence database
- `frontend` — React/Vite dashboard
- `otel-collector` — telemetry routing
- `loki` — logs
- `prometheus` — metrics
- `tempo` — traces

The synthetic services exist only to generate controlled, realistic runtime failures and degradation.

## How analysis works

```text
logs ------> Loki -----------+
metrics ---> Prometheus -----+
traces ----> Tempo ----------+--> LocalTelemetryProvider --> normalized evidence
health ----> Actuator -------+                                |
                                                               v
Tempo spans --> topology discovery/retention --> DependencyTopology
                                                               |
                         +-------------------------------------+
                         v
                 IncidentAnalysisService
                         |
             +-----------+------------+
             |                        |
      BlastRadiusGraphService   IncidentSeverityService
             |                        |
             +-----------+------------+
                         v
                  IncidentAnalysis
                         |
                IncidentLifecycleService
                         |
                JdbcIncidentRepository
                         |
                 blast-radius-db
                         |
                  REST + dashboard
                         |
              optional AI diagnosis
```

AI does **not** calculate blast radius, choose the origin, classify observed impact, score severity or control incident lifecycle. Gemini is an optional advisory provider after deterministic analysis.

## Current Spring Boot package structure

The API uses straightforward feature-first Spring packages. It does **not** use `api/application/domain/infrastructure/port` layering.

```text
com.madlanga.blastradius/
├── diagnosis/
│   ├── config/
│   ├── dto/
│   ├── mapper/
│   ├── provider/
│   └── service/
├── incident/
│   ├── controller/
│   ├── dto/
│   ├── model/
│   ├── repository/
│   └── service/
├── lifecycle/
│   ├── scheduler/
│   └── service/
├── shared/
├── telemetry/
│   ├── config/
│   ├── model/
│   └── provider/
│       ├── health/
│       ├── loki/
│       ├── prometheus/
│       └── tempo/
└── topology/
    ├── config/
    ├── model/
    ├── provider/
    ├── repository/
    └── service/
```

Folders are created only when the feature needs them.

## Topology

Topology answers: **If this component fails, what could be affected?**

Important classes:

- `topology/provider/DependencyTopologyProvider.java` — topology boundary used by incident analysis.
- `topology/provider/RuntimeSpanProvider.java` — runtime-span source used for topology learning.
- `topology/service/TraceTopologyService.java` — converts normalized spans into dependency topology.
- `topology/service/TopologyService.java` — learns, retains, reloads and expires topology knowledge.
- `topology/service/BlastRadiusGraphService.java` — deterministic reverse graph traversal from an origin to affected dependents.
- `topology/repository/TopologyRepository.java` — retained-topology persistence contract.
- `topology/repository/FileTopologyRepository.java` — local file-backed implementation.
- `topology/model/BlastRadiusResult.java` and `TheoreticalImpact.java` — deterministic graph result.

Dependency edges are stored as **dependent -> dependency**. Blast-radius propagation walks the reverse direction: **failed dependency -> affected dependents**.

## Telemetry

Telemetry answers: **What was actually affected during this incident?**

`TelemetryProvider` returns normalized logs, metrics, traces, health and coverage. `LocalTelemetryProvider` combines:

- `LokiLogAdapter`
- `PrometheusMetricsAdapter`
- `TempoTraceAdapter`
- `ActuatorHealthAdapter`

Provider-specific response models and configuration details remain inside their adapters/configuration instead of leaking into incident analysis.

Missing telemetry is not treated as healthy evidence. Partial coverage can block automatic recovery.

## Incident lifecycle

`IncidentAnalysisService` correlates telemetry with topology, assesses the likely origin, runs `BlastRadiusGraphService`, distinguishes theoretical/observed/unexpected impact and invokes `IncidentSeverityService`.

`IncidentLifecycleScheduler` periodically evaluates the configured application/environment. `IncidentLifecycleService` creates or updates one ACTIVE incident and requires sufficient healthy evidence/coverage before resolving it. Persistence is through `IncidentRepository` and `JdbcIncidentRepository`.

## Validation model

| Stage | Purpose | Target |
|---|---|---:|
| Stage 1 / Phase 11 | hard failures, lifecycle and partial observability | 6/6 |
| Stage 2 | degraded-but-running detection | 4/4 |
| **E2E total** | | **10/10** |

Run:

```bash
mvn -f blast-radius-api/pom.xml clean test
python3 scripts/run-phase11-e2e.py
python3 scripts/run-stage2-e2e.py --scenario all
```

## Main endpoints

- Dashboard: `http://localhost:5173`
- Blast Radius API: `http://localhost:8080`
- API health: `http://localhost:8080/actuator/health`
- payment-service: `http://localhost:8081`
- customer-service: `http://localhost:8082`
- document-service: `http://localhost:8083`
- Prometheus: `http://localhost:9090`
- Tempo: `http://localhost:3200`
- Loki: `http://localhost:3100`

## Quick start

```bash
git clone https://github.com/SLMakomazi/blast-radius-engine.git
cd blast-radius-engine
cp -n .env.example .env

docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
curl -s http://127.0.0.1:8080/actuator/health
```

See **SETUP.md** for the complete handover procedure.

## Repository areas

```text
blast-radius-api/    Spring Boot blast-radius engine and REST API
frontend/            React/Vite incident dashboard
mock-services/       Synthetic payment/customer/document application
traffic-generator/   Continuous synthetic request traffic
infrastructure/      OpenTelemetry/Loki/Prometheus/Tempo configuration
scripts/             Stage 1 and Stage 2 E2E runners
fixtures/            Synthetic fixture data
```

See **ARCHITECTURE.md** for the current code map.

## Connecting another system

A monitored system needs two main boundaries:

1. `DependencyTopologyProvider` supplies canonical components and directed dependencies.
2. `TelemetryProvider` supplies normalized logs, metrics, traces and health.

The local lab proves those contracts using Tempo, Loki, Prometheus and Actuator. Enterprise integrations can implement the same boundaries without changing deterministic incident logic.

## Security and operating principles

Telemetry is sanitized before persistence/display/AI use. Secrets stay outside Git. AI is advisory. Missing evidence is not interpreted as health. The engine does not autonomously modify production infrastructure.

## Documentation

The repository intentionally keeps three Markdown documents:

- **README.md** — system overview and current structure.
- **SETUP.md** — clone/build/run/test/handover instructions.
- **ARCHITECTURE.md** — current package and code map.

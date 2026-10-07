# MadlangaAI Blast Radius Engine

MadlangaAI Blast Radius Engine is a deterministic incident-analysis service for **MadlangaAI Phase 4**. It connects application dependency topology with runtime telemetry so that a failure can be traced from its likely origin to the services that may be affected.

> **Topology calculates potential impact. Runtime telemetry proves observed impact. AI explains sanitized evidence.**

## What the system does

The engine continuously evaluates recent logs, metrics, distributed traces and health information. It identifies a likely origin, calculates the theoretical dependency blast radius, distinguishes theoretical impact from impact actually observed at runtime, calculates deterministic severity, persists the incident, tracks recovery and optionally produces an AI explanation.

AI does **not** decide the blast radius. The deterministic Java domain owns origin assessment, graph traversal, observed impact, propagation, severity and incident state. Gemini is an optional advisory layer that receives sanitized evidence after deterministic analysis.

## Local proof environment

The repository includes a synthetic application chain used to prove the engine:

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

The lab also runs the Blast Radius API, a separate diagnostic PostgreSQL database, OpenTelemetry Collector, Loki, Prometheus, Tempo and a React dashboard.

The payment/customer/document services are test applications. Their failures are deliberately controlled so the Blast Radius Engine can be validated without touching a real enterprise application.

## How information reaches Blast Radius

```text
Monitored application
        |
        +---- logs ------> Loki -----------+
        +---- metrics ---> Prometheus -----+
        +---- traces ----> Tempo ----------+--> TelemetryProvider
        +---- health ----> Actuator -------+          |
                                                     v
                                               normalized evidence
                                                     |
                                              TelemetrySanitizer
                                                     |
                 +-----------------------------------+------------------+
                 |                                                      |
                 v                                                      v
        dependency topology                                    runtime evidence
                 |                                                      |
                 +-------------------> deterministic analysis <---------+
                                         |
                         origin / impact / propagation / severity
                                         |
                                  incident persistence
                                         |
                                REST API + dashboard
                                         |
                              optional AI diagnosis
```

## Capability-first architecture

The main Spring Boot service uses capability-first DDD packages:

- **incident** — incident analysis, severity, API contracts and JDBC persistence.
- **topology** — components, dependency edges, graph traversal, trace-discovered topology and retained topology.
- **telemetry** — provider-neutral evidence plus Loki, Prometheus, Tempo and Actuator adapters.
- **diagnosis** — deterministic/AI diagnosis context and optional Gemini integration.
- **lifecycle** — scheduled detection, incident updates and guarded recovery.
- **shared** — OpenAPI configuration and telemetry sanitization/redaction.

Dependency direction is kept simple: **API -> application -> domain**. Infrastructure implements ports required by the capabilities.

## Topology vs telemetry

Topology answers: **If this component fails, what could be affected?**

Telemetry answers: **What was actually affected during this incident?**

The engine deliberately keeps those answers separate. A downstream dependency may be theoretically reachable without showing runtime failure evidence.

Local topology can be retained to disk and can also be discovered from Tempo spans. Enterprise integration can replace the local adapters by implementing the topology and telemetry ports without rewriting the deterministic domain.

## Incident lifecycle

A scheduled monitor evaluates the configured application/environment. Failure evidence creates or updates one ACTIVE incident. Repeated evaluations reuse the same incident rather than creating duplicates. Recovery is guarded by consecutive healthy windows and sufficient telemetry coverage before the incident is automatically resolved.

## Validation model

The active validation model has two stages only:

| Stage | Purpose | Expected |
|---|---|---:|
| Stage 1 | Hard failure / blast-radius detection | 6/6 |
| Stage 2 | Degraded-but-running detection | 4/4 |
| **Total** | | **10/10** |

Stage 1 covers the healthy baseline, PostgreSQL and service outages, dependency direction, stable incident identity, recovery and partial-observability protection.

Stage 2 keeps the document service running while injecting HTTP 500, intermittent 500, latency and database-connectivity degradation. The injected condition is synthetic, but the resulting logs, metrics and traces are real runtime telemetry produced by the running lab.

## Main endpoints

- Dashboard: `http://localhost:5173`
- Blast Radius API: `http://localhost:8080`
- API health: `http://localhost:8080/actuator/health`
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
```

For a complete handover, use **SETUP.md**.

## Testing

```bash
mvn -f blast-radius-api/pom.xml clean verify
python3 scripts/run-phase11-e2e.py
python3 scripts/run-stage2-e2e.py --scenario all
```

Expected acceptance result: **10/10**.

## Repository areas

```text
blast-radius-api/    Spring Boot blast-radius engine and REST API
frontend/            React/Vite incident dashboard
mock-services/       Synthetic payment/customer/document application
traffic-generator/   Continuous requests through the synthetic chain
infrastructure/      OpenTelemetry, Loki, Prometheus and Tempo configuration
scripts/             Topology/bootstrap/verification/E2E utilities
fixtures/            Synthetic request data
```

For a file-by-file map, read **ARCHITECTURE.md**. For a manager-facing walkthrough showing what to say and which files to open, read **PRESENTATION_GUIDE.md**.

## Connecting a real system

A real application needs two main integration boundaries:

1. `DependencyTopologyProvider` supplies canonical components and directed dependencies.
2. `TelemetryProvider` supplies normalized logs, metrics, traces and health for those component identities.

The current local adapters prove this contract with Tempo/Loki/Prometheus/Actuator. Final MadlangaAI/enterprise adapters can be added behind the same ports.

## Security and operating principles

Telemetry is sanitized before persistence/display/AI use. Missing telemetry is not treated as healthy evidence. Secrets stay outside Git. AI remains advisory. The engine does not autonomously modify production infrastructure.

## Documentation

This repository intentionally keeps only four Markdown documents:

- **README.md** — what the system is and how the pieces fit together.
- **SETUP.md** — handover/setup/run/test instructions.
- **ARCHITECTURE.md** — folder and file-by-file repository map.
- **PRESENTATION_GUIDE.md** — short presentation walkthrough for managers.

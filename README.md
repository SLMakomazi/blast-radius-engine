# MadlangaAI Blast Radius Engine

A proactive, deterministic incident-analysis capability for **MadlangaAI Phase 4**.

> **Topology calculates potential impact; runtime telemetry proves observed impact; AI explains sanitized evidence.**

The engine monitors application telemetry, identifies a likely failure origin, calculates the dependency blast radius, distinguishes theoretical from observed impact, measures propagation/severity, persists the incident and automatically confirms recovery. AI diagnosis is optional and remains advisory.

## What it does

- proactively evaluates recent telemetry instead of waiting for a user to report an outage;
- correlates logs, metrics, distributed traces and health;
- determines a suspected origin with confidence/evidence;
- calculates direct/indirect theoretical blast radius from dependency topology;
- identifies actually observed impact from runtime evidence;
- records propagation paths and depth;
- calculates deterministic incident severity;
- creates/updates ACTIVE incidents automatically;
- requires consecutive healthy windows before automatically resolving an incident;
- displays incident history/evidence in a local dashboard;
- optionally generates sanitized AI explanation/remediation.

## Local proof

~~~text
traffic-generator
      |
payment-service
      |
customer-service
      |
document-service
      |
postgres
~~~

The lab also runs the Blast Radius API/dashboard, a separate diagnostic PostgreSQL database, OpenTelemetry Collector, Loki, Prometheus and Tempo.

The synthetic application exists to prove failure detection and propagation. It is not MadlangaAI business functionality.

## Architecture

~~~text
             MONITORED APPLICATION
                      |
       +--------------+--------------+
       |              |              |
     logs          metrics         traces       health
       |              |              |            |
       +--------------+------+-------+------------+
                             |
                     TelemetryProvider
                             |
                       sanitization
                             |
            +----------------+----------------+
            |                                 |
   runtime evidence                  dependency topology
            |                                 |
            +----------------+----------------+
                             |
                  deterministic analysis
                             |
             origin / theoretical impact
              observed impact / severity
                  propagation / evidence
                             |
                   incident lifecycle
                             |
                    diagnostic database
                             |
                      REST + dashboard
                             |
                  optional AI diagnosis
~~~

The core is provider-neutral. Loki/Prometheus/Tempo/Actuator are local adapters, not hard requirements for the deterministic domain.

## Proactive lifecycle

~~~text
failure evidence
      |
scheduled evaluation
      |
origin + blast radius
      |
ACTIVE incident
      |
continued telemetry evaluation
      |
recovery evidence
      |
3 consecutive healthy windows
      |
same incident -> RESOLVED
~~~

The dashboard polls persisted incident state automatically. AI diagnosis remains a deliberate button/action.

## Run locally

Prerequisites for the easiest path:
- Git
- Docker Desktop / Docker Compose v2
- curl
- browser

~~~bash
git clone https://github.com/SLMakomazi/blast-radius-engine.git
cd blast-radius-engine
git checkout feat/stage2-degradation-testing
cp -n .env.example .env

docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
~~~

Open:

- Dashboard: http://localhost:5173
- Blast Radius API: http://localhost:8080
- API health: http://localhost:8080/actuator/health

AI is optional. Do not commit API keys.

For the complete setup, tests, outage and recovery walkthrough, read **SETUP.md**.

## Test proactive detection

With the healthy stack running:

~~~bash
docker compose stop customer-service
~~~

Do not manually call the analysis endpoint. Synthetic traffic continues and the scheduled detector should create/update an ACTIVE incident from telemetry.

~~~bash
curl -s 'http://localhost:8080/api/v1/blast-radius/incidents?status=ACTIVE' | python3 -m json.tool
~~~

Restore the service without manually resolving the incident:

~~~bash
docker compose start customer-service
~~~

The engine should require consecutive healthy windows and automatically move the same incident to RESOLVED.

See **SETUP.md** for the full validation procedure.

## AI diagnosis

AI does not calculate blast radius.

The deterministic engine owns:
- topology;
- origin assessment;
- theoretical/observed impact;
- propagation;
- severity;
- incident lifecycle.

AI receives sanitized evidence only after that analysis and produces an advisory explanation/remediation. AI provider failure does not invalidate deterministic analysis.

## Adding real monitored applications

A new system integrates through two main ports:

1. **DependencyTopologyProvider** — supplies canonical components and directed dependencies.
2. **TelemetryProvider** — supplies normalized logs, metrics, traces and health for those component identities.

Conceptually:

~~~text
MadlangaAI application registration
              |
     +--------+--------+
     |                 |
 topology source   telemetry source
     |                 |
     v                 v
DependencyTopology   TelemetryProvider
     |                 |
     +--------+--------+
              |
      Blast Radius Engine
              |
       incidents/evidence
              |
        MadlangaAI UI/API
~~~

The local lifecycle scheduler currently proves one application/environment: document-platform/local. Multi-application registration/scheduling and final MadlangaAI/Datadog contracts are future integration work; they are not represented as already implemented.

Read **ABOUT.md** for the complete onboarding/integration explanation.

## Repository guide

Start here:

| Document | Purpose |
|---|---|
| ABOUT.md | What the system is, what it does and how other applications plug into it. |
| SETUP.md | Local installation, startup, tests, outage/recovery validation and troubleshooting. |
| PROJECT_STRUCTURE.md | Folder/file map for developers reviewing the codebase. |
| docs/ARCHITECTURE.md | Deeper internal architecture. |
| docs/INTEGRATION.md | MadlangaAI/enterprise adapter design and unresolved integration contracts. |
| docs/DECISIONS.md | Architectural decision history. |

The old phase-by-phase implementation/verification documents were intentionally removed from the active documentation set. Git history retains them if historical evidence is needed.

## Main repository areas

~~~text
blast-radius-api/    Spring Boot deterministic engine/API
frontend/            React/Vite incident dashboard
mock-services/       Synthetic payment/customer/document application
traffic-generator/   Continuous synthetic traffic/canary
infrastructure/      Loki, Prometheus, Tempo and OpenTelemetry configuration
scripts/             Verification/bootstrap utilities
fixtures/            Synthetic request/test data
docs/                Durable architecture/integration/decision documentation
~~~

See **PROJECT_STRUCTURE.md** for details.

## Security principles

- sanitize telemetry before persistence/display/export/AI;
- do not treat missing telemetry as healthy evidence;
- keep secrets and credentials out of Git;
- preserve evidence provenance/timestamps;
- keep AI advisory;
- do not autonomously modify production infrastructure;
- keep Blast Radius incident severity separate from the MadlangaAI Overall Health Score.

## Validation roadmap — Stages 1–4

The validation roadmap grows from simple availability failures into production-relevant distributed failure analysis. A stage is not considered complete merely because an incident was created; its scenario-specific acceptance criteria must pass.

| Stage | Focus | Scenarios | Status |
|---|---|---|---|
| **Stage 1 — Hard failures** | Availability and dependency propagation | PostgreSQL outage, document-service outage, customer-service outage, payment-service outage, partial observability | **Validated locally** |
| **Stage 2 — Degradation** | Failures while services remain running | HTTP 500, intermittent 500, latency/timeouts, database connectivity; resource-pressure correlation | **In validation** |
| **Stage 3 — Change-related failures** | Connect incidents to deployments/configuration changes | bad deployment, incompatible configuration, migration/change regression, rollback/recovery correlation | **Planned** |
| **Stage 4 — Complex distributed failures** | Multiple/partial failures and wider blast-radius reasoning | concurrent failures, cascading degradation, network/dependency partitions, asymmetric/partial failures | **Planned** |

### Stage 1 — Hard failures

Stage 1 proves the baseline: a component becomes unavailable, telemetry detects the failure, topology calculates what could be affected, runtime evidence shows what was affected, the incident is persisted, and guarded recovery resolves the same incident after healthy windows.

The Phase 11 runner is the Stage 1 end-to-end acceptance suite.

### Stage 2 — Degraded but running

Stage 2 proves that Blast Radius is not limited to stopped containers. The synthetic document service can remain UP while bounded HTTP 500, intermittent 500, latency and database-connectivity faults are injected.

Stage 2 acceptance is intentionally evidence-aware. Provider availability alone is not enough: required telemetry families must contribute correlated incident evidence. The current runner expects LOG + METRIC + TRACE for HTTP 500/intermittent/latency and LOG + TRACE for database connectivity. HEALTH may remain available with zero incident evidence because a degraded service can legitimately remain UP.

Current validation has demonstrated degradation detection, trace/log enrichment and automatic recovery, but metric enrichment remains under active validation. Until `python3 scripts/run-stage2-e2e.py --scenario all` passes all required evidence assertions, Stage 2 remains **in validation**, not complete.

### Stage 3 — Change-related failures

Stage 3 will add change context to deterministic incident analysis. The objective is to answer not only *what failed and what was affected*, but also whether a recent deployment, configuration or migration is a credible change-related contributor.

Planned validation includes bad deployments, configuration regressions, migration/change failures and rollback/recovery correlation. Exact Stage 3 fault injectors, evidence contracts and acceptance thresholds will be defined during implementation; they are not represented as existing capabilities.

### Stage 4 — Complex distributed failures

Stage 4 will validate scenarios where one simple origin may not describe the incident adequately. Planned work includes concurrent failures, cascading degradation, network/dependency partitions and asymmetric or partial failures.

This stage is expected to exercise multi-origin/ambiguous-origin reasoning, evidence conflicts, topology boundaries and partial observability more heavily. Exact scenarios and acceptance criteria remain planned work and must not be treated as implemented.

After Stage 4, the major boundary is enterprise/MadlangaAI integration: real application registration, canonical identity mapping, topology/evidence adapters, approved monitoring sources, RBAC/audit and production deployment/retention decisions.

## Current status

Implemented locally:
- full telemetry adapters;
- technology-neutral topology and graph engine;
- origin/evidence correlation;
- theoretical vs observed impact;
- deterministic severity;
- incident persistence/history;
- proactive scheduled detection;
- guarded automatic recovery;
- responsive dashboard with separate Stage 1 hard-failure and Stage 2 degradation views, live incident polling and ACTIVE/RESOLVED history;
- Stage 2 bounded degradation injection for HTTP 500, intermittent 500, latency and database-connectivity failures while the service remains running;
- Stage 2 acceptance runner with evidence-family assertions;
- optional Gemini diagnosis with deterministic fallback.

Current acceptance status:
- **Stage 1:** locally validated through the Phase 11 hard-failure/end-to-end scenarios;
- **Stage 2:** in validation; incident detection/recovery and LOG/TRACE evidence have been demonstrated, while required METRIC enrichment is not yet fully passing;
- **Stage 3:** planned, not implemented;
- **Stage 4:** planned, not implemented.


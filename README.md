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
git checkout feat/phase10-local-ui
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
- responsive dashboard with live incident polling;
- optional Gemini diagnosis with deterministic fallback.

The next major boundary is enterprise/MadlangaAI integration: application registration, canonical identity mapping, MadlangaAI topology adapter, approved telemetry adapters, RBAC/audit and production deployment/retention decisions.

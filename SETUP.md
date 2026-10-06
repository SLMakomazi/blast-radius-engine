# Local Setup and Test Guide

This guide is for engineers reviewing or running the **MadlangaAI Blast Radius Engine** locally.

## Prerequisites

Recommended:
- Git
- Docker Desktop with Docker Compose v2
- curl
- a modern browser

Optional for running tests directly on the host:
- JDK 21
- Maven 3.9.x
- Python 3.9+
- Node.js 20+ and npm

PostgreSQL, Prometheus, Loki, Tempo and OpenTelemetry do not need to be installed on the host; Compose starts them.

## Clone and configure

~~~bash
git clone https://github.com/SLMakomazi/blast-radius-engine.git
cd blast-radius-engine
cp -n .env.example .env
~~~

AI is optional. To enable Gemini diagnosis, create your own key and export it locally:

~~~bash
export GEMINI_ENABLED=true
export GEMINI_API_KEY='<your-own-key>'
~~~

Never commit keys, tokens or credentials.

## Build and start

From the repository root:

~~~bash
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
~~~

Main local endpoints:
- Dashboard: http://localhost:5173
- Blast Radius API: http://localhost:8080
- API health: http://localhost:8080/actuator/health
- Prometheus: http://localhost:9090
- Tempo: http://localhost:3200
- Loki: http://localhost:3100

Synthetic monitored chain:

~~~text
traffic-generator
      |
payment-service :8081
      |
customer-service :8082
      |
document-service :8083
      |
postgres :5432
~~~

The separate blast-radius-db stores diagnostic incidents; it is not the monitored target database.

## Healthy baseline

~~~bash
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8081/actuator/health
curl --fail http://localhost:8082/actuator/health
curl --fail http://localhost:8083/actuator/health

curl -s 'http://localhost:8080/api/v1/blast-radius/incidents?status=ACTIVE' | python3 -m json.tool
~~~

Expected ACTIVE result is an empty array. Normal synthetic traffic should show HTTP 201:

~~~bash
docker compose logs --tail=20 traffic-generator
~~~

## Stage 1–4 validation map

Use the stages as progressive validation boundaries. Do not skip a stage or mark a
planned scenario as implemented before its acceptance suite passes.

| Stage | Validation target | Local entry point | Status |
|---|---|---|---|
| **Stage 1 — Hard failures** | outages, propagation, recovery, partial observability | `scripts/run-phase11-e2e.py` | Validated locally |
| **Stage 2 — Degradation** | HTTP errors, intermittent errors, latency, DB connectivity while services stay running | `scripts/run-stage2-e2e.py` | In validation |
| **Stage 3 — Change-related** | deployments/config/migrations and rollback correlation | To be implemented | Planned |
| **Stage 4 — Complex distributed** | concurrent/cascading/partition/partial failures | To be implemented | Planned |

### Stage 1 acceptance

With the full stack healthy:

~~~bash
python3 scripts/run-phase11-e2e.py
~~~

Stage 1 covers the healthy baseline, PostgreSQL/service outages, dependency direction,
stable incident identity, automatic recovery and partial-observability protection.
The existing manual outage sections below can be used to inspect individual scenarios.

### Stage 2 acceptance

With the full stack healthy:

~~~bash
python3 scripts/run-stage2-e2e.py --scenario all
~~~

Stage 2 is stricter than "incident detected." The runner validates required correlated
evidence families. Expected evidence is LOG + METRIC + TRACE for HTTP 500,
intermittent 500 and latency; DB connectivity requires LOG + TRACE. HEALTH can
legitimately have zero incident evidence while the service remains UP.

A completed scenario resets its fault and waits for automatic recovery, so successful
test incidents should normally be found under **Stage 2 -> RESOLVED**.

At the current validation point, LOG/TRACE enrichment and recovery have been observed,
but required METRIC enrichment is not yet fully passing. Do not mark Stage 2 complete
until the complete runner passes all scenario assertions.

### Stage 3 plan — change-related failures

Stage 3 is **not implemented yet**. Its implementation should define deterministic
change evidence before adding test scenarios. Planned validation targets are:

- bad deployment correlated with the beginning of an incident;
- incompatible configuration/change regression;
- migration-related failure;
- rollback correlated with recovery.

The acceptance runner, fault injectors, persisted change metadata and thresholds must
be defined when Stage 3 is implemented. Do not use synthetic Stage 2 evidence as a
substitute for real change context.

### Stage 4 plan — complex distributed failures

Stage 4 is **not implemented yet**. Planned validation targets are concurrent failures,
cascading degradation, network/dependency partitions and asymmetric/partial failures.

Stage 4 should explicitly test ambiguous or multiple origins, conflicting evidence,
topology boundaries and partial telemetry. Exact pass/fail rules must be defined
before implementation so that the runner tests deterministic behavior rather than
merely checking that an incident exists.


## Test proactive detection

Do not call the analyze endpoint. This test proves that monitoring detects an outage before a user reports it.

~~~bash
docker compose stop customer-service
docker compose logs --tail=20 -f traffic-generator
~~~

Traffic should move from 201 to 502. Exit logs with Ctrl+C and allow the lifecycle monitor to evaluate the rolling telemetry window.

~~~bash
curl -s 'http://localhost:8080/api/v1/blast-radius/incidents?status=ACTIVE' | python3 -m json.tool

docker compose logs --since=2m blast-radius-api \
  | grep -E 'Proactive failure|Recovery evidence|remains ACTIVE|automatically resolved'
~~~

Expected:
- an ACTIVE incident is created automatically;
- customer-service is assessed as the origin when supported by evidence;
- repeated evaluations update the same active incident rather than create duplicates;
- the dashboard receives the incident automatically;
- AI is not involved in detection.

## Test automatic recovery

Do not manually resolve the incident.

~~~bash
docker compose start customer-service

docker compose logs -f blast-radius-api \
  | grep -E 'Proactive failure|Recovery evidence|remains ACTIVE|automatically resolved'
~~~

The local configuration requires three consecutive healthy evaluation windows before resolution:

~~~text
healthy window 1/3
healthy window 2/3
healthy window 3/3
automatically resolved
~~~

Verify:

~~~bash
curl -s 'http://localhost:8080/api/v1/blast-radius/incidents?status=ACTIVE' | python3 -m json.tool
curl -s 'http://localhost:8080/api/v1/blast-radius/incidents?status=RESOLVED' | python3 -m json.tool
~~~

## Test database blast radius

Stop only the monitored target PostgreSQL, not blast-radius-db:

~~~bash
docker compose stop postgres
~~~

Expected outage path:

~~~text
postgres             ORIGIN
document-service     OBSERVED
customer-service     OBSERVED
payment-service      OBSERVED
~~~

Restore it:

~~~bash
docker compose start postgres
~~~

The detector should confirm recovery and resolve the same incident automatically.

## AI diagnosis

AI diagnosis is deliberately on-demand. It does not create incidents, choose the origin, calculate blast radius or determine severity.

When Gemini is configured, select an incident in the dashboard and use **Generate AI Diagnosis**. Provider failure must not invalidate deterministic analysis.

## Tests

Backend:

~~~bash
mvn -f blast-radius-api/pom.xml clean verify
~~~

Synthetic services:

~~~bash
mvn -f mock-services/payment-service/pom.xml clean verify
mvn -f mock-services/customer-service/pom.xml clean verify
mvn -f mock-services/document-service/pom.xml clean verify
~~~

Traffic generator:

~~~bash
python3 -m unittest discover -s traffic-generator -v
~~~

Frontend:

~~~bash
cd frontend
npm install
npm run build
~~~

## Phase 11 end-to-end acceptance

With the full Compose stack running, execute:

~~~bash
python3 scripts/run-phase11-e2e.py
~~~

The runner validates the healthy baseline, PostgreSQL/service outages, dependency-direction correctness, stable incident UUIDs, automatic recovery and partial-observability recovery protection.

See `docs/E2E_VALIDATION.md` for scenario expectations and single-scenario commands.

## Stage 2 degradation validation

Stage 2 keeps every application container running and injects bounded failures into the
synthetic document service. This proves that Blast Radius is not limited to detecting
stopped containers.

Run the complete degradation suite:

~~~bash
python3 scripts/run-stage2-e2e.py --scenario all
~~~

Or run one scenario:

~~~bash
python3 scripts/run-stage2-e2e.py --scenario http-500
python3 scripts/run-stage2-e2e.py --scenario intermittent
python3 scripts/run-stage2-e2e.py --scenario latency
python3 scripts/run-stage2-e2e.py --scenario db-connectivity
~~~

The local fault-control endpoint is published only through document-service's
localhost-bound Compose port. It is synthetic lab code and must not be copied into
a production application.

Manual examples:

~~~bash
# Continuous HTTP 500 while document-service stays UP
curl -X PUT http://localhost:8083/lab/faults \
  -H 'Content-Type: application/json' \
  -d '{"mode":"ERROR_500","latencyMs":0,"everyNthRequest":5}'

# 9 second latency; customer-service has an 8 second read timeout
curl -X PUT http://localhost:8083/lab/faults \
  -H 'Content-Type: application/json' \
  -d '{"mode":"LATENCY","latencyMs":9000,"everyNthRequest":5}'

# Synthetic database connectivity failure while Postgres remains running
curl -X PUT http://localhost:8083/lab/faults \
  -H 'Content-Type: application/json' \
  -d '{"mode":"DATABASE_FAILURE","latencyMs":0,"everyNthRequest":5}'

# Always restore healthy behaviour
curl -X DELETE http://localhost:8083/lab/faults
~~~

The engine also correlates sustained process CPU usage (>= 90%), sustained Hikari
connection-pool contention, HTTP 5xx counter growth and high mean HTTP latency.
Memory-used by itself is deliberately not treated as memory pressure because a
used-byte value without a configured/max limit is insufficient evidence.

### Stage 2 acceptance criteria

A detected incident is not enough to pass a Stage 2 scenario. The runner also checks
that the expected telemetry families actually contributed correlated evidence.

Expected evidence:
- **http-500:** LOG + METRIC + TRACE
- **intermittent:** LOG + METRIC + TRACE
- **latency:** LOG + METRIC + TRACE
- **db-connectivity:** LOG + TRACE

HEALTH may legitimately contain zero incident evidence for these scenarios because
the purpose of Stage 2 is to validate degraded-but-running services. Provider
availability and incident evidence are separate concepts: `AVAILABLE / 0 evidence`
means the provider was reachable, not that it proved the incident.

After each scenario the runner removes the synthetic fault and waits for automatic
recovery. Therefore, after a successful complete run the generated incidents are
expected under **Stage 2 -> RESOLVED**, not ACTIVE.

### Stage 2 dashboard

The left navigation separates:
- **Stage 1 / Hard failures** for availability/outage validation;
- **Stage 2 / Degradation** for degraded-but-running validation.

Use the ACTIVE/RESOLVED toggle inside the selected stage. A completed Stage 2 runner
normally leaves no Stage 2 incident ACTIVE because recovery is part of the test.

### Rebuild after Stage 2 code changes

From the repository root:

~~~bash
docker compose up -d --build --force-recreate blast-radius-api document-service frontend
docker compose ps blast-radius-api document-service frontend
~~~

Wait until the three services are healthy before running the Stage 2 suite.

If Maven fails during a Docker build with `Premature end of Content-Length delimited
message body`, that indicates an incomplete dependency download rather than an
application compilation failure. Retry the API image build:

~~~bash
docker compose build --no-cache blast-radius-api
docker compose up -d blast-radius-api frontend
docker compose ps blast-radius-api frontend
~~~

Do not modify the application POM merely to work around an interrupted Maven Central
download.

## Useful commands

~~~bash
docker compose ps
docker compose logs -f blast-radius-api
docker compose logs -f payment-service customer-service document-service
docker compose logs -f traffic-generator
docker compose down
~~~

To deliberately remove local volumes/data:

~~~bash
docker compose down -v
~~~

Do not use -v if you want to retain local state.

## Troubleshooting

**Dashboard has no incidents:** a healthy system should have no ACTIVE incidents. Verify the API and lifecycle logs.

**Failure occurs but no incident appears:** verify the lifecycle monitor is enabled, telemetry providers are reachable, and allow the rolling telemetry window to be evaluated.

**Telemetry is AVAILABLE with zero evidence:** AVAILABLE means the provider responded; zero means no normalized evidence from that family matched the incident window/filter.

**AI fails:** deterministic analysis should remain available. Verify Gemini environment configuration separately.

## Review principle

> **Topology calculates potential impact; runtime telemetry proves observed impact; AI explains sanitized evidence.**

The deterministic domain should remain independent of telemetry vendors, the LLM provider and the frontend.

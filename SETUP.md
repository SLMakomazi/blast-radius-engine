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
git checkout main
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
python3 scripts/run-stage2-e2e.py
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

## Complete regression gate

The validation gate is Stage 1: 6/6 and Stage 2: 4/4 (TOTAL: 10/10 PASS). Run both suites after structural changes; record fresh results separately from historical baselines. Follow [the complete validation order](docs/E2E_VALIDATION.md), including at least 10 seconds between stages, fresh trace-export checks after Stage 1 and final platform checks.

For new code, follow [the developer architecture guide](docs/DEVELOPER_GUIDE.md).

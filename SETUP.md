# MadlangaAI Blast Radius Engine — Setup and Handover

This file is for the next engineer who needs to clone, run, test and understand the local Blast Radius Engine.

## 1. Prerequisites

Install:

- Git
- Docker Desktop with Docker Compose v2
- curl
- a modern browser

Useful for running components directly:

- JDK 21
- Maven 3.9+
- Python 3.9+
- Node.js 20+ and npm

PostgreSQL, Loki, Prometheus, Tempo and OpenTelemetry Collector run through Docker Compose.

## 2. Clone and configure

```bash
git clone https://github.com/SLMakomazi/blast-radius-engine.git
cd blast-radius-engine
cp -n .env.example .env
```

Never commit secrets. Gemini is optional. If required, set `GEMINI_ENABLED=true` and provide `GEMINI_API_KEY` in your local environment.

## 3. Build and start

```bash
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
```

Expected local endpoints:

| Component | URL |
|---|---|
| Dashboard | http://localhost:5173 |
| Blast Radius API | http://localhost:8080 |
| Blast Radius health | http://localhost:8080/actuator/health |
| payment-service | http://localhost:8081 |
| customer-service | http://localhost:8082 |
| document-service | http://localhost:8083 |
| Prometheus | http://localhost:9090 |
| Tempo | http://localhost:3200 |
| Loki | http://localhost:3100 |

## 4. Understand the two databases

`postgres` is the database used by the synthetic document application. Stopping it is a monitored failure scenario.

`blast-radius-db` belongs to the Blast Radius Engine and stores detected incidents. Do not stop the wrong database when demonstrating PostgreSQL blast radius.

## 5. Check the healthy baseline

```bash
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8081/actuator/health
curl --fail http://localhost:8082/actuator/health
curl --fail http://localhost:8083/actuator/health

curl -s 'http://localhost:8080/api/v1/blast-radius/incidents?status=ACTIVE'   | python3 -m json.tool

docker compose logs --tail=20 traffic-generator
```

A healthy system should normally have no ACTIVE incident. The traffic generator should continuously exercise payment -> customer -> document -> PostgreSQL.

## 6. Stage 1 — hard failures

Run all six scenarios:

```bash
python3 scripts/run-phase11-e2e.py
```

Expected result: **6/6 PASS**.

You can also demonstrate a failure manually:

```bash
docker compose stop customer-service
```

Wait for the scheduled lifecycle monitor, then inspect:

```bash
curl -s 'http://localhost:8080/api/v1/blast-radius/incidents?status=ACTIVE'   | python3 -m json.tool
```

Restore the service:

```bash
docker compose start customer-service
```

Do not manually resolve the incident. The engine should use healthy windows to resolve the same incident automatically.

For the database blast-radius demonstration:

```bash
docker compose stop postgres
# expected potential/observed path: postgres -> document-service -> customer-service -> payment-service
docker compose start postgres
```

## 7. Stage 2 — degraded but still running

Run all four scenarios:

```bash
python3 scripts/run-stage2-e2e.py --scenario all
```

Expected result: **4/4 PASS**.

Individual scenarios:

```bash
python3 scripts/run-stage2-e2e.py --scenario http-500
python3 scripts/run-stage2-e2e.py --scenario intermittent
python3 scripts/run-stage2-e2e.py --scenario latency
python3 scripts/run-stage2-e2e.py --scenario db-connectivity
```

Stage 2 uses the localhost-only `/lab/faults` endpoint on document-service. The fault is deliberately injected; the telemetry produced by the running services is real runtime telemetry.

Always restore normal behaviour after manual fault testing:

```bash
curl -X DELETE http://localhost:8083/lab/faults
```

## 8. Unit and build tests

Blast Radius API:

```bash
mvn -f blast-radius-api/pom.xml clean verify
```

Mock services:

```bash
mvn -f mock-services/payment-service/pom.xml clean verify
mvn -f mock-services/customer-service/pom.xml clean verify
mvn -f mock-services/document-service/pom.xml clean verify
```

Traffic generator:

```bash
python3 -m unittest discover -s traffic-generator -v
```

Frontend:

```bash
cd frontend
npm install
npm run build
cd ..
```

Final acceptance target:

```text
Stage 1: 6/6
Stage 2: 4/4
TOTAL: 10/10
```

## 9. Useful logs

```bash
docker compose logs -f blast-radius-api
docker compose logs -f payment-service customer-service document-service
docker compose logs -f otel-collector
docker compose logs -f traffic-generator
```

If telemetry appears incomplete, check the OpenTelemetry Collector first, then the relevant Loki/Prometheus/Tempo adapter configuration.

## 10. Stop the environment

Keep volumes:

```bash
docker compose down
```

Delete local volumes/data:

```bash
docker compose down -v
```

Use `-v` only when you intentionally want a clean database state.

## 11. Where to start when changing code

Read **ARCHITECTURE.md** before moving classes. The main API is capability-first:

```text
incident/
topology/
telemetry/
diagnosis/
lifecycle/
shared/
```

Domain classes should remain plain Java. Application classes orchestrate use cases. Infrastructure contains JDBC, telemetry vendors, filesystem, Gemini and scheduling implementations. API contains HTTP controllers and transport DTOs.

## 12. Adding another monitored system

Do not rewrite the domain around a vendor. Implement/adapt:

- `DependencyTopologyProvider` for the application's component/dependency model.
- `TelemetryProvider` for normalized evidence.

Ensure both sides use the same canonical component identities. The deterministic engine can then correlate theoretical topology with observed runtime evidence.

## 13. Common troubleshooting

**No incident appears:** confirm the lifecycle monitor is enabled, traffic is flowing, telemetry backends are reachable and enough time has passed for the rolling window.

**AVAILABLE but zero evidence:** the provider responded, but no normalized evidence matched the requested component/time window.

**Stage 2 misses TRACE:** check OpenTelemetry Collector and Tempo export/search before changing the test assertion.

**AI fails:** deterministic analysis should still work. Treat Gemini configuration separately.

**Dashboard is empty:** first query the incident API. A healthy system legitimately has no ACTIVE incidents.

## 14. Handover checklist

Before handing the repository to another engineer:

```bash
docker compose config --quiet
mvn -f blast-radius-api/pom.xml clean verify
python3 scripts/run-phase11-e2e.py
python3 scripts/run-stage2-e2e.py --scenario all
docker compose ps
git status
```

The expected E2E result is **10/10**.

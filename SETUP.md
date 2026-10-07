# MadlangaAI Blast Radius Engine — Setup and Handover

This guide is for cloning, running, validating and handing over the current Blast Radius Engine.

## 1. Prerequisites

Required:

- Git
- Docker Desktop with Docker Compose v2
- curl
- Python 3
- modern browser

For direct API development/testing:

- JDK 21+
- Maven 3.9+

For direct frontend development:

- Node.js 20+
- npm

PostgreSQL, Loki, Prometheus, Tempo and OpenTelemetry Collector run through Docker Compose.

## 2. Clone and configure

```bash
git clone https://github.com/SLMakomazi/blast-radius-engine.git
cd blast-radius-engine
cp -n .env.example .env
```

Never commit secrets. Gemini is optional. If required locally, set `GEMINI_ENABLED=true` and provide `GEMINI_API_KEY`.

## 3. Build and start

```bash
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
```

Health check:

```bash
curl -s http://127.0.0.1:8080/actuator/health
```

Expected API status contains `"status":"UP"`.

Local endpoints:

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

## 4. Know the two PostgreSQL containers

`postgres` belongs to the synthetic document application. Stopping it is a monitored Stage 1 failure.

`blast-radius-db` belongs to the Blast Radius Engine and stores incidents.

Do not stop `blast-radius-db` when demonstrating the monitored PostgreSQL blast radius.

## 5. Healthy baseline

```bash
docker compose ps

curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8081/actuator/health
curl --fail http://localhost:8082/actuator/health
curl --fail http://localhost:8083/actuator/health

curl -s 'http://localhost:8080/api/v1/blast-radius/incidents?status=ACTIVE' | python3 -m json.tool

docker compose logs --tail=20 traffic-generator
```

A healthy environment should normally have no new ACTIVE incident. The traffic generator continuously exercises:

```text
payment-service -> customer-service -> document-service -> postgres
```

## 6. Rebuild the API after Java changes

A successful local Maven test does not update an already-running Docker image. After API code changes:

```bash
docker compose up -d --build blast-radius-api
curl -s http://127.0.0.1:8080/actuator/health
```

## 7. API tests

Run the current API suite:

```bash
mvn -f blast-radius-api/pom.xml clean test
```

Current validated result:

```text
Tests run: 189
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

If Maven clean cannot delete `blast-radius-api/target`, remove the generated directory and rerun:

```bash
rm -rf blast-radius-api/target
mvn -f blast-radius-api/pom.xml clean test
```

## 8. Stage 1 / Phase 11 — hard failures

Run:

```bash
python3 scripts/run-phase11-e2e.py
```

Current validated result: **6/6 PASS**.

Scenarios:

1. healthy baseline
2. PostgreSQL outage
3. document-service outage
4. customer-service outage
5. payment-service outage
6. partial observability

The runner intentionally stops/starts Compose services and restores them in `finally` blocks. Do not manually stop services while the suite is running.

The validated dependency direction for a PostgreSQL failure is:

```text
postgres -> document-service -> customer-service -> payment-service
```

The partial-observability scenario verifies that loss of Tempo coverage cannot falsely resolve an ACTIVE incident.

## 9. Stage 2 — degraded but still running

Run all four:

```bash
python3 scripts/run-stage2-e2e.py --scenario all
```

Individual scenarios:

```bash
python3 scripts/run-stage2-e2e.py --scenario http-500
python3 scripts/run-stage2-e2e.py --scenario intermittent
python3 scripts/run-stage2-e2e.py --scenario latency
python3 scripts/run-stage2-e2e.py --scenario db-connectivity
```

Target: **4/4 PASS**.

Stage 2 uses the localhost-only `document-service` fault endpoint. The injected condition is synthetic; the resulting logs, metrics and traces come from the running services.

Manual reset:

```bash
curl -X DELETE http://localhost:8083/lab/faults
```

## 10. Other component tests

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

Full E2E acceptance target:

```text
Stage 1: 6/6
Stage 2: 4/4
TOTAL: 10/10
```

## 11. Useful logs

```bash
docker compose logs -f blast-radius-api
docker compose logs -f payment-service customer-service document-service
docker compose logs -f otel-collector
docker compose logs -f tempo
docker compose logs -f prometheus
docker compose logs -f loki
docker compose logs -f traffic-generator
```

For incomplete telemetry, inspect the emitting service, OpenTelemetry Collector and corresponding backend before weakening an E2E assertion.

## 12. Stop the environment

Keep persistent volumes:

```bash
docker compose down
```

Delete local volumes/data:

```bash
docker compose down -v
```

Use `-v` only when a clean local database/topology state is intentional.

## 13. Current code organization

The Spring Boot API is feature-first:

```text
diagnosis/   config, dto, mapper, provider, service
incident/    controller, dto, model, repository, service
lifecycle/   scheduler, service
shared/      shared cross-feature utilities/configuration
telemetry/   config, model, provider
topology/    config, model, provider, repository, service
```

Do not reintroduce `api/application/domain/infrastructure/port` package layers. Use familiar Spring names and create only folders that are actually needed.

## 14. Adding another monitored system

Implement/adapt:

- `topology.provider.DependencyTopologyProvider`
- `telemetry.provider.TelemetryProvider`

Both must use the same canonical component identities. The deterministic engine then correlates topology with runtime evidence without being tied to a telemetry vendor.

## 15. Troubleshooting

**No incident appears:** verify traffic, lifecycle scheduling, telemetry backends and the analysis window.

**Telemetry provider is AVAILABLE but has no evidence:** the backend responded, but no normalized evidence matched the requested scope/window.

**Stage 2 misses TRACE:** inspect service Java-agent output, OpenTelemetry Collector and Tempo ingestion/search.

**AI fails:** deterministic incident analysis should continue. Gemini is optional.

**Dashboard is empty:** query the incident API first; a healthy system can legitimately have no ACTIVE incident.

**Docker was restarted:** verify `docker info` and `docker compose ps` before running E2E tests.

## 16. Handover checklist

```bash
docker compose config --quiet
mvn -f blast-radius-api/pom.xml clean test
docker compose up -d --build blast-radius-api
curl -s http://127.0.0.1:8080/actuator/health
python3 scripts/run-phase11-e2e.py
python3 scripts/run-stage2-e2e.py --scenario all
docker compose ps
git status
```

Read **ARCHITECTURE.md** for the current package/class map.

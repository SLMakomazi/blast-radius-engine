# MadlangaAI Blast Radius Engine — Setup and Handover

Read this after [README.md](README.md). It takes a new operator from a clean checkout to a healthy local lab and verified incident detection; use [PRESENTATION.md](PRESENTATION.md) for the demonstration.

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

- Node.js 22.12+ (the container build uses Node 22)
- npm

PostgreSQL, Loki, Prometheus, Tempo and OpenTelemetry Collector run through Docker Compose.

## 2. Clone and configure

```bash
git clone https://github.com/SLMakomazi/blast-radius-engine.git
cd blast-radius-engine
cp -n .env.example .env
```

Never commit secrets. Gemini is optional. If required locally, set `GEMINI_ENABLED=true` and provide `GEMINI_API_KEY`. The default advisory model chain is `gemini-3.5-flash-lite` followed by `gemini-3.5-flash`.

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

`postgres` belongs to the synthetic document application. Stopping it is a monitored hard-failure validation failure.

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

Previously recorded result (rerun for the current checkout):

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

## 8. Hard failure validation

Run:

```bash
python3 scripts/run-phase11-e2e.py
```

Previously recorded result (rerun for the current checkout): **6/6 PASS**.

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

Before degradation validation, wait at least 10 seconds after hard-failure validation. Confirm Tempo `/ready`, Collector health on port 13133, and fresh traces through Tempo `/api/search`; inspect recent Collector logs for export failures. If tracing has not recovered after the deliberate outage, restart `tempo` and `otel-collector`, wait for readiness and fresh traces, then continue. A running container alone is not sufficient.

## 9. degradation validation — degraded but still running

Run all four:

```bash
python3 scripts/run-degradation-e2e.py --scenario all
```

Individual scenarios:

```bash
python3 scripts/run-degradation-e2e.py --scenario http-500
python3 scripts/run-degradation-e2e.py --scenario intermittent
python3 scripts/run-degradation-e2e.py --scenario latency
python3 scripts/run-degradation-e2e.py --scenario db-connectivity
```

Target: **4/4 PASS**.

degradation validation uses the localhost-only `document-service` fault endpoint. The injected condition is synthetic; the resulting logs, metrics and traces come from the running services.

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
npm ci
node --test src/incident-visuals.test.js
npm run build
cd ..
```

Full E2E acceptance target:

```text
hard-failure validation: 6/6
degradation validation: 4/4
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

**degradation validation misses TRACE:** inspect service Java-agent output, OpenTelemetry Collector and Tempo ingestion/search.

**AI fails:** deterministic incident analysis should continue. Gemini is optional. Check `GEMINI_ENABLED`, `GEMINI_API_KEY`, `GEMINI_MODEL` and `GEMINI_FALLBACK_MODELS`. The default model chain is `gemini-3.5-flash-lite` -> `gemini-3.5-flash`; model unavailability, timeout and retryable provider failures can move diagnosis to the configured fallback model, while the deterministic provider remains the final application fallback.

**Dashboard selection changes during refresh:** the dashboard should preserve an explicitly selected incident during its five-second polling cycle. Generated diagnoses are cached per incident for the current browser session.

**Dashboard is empty:** query the incident API first; a healthy system can legitimately have no ACTIVE incident.

**Docker was restarted:** verify `docker info` and `docker compose ps` before running E2E tests.

## 16. Handover checklist

```bash
docker compose config --quiet
mvn -f blast-radius-api/pom.xml clean test
docker compose up -d --build blast-radius-api
curl -s http://127.0.0.1:8080/actuator/health
python3 scripts/run-phase11-e2e.py
python3 scripts/run-degradation-e2e.py --scenario all
docker compose ps
git status
```

Read [ARCHITECTURE.md](ARCHITECTURE.md) for every file and its connections.

## 17. Rebuild and demonstrate the visual dashboard

```bash
docker compose up -d --build frontend
```

Open [the dashboard](http://localhost:5173). Select an ACTIVE or RESOLVED incident and its hard-failure validation or degradation validation view. If there are no active incidents, use resolved history; an empty ACTIVE list is legitimate.

1. Search the incident list for a service or symptom.
2. Open the service map. Red is unavailable, amber is failure signals, pulsing green is potential impact, and gray is uncertainty.
3. Hover, focus or select a service to read its explanation and recorded dependency path. Select “Inspect supporting evidence” to see the underlying observations.
4. Use the evidence timeline legend to filter by logs, measurements, traces or health. Expand “Technical observation” for the original sanitized signal.
5. “Copy team handoff” copies text locally for review; it does not notify anyone. Browser clipboard restrictions may require manual copying.

Resolved snapshots are historical, not a live uptime map. Potential impact must never be presented as a confirmed outage or confirmed health. The animation respects reduced-motion settings.

The frontend build uses `VITE_API_BASE_URL`. Compose defaults to the same-origin Nginx proxy in `frontend/nginx.conf`; keep this path for the simplest setup. A standalone Vite development server needs an API URL and backend CORS support or a local proxy.

For a manual local demonstration, stop `customer-service`, wait for automatic detection, then start it again and wait for recovery. Do not stop the diagnostic database or inject faults during a validation run.

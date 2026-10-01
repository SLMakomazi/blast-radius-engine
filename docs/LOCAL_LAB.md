# Local Docker Lab

## Purpose
Provide a self-contained environment that proves Blast Radius with **real synthetic runtime behavior**, not only JSON fixtures.

## Required topology
```text
traffic-generator
      |
      v
payment-service
      |
      v
customer-service
      |
      v
document-service
      |
      v
postgres
```

All business data is synthetic.

## Required containers/capabilities
### Application path
- `payment-service` — public entry point.
- `customer-service` — downstream dependency.
- `document-service` — performs database operations.
- `postgres` — target database and canonical failure origin.
- `traffic-generator` — continuously/periodically generates requests.

### Blast Radius
- `blast-radius-api` — analysis API.
- Optional dedicated `blast-radius-db` if incident/evidence history is persisted. It must remain available when target `postgres` is intentionally failed.

### Observability
The local stack must make all four normalized evidence families queryable:
- logs;
- metrics;
- traces;
- endpoint/application health.

Recommended implementation direction:
- OpenTelemetry SDK/agent instrumentation in services;
- OpenTelemetry Collector for OTLP;
- Prometheus-compatible metrics backend;
- lightweight trace backend;
- lightweight log backend or structured-log collector;
- Spring Boot Actuator-style health endpoints where applicable.

Product choice is implementation detail; Blast Radius talks to adapters, not vendor schemas.

### Failure/chaos driver
A local-only component/script must support controlled scenarios such as:
- stop/unavailable PostgreSQL;
- injected DB connection failure;
- service stop;
- latency injection;
- HTTP error injection;
- optional network interruption where practical.

It must emit a `FailureExperiment` event containing target, timestamp, failure type and expected containment boundary.

## Docker networking
All containers run on a dedicated Compose network and resolve dependencies by service name. Health checks and startup dependencies must avoid false incident signals during normal boot.

## Local container names
Explicitly set `container_name` for each application/infrastructure service in the
local lab, using these names as services are introduced:

```text
blast-radius-api
payment-service
customer-service
document-service
postgres
traffic-generator
otel-collector
prometheus
tempo
loki
```

Do not add a `blast-radius-engine-` prefix. Phase 1 contains only `blast-radius-api`;
the remaining services are added when implemented. Keep the Compose configuration
compatible with Podman and validate it with `podman compose config`.

Compose **service names** remain the canonical DNS names for service-to-service
communication. Application configuration must never depend on `container_name`,
container IP addresses or localhost for reaching another container. Localhost in a
container health check refers only to that container itself.

Explicit names are a local development convenience, not a production deployment
assumption. They also mean only one instance of this named lab can run per container
engine at a time.

## Local image names
Set an explicit `image` alongside `build` for every locally built application:

| Compose service | Image |
| --- | --- |
| `blast-radius-api` | `blast-radius-api:latest` |
| `payment-service` | `payment-service:latest` |
| `customer-service` | `customer-service:latest` |
| `document-service` | `document-service:latest` |
| `traffic-generator` | `traffic-generator:latest` |

Do not let Compose generate application image names such as
`blast-radius-engine-blast-radius-api` or `blast-radius-engine-payment-service`.
These `latest` tags are for the local development lab only.

PostgreSQL, Prometheus, OpenTelemetry Collector, Tempo and Loki retain their
official upstream image names with explicitly pinned versions when introduced.
Do not rename or rebuild infrastructure images merely to match container names.
Image and container naming never change the canonical internal DNS names:
application configuration continues to use Compose service names.

## Healthy baseline
Before injecting failure:
1. database is healthy;
2. all services report healthy;
3. traffic crosses the full chain;
4. logs are available;
5. request/error/latency metrics are available;
6. a distributed trace spans payment -> customer -> document -> database/client boundary;
7. Blast Radius reports no active blast-radius incident for the baseline window.

## Canonical failure
At T0, make target PostgreSQL unavailable while traffic continues.

Expected evidence:
- postgres health/down or controlled-failure event;
- document-service connection errors, failed spans, elevated 5xx/error rate and/or latency;
- customer-service downstream timeout/error evidence;
- payment-service evidence according to configured resilience behavior.

The test must demonstrate a component that is theoretically reachable but not observed affected in at least one scenario.

## Telemetry adapter expectations
Local adapters must map backend-specific data to `TelemetryBundle`. They must expose coverage status and provider warnings.

No test may bypass the normalized contract by placing backend-specific payloads directly in domain classes.

## Sanitization test data
The lab must contain explicit synthetic PII/secret-like canaries used only to prove redaction. They must be clearly fake and must not survive into normalized evidence, application output, exports or AI context.

## Developer experience target
The final implementation should support a small command set such as:
```bash
docker compose up --build -d
# wait for health
./scripts/run-baseline.sh
./scripts/inject-postgres-failure.sh
./scripts/analyze-latest.sh
./scripts/restore.sh
docker compose down -v
```

Exact scripts may change during implementation, but a new developer must be able to reproduce the demo from the README without external credentials.

## Local success criteria
- all containers become healthy;
- traffic and telemetry are visible;
- controlled failure is reproducible;
- Blast Radius identifies theoretical/observed impact;
- propagation timeline is evidence-backed;
- telemetry coverage is explicit;
- containment assessment works when experiment metadata is supplied;
- recovery can restore the healthy baseline.

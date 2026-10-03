# Local Container Lab (Podman primary)

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

Do not add a `blast-radius-engine-` prefix. Phase 3 contains all ten services listed
above. Keep the Compose configuration
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

## Full-lab healthy baseline (later phases)
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
podman compose up --build -d
# wait for health
./scripts/run-baseline.sh
./scripts/inject-postgres-failure.sh
./scripts/analyze-latest.sh
./scripts/restore.sh
podman compose down -v
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


## Implemented Phase 2 contract

The synthetic runtime path is traffic-generator → payment-service → customer-service
→ document-service → PostgreSQL. The Blast Radius API remains independent. Service
ports are 8080/8081/8082/8083 respectively and PostgreSQL uses 5432. Only document
has database dependencies; the Blast Radius domain is unchanged.

- Payment `POST /api/payments`: validates synthetic IDs, positive decimal amount and
  currency; calls customer and returns 201 only after the downstream receipt arrives.
- Customer `POST /api/customers/validate`: validates synthetic references, calls
  document, and returns 200 with a document receipt.
- Document `POST /api/documents`: transactionally inserts and reads a real row,
  returning 201 after transaction completion.
- Traffic: standard-library Python HTTP requests to payment only, with per-transaction
  UUID correlation IDs and configurable interval/enabled state.

Spring RestClient uses configuration-based service URLs, explicit connect/read
limits and no application retries/fallbacks. Failure bodies identify the immediate
dependency and downstream HTTP status without forwarding raw error messages.
Database unavailability maps to document 503, then customer 502, then payment 502.
Correlated logs identify all hops. No request payloads are logged.

`X-Correlation-ID` is preserved when safe (1–128 alphanumeric/dot/underscore/hyphen
characters), generated when absent, rejected when unsafe, and removed from MDC at
request completion. Document stores the ID with the row for verification.

The official `docker.io/library/postgres:17.6` image uses a named volume. Flyway
migration V1 belongs to document-service and creates `synthetic_documents` with
UUID primary key, document/customer references, timestamp and correlation ID.
The small Spring JDBC repository inserts and reads within a transaction. Credentials
come from the ignored root `.env`, initialized using the synthetic `.env.example`.
Database initialization is versioned; it does not rely on first-volume-only init scripts.

Health-based Compose startup orders postgres → document → customer → payment →
traffic. All mock services expose Actuator liveness/readiness, and document readiness
includes the database. Payment/customer readiness intentionally does not recursively
probe downstream services. Runtime business failures remain visible through the APIs.

Use the exact [README commands](../README.md) for build/start/status/health/manual
request/logs/failure/recovery/shutdown. `python3 scripts/verify-phase2.py` checks real
persistence, correlated failure at every hop, and recovery without an application
rebuild. It restores PostgreSQL even when a failure assertion fails. A passing
container startup alone is not Phase 2 acceptance.

The earlier full-lab analysis/normalization criteria remain future requirements.
Phase 3 implements evidence collection only; it does not implement analysis.

## Implemented Phase 3 observability

The [README](../README.md#phase-3-telemetry-and-reproducible-experiments) contains
exact versions, ports, build/query/experiment commands and retention/memory limits.
The [verification report](PHASE3_VERIFICATION.md) records actual retrieved evidence.

```text
payment/customer/document -- Java agent / OTLP HTTP --> otel-collector:4318
                                                       | traces -> tempo:4318
                                                       | logs   -> loki:3100/otlp
API/payment/customer/document -- /actuator/prometheus --> prometheus (5s scrape)
API/payment/customer/document -- /actuator/health/* --> verification script
```

Only service/environment attributes become Loki index labels. Correlation ID and
trace/span IDs are queryable structured metadata. Backend API responses preserve
timestamps and service identity; verification records health observation time and
local environment. Micrometer exposes count/status/latency, JVM/process, HTTP client
and Hikari pool metrics. No per-request IDs are metric tags.

No instrumentation SDK/vendor is introduced into the Blast Radius domain. The
traffic generator remains Python stdlib; tracing begins at payment. The API exposes
metrics/health independently of the synthetic trace chain. Direct health probes
retain document DB readiness vs process liveness; upstream readiness is not made
artificially dependent on the database.

Telemetry export queues are bounded and independent of business calls. Brief
backend loss may delay or lose data; metrics and health remain available when the
Collector stops. Coverage classification is future Phase 4 work. Application
requests still have no retries, circuit breakers or fallback success.

The local ARM64 stack uses no socket mounts, host networking or Docker Desktop.
Loopback host bindings protect unauthenticated development query endpoints from
remote access. Collector, Tempo and Loki expose API readiness rather than adding
shells to their upstream images. PostgreSQL retains its named volume; telemetry
backends use short-retention disposable storage.

## Phase 6 retained knowledge

The API now owns a `topology-data` named volume. Runtime discovery automatically retains scoped dependency knowledge with first/last observation times and a configurable seven-day TTL; raw Tempo history remains disposable. The engine's deterministic graph has no storage/vendor dependency. See [topology retention](TOPOLOGY_RETENTION.md) for settings and the strictly offline acceptance bootstrap. During the current outage acceptance, use API-only Compose operations with `--no-deps`; do not start PostgreSQL or run recovery without approval.

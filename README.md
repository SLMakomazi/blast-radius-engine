# MadlangaAI Blast Radius Engine

Full-capability Blast Radius proof-of-concept for future integration into **MadlangaAI Phase 4**.

## Core principle
> **Dependency topology calculates potential blast radius; full runtime telemetry validates observed blast radius; AI explains sanitized evidence.**

The planned engine supports **logs, metrics, distributed traces and endpoint/application health**. Current MadlangaAI/Datadog requirements expose only part of that set, so missing capabilities are proven locally behind replaceable provider contracts rather than removed from the design.

## Target local-first proof
The target repository runs a complete synthetic environment with Docker Compose:

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

The services emit logs, metrics, traces and health signals. A controlled failure driver can break PostgreSQL/services while traffic continues, allowing the engine to calculate theoretical vs observed impact and propagation.

## Planned analysis results
- suspected origin + confidence;
- direct/indirect theoretical impact;
- observed impact;
- dependency paths;
- propagation timeline;
- telemetry coverage/data gaps;
- evidence/provenance;
- deterministic incident severity;
- optional chaos containment assessment;
- sanitized AI diagnosis/remediation context.

## Current status — Phase 5 deterministic graph engine

Phase 5 implementation and verification are complete on `feat/deterministic-graph-engine`. **Final clean Maven regression passed on 2026-10-02: 125 tests, 0 failures, 0 errors, 0 skipped.** The separate live topology-discovery acceptance also passed against real Docker/Tempo evidence. See [Phase 5 verification](docs/PHASE5_VERIFICATION.md).

The graph engine is deliberately independent of topology discovery. Engineers do **not** need to hand-author a graph for every monitored application. The core consumes the `DependencyTopologyProvider` port; adapters can populate that contract from MadlangaAI's architecture/dependency model, runtime trace discovery, service catalogs/cloud metadata, or local fixtures. Test fixtures in this repository are deterministic test inputs, not a production onboarding requirement.

Phase 5 adds:

- technology-neutral component nodes and directed dependency edges;
- separate component type and technology metadata;
- topology validation and duplicate-edge normalization;
- provider-neutral `DependencyTopologyProvider`;
- reverse dependency traversal from a supplied origin;
- minimum hop distance and dependency path;
- DIRECT vs INDIRECT theoretical impact;
- deterministic ordering and deterministic shortest-path tie breaking;
- fan-out, fan-in, cycles, disconnected components and duplicate-edge coverage.

Phase 5 calculates **theoretical** impact only. Runtime evidence correlation and observed impact remain Phase 6.

Phase 5 adds 16 regular graph/topology/discovery tests (11 graph-engine + 3 topology-domain + 2 trace-discovery); all passed as part of the 125-test Blast Radius API regression suite. The opt-in live trace-discovery integration test passed separately.

## Current status — Phase 4 normalized telemetry

Phase 4 introduces the provider-neutral telemetry domain boundary. The Blast Radius
domain now consumes a `TelemetryProvider` port rather than raw Loki/Prometheus/Tempo/
Actuator responses directly. All provider-specific data is normalized, sanitized and
mapped to typed evidence models before entering the domain.

**Phase 4 implementation and verification are complete on `feat/normalized-telemetry-sanitization`.**
The final regression pass completed 109 Blast Radius API tests plus 39 supporting-service tests with no failures, and the opt-in live `LocalTelemetryProviderLiveIT` passed against real Loki, Prometheus, Tempo and Actuator providers. See [Phase 4 verification](docs/PHASE4_VERIFICATION.md) for the runtime findings and acceptance evidence.

### What Phase 4 adds

| Layer | What was added |
|---|---|
| `domain/evidence` | `LogEvidence`, `MetricEvidence`, `SpanEvidence`, `HealthEvidence`, `TelemetryBundle`, `TelemetryCoverage`, `CoverageStatus`, `HealthState`, `SpanStatus`, `EvidenceProvenance`, `EvidenceFamily`, `TelemetryQuery` |
| `ports` | `TelemetryProvider` interface |
| `sanitization` | `TelemetrySanitizer`, `RedactionRule`, `BuiltInRedactionRules`, `RedactionPlaceholders` |
| `adapters/telemetry/loki` | `LokiLogAdapter` + internal DTOs + `LokiProperties` |
| `adapters/telemetry/prometheus` | `PrometheusMetricsAdapter` + internal DTOs + `PrometheusProperties` |
| `adapters/telemetry/tempo` | `TempoTraceAdapter` + internal DTOs + `TempoProperties` |
| `adapters/telemetry/health` | `ActuatorHealthAdapter` + internal DTOs + `ActuatorHealthProperties` |
| `adapters/telemetry` | `LocalTelemetryProvider` (composite, implements port) |
| `config` | `TelemetryAdapterConfig` |
| `application.yml` | `blast-radius.telemetry.*` configuration block |
| Tests | Unit/regression coverage plus opt-in real-provider integration verification |

### Architectural guarantees introduced in Phase 4

- The domain never imports provider-specific DTOs (Loki/Prometheus/Tempo/Actuator types).
- All evidence is sanitized before construction (Authorization, Bearer tokens, passwords,
  API keys, access tokens, cookies/sessions, SA ID number fields).
- `CoverageStatus.UNAVAILABLE` for any family **does not** mean services are healthy.
- Provider failures are isolated — one unavailable backend does not suppress other families.
- Every evidence item retains full `EvidenceProvenance` (family, provider, collectedAt, sourceRef).

Phase 3 observability stack (Loki, Prometheus, Tempo, Actuator) is fully preserved and
unchanged. Analysis algorithms, graph traversal, AI, persistence and UI remain deferred.

See the [Phase 3 verification report](docs/PHASE3_VERIFICATION.md) for observed
telemetry, outage/recovery results and resource measurements. The historical
[Phase 2 report](docs/PHASE2_VERIFICATION.md) records the original business proof.

| Service | Responsibility / endpoint | Host and container port | Local image / container |
| --- | --- | --- | --- |
| blast-radius-api | Foundation; `GET /actuator/health` | 8080 | `blast-radius-api:latest` / `blast-radius-api` |
| payment-service | Validate synthetic payment; `POST /api/payments` | 8081 | `payment-service:latest` / `payment-service` |
| customer-service | Validate synthetic references; `POST /api/customers/validate` | 8082 | `customer-service:latest` / `customer-service` |
| document-service | Insert/read a document; `POST /api/documents` | 8083 | `document-service:latest` / `document-service` |
| postgres | Synthetic document storage | 5432 | `docker.io/library/postgres:17.6` / `postgres` |
| traffic-generator | Send transactions to payment only | None | `traffic-generator:latest` / `traffic-generator` |

All Spring applications expose health and `/actuator/prometheus`. The mock services additionally expose
`/actuator/health/liveness` and `/actuator/health/readiness`; document readiness
includes its database. No `/actuator/env` endpoint is exposed.

## Build and test locally

Prerequisites: JDK 21, Maven 3.9.x (images use 3.9.16), and Python 3.9+ for tests and
the acceptance script. Set `JAVA_HOME` to JDK 21. Run from the repository root:

```bash
mvn -f blast-radius-api/pom.xml clean verify
mvn -f mock-services/payment-service/pom.xml clean verify
mvn -f mock-services/customer-service/pom.xml clean verify
mvn -f mock-services/document-service/pom.xml clean verify
python3 -m unittest discover -s traffic-generator -v
```

The mock service tests exercise full Spring contexts with MockMvc. HTTP dependencies
use Spring's mock HTTP server; document tests use Flyway with H2 in PostgreSQL mode.
Those tests are supplemented by the real PostgreSQL acceptance sequence below.
Mockito uses its subclass mock maker in the new services; no final-class mocking or
Java agent is needed for these tests.

To run only the independent Blast Radius API from source or its packaged JAR:

```bash
mvn -f blast-radius-api/pom.xml spring-boot:run
# Alternative after building:
java -jar blast-radius-api/target/blast-radius-api.jar
```

## Podman local lab

Prerequisites: Podman, a running Podman machine on macOS/Windows, and a Compose
provider supporting `depends_on: condition: service_healthy`. Docker Desktop and
Docker socket mounts are not required. Initial builds download upstream images and
Maven dependencies. On macOS, if Podman is installed outside PATH:

```bash
export PATH="/opt/podman/bin:$PATH"
podman machine list
# Only if the existing machine is stopped:
# podman machine start
```

Copy the deliberately synthetic environment template once. Do not overwrite an
existing customized `.env`. Keep production credentials out of this lab.

```bash
cp -n .env.example .env
podman compose config --quiet
podman compose build
podman compose up -d
podman compose ps
```

Health-based dependencies order startup: PostgreSQL → document → customer → payment
→ traffic. The API starts independently. Wait for services to report healthy in
`podman compose ps`, then check:

```bash
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8081/actuator/health
curl --fail http://localhost:8082/actuator/health
curl --fail http://localhost:8083/actuator/health
```

Expected: HTTP 200 and `"status":"UP"`. Host ports bind only to loopback. Inside the
network, applications use service DNS names: `customer-service:8082`,
`document-service:8083`, and `postgres:5432`. Application configuration never relies
on `container_name`, container IPs or localhost for cross-container calls. Localhost
in health checks refers only to the container checking itself.

Explicit application image/container names are local conventions, not production
assumptions. Infrastructure keeps official upstream names and pinned versions.
Only one instance of this named lab can run on a container engine at a time.

### Docker Desktop portability

Podman is the primary documented local runtime, but the Compose configuration and
OCI images are intentionally portable. The same lab was also verified with Docker
Desktop during Phase 3 recovery testing. Substitute `docker compose` for
`podman compose` when Docker is the active runtime. Verification commands that
control containers accept `--runtime docker` where documented. Do not run the
same named lab simultaneously on both engines.

## Manual healthy request and persistence proof

```bash
curl -i --max-time 20 -X POST http://localhost:8081/api/payments \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-ID: phase2-manual-test-001' \
  --data-binary @fixtures/payment-request.json

podman compose exec -T postgres sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT id, document_reference, customer_reference, created_at, correlation_id FROM synthetic_documents WHERE correlation_id = '\''phase2-manual-test-001'\'';"'

podman compose logs --tail=100 payment-service customer-service document-service traffic-generator
```

The payment endpoint returns HTTP 201 with `PROCESSED`, an ephemeral payment UUID,
the persisted document receipt, and the correlation ID. Customer returns HTTP 200
with `VALIDATED`; document returns HTTP 201. Payments/customers are not persisted.
Each accepted transaction creates a new document row, even for repeated references.

Valid caller correlation IDs (1–128 letters, digits, dots, underscores or hyphens)
are preserved in `X-Correlation-ID`, response bodies, downstream calls, logging MDC
and the database row. Missing IDs become UUIDs; unsafe IDs are rejected with HTTP
400. References must use `SYNTH-CUST-` / `SYNTH-DOC-`; input values and raw downstream
error bodies are not logged. Currency is a three-letter code and amount is positive
with at most two decimal places. Validation is deliberately synthetic, not a real
customer verification or financial processing integration.

## PostgreSQL failure and recovery

Keep the applications running. This intentionally interrupts only the local target
database; the volume is preserved.

```bash
podman compose stop postgres
curl -i --max-time 20 -X POST http://localhost:8081/api/payments \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-ID: phase2-manual-failure-001' \
  --data-binary @fixtures/payment-request.json
curl -i http://localhost:8083/actuator/health/readiness
podman compose logs --tail=100 document-service customer-service payment-service

podman compose start postgres
# Poll boundedly until database-backed document health recovers:
curl --fail --retry 30 --retry-delay 2 --retry-max-time 120 --retry-all-errors \
  http://localhost:8083/actuator/health
curl -i --max-time 20 -X POST http://localhost:8081/api/payments \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-ID: phase2-manual-recovery-001' \
  --data-binary @fixtures/payment-request.json
```

During failure, document returns 503 `DATABASE_UNAVAILABLE`; customer sees document's
503 and returns 502; payment sees customer's 502 and returns 502 to the caller.
Each hop logs its immediate dependency and correlation ID. Response bodies contain
no raw database/HTTP exceptions or credentials. Document readiness becomes DOWN,
while its liveness and the independent API remain UP. Payment/customer health is
local process readiness, not a recursive claim about the whole request chain.
Normal requests resume after PostgreSQL restarts, without rebuilding applications.
Application requests have no fallback, circuit breaker or retry policy; the `curl`
retry above is only a recovery health poll.

For repeatable assertions, including persisted rows and matching failure logs:

```bash
python3 scripts/verify-phase2.py
```

This script checks all four health endpoints, makes a payment, verifies the row,
stops PostgreSQL, asserts failure at each hop, restarts PostgreSQL in a `finally`
block, and asserts recovery and retention of the original row. It must run against
this synthetic local stack only. Set `PODMAN` to the executable path if necessary.

## Configuration, logs and shutdown

`CUSTOMER_SERVICE_URL` and `DOCUMENT_SERVICE_URL` configure downstream HTTP URLs;
`HTTP_CONNECT_TIMEOUT` / `HTTP_READ_TIMEOUT` configure bounded client timeouts.
The defaults allow the database's 3-second connection-pool timeout to propagate
through customer (8 seconds) and payment (12 seconds). Document reads `DATABASE_URL`,
`POSTGRES_USER` and `POSTGRES_PASSWORD`. Flyway owns schema initialization/versioning.
The named `postgres-data` volume survives container restarts and normal shutdown.

Traffic uses `TARGET_URL`, `REQUEST_INTERVAL` (seconds, 0.1–3600), and `ENABLED`.
Compose exposes interval as `REQUEST_INTERVAL` and the enabled switch as
`TRAFFIC_ENABLED` in `.env`. Disabled traffic waits without sending requests; after
changing `.env`, apply it with `podman compose up -d traffic-generator`.

```bash
podman compose logs -f payment-service customer-service document-service traffic-generator
podman compose ps
podman compose down
# Optional deliberate reset: deletes all synthetic database records
# podman compose down -v
```

## Phase 3 telemetry and reproducible experiments

| Component / pinned official image | Host API (loopback only) | Internal ingestion |
| --- | --- | --- |
| Collector `docker.io/otel/opentelemetry-collector-contrib:0.157.0` | 13133 health | OTLP HTTP 4318 |
| Prometheus `docker.io/prom/prometheus:v3.14.0` | 9090 | Scrapes service DNS at 5-second intervals |
| Tempo `docker.io/grafana/tempo:2.10.7` | 3200 query/readiness | OTLP HTTP 4318 |
| Loki `docker.io/grafana/loki:3.7.8` | 3100 query/readiness | Native OTLP `/otlp/v1/logs` |

The three mock images include checksum-verified OpenTelemetry Java agent **2.31.1**.
Compose activates it through `JAVA_TOOL_OPTIONS` and the shared
`infrastructure/observability/otel/javaagent.properties`. Local Maven tests/source
runs do not activate the agent. Standard W3C trace context links HTTP/JDBC spans.
`X-Correlation-ID` remains a separate business ID, present in response headers,
logging MDC and the database; it is never used as a trace ID. Logs carry the active
trace/span IDs, allowing correlation-ID → trace lookup. Python traffic stays small
and uninstrumented; its requests create traces at payment-service.

Metrics: all four Spring applications → `/actuator/prometheus` → Prometheus.
Logs: controlled application Logback events → agent OTLP → Collector → Loki.
Traces: agent HTTP/JDBC instrumentation → OTLP → Collector → Tempo.
Health: direct Actuator root/liveness/readiness calls, recorded by the verification
script. Prometheus `up` means scraping works; it is not database readiness.

After the build/start commands above, run:

```bash
./scripts/verify-observability.sh --output /tmp/blast-radius-phase3
# Original business acceptance remains available:
python3 scripts/verify-phase2.py
```

The Phase 3 script makes uniquely named healthy/failure/recovery requests, proves
real rows, retrieves all four evidence families and saves timestamped JSON evidence.
It deliberately stops PostgreSQL, then restores it in `finally`. It also stops the
Collector, verifies HTTP 201/persistence/metrics/health while new logs and traces are
unavailable, restores the Collector, waits for agent export reconnection using fresh synthetic
probe transactions, and verifies telemetry again. Collector readiness alone does
not guarantee exporter reconnection; probes record gaps rather than claiming replay. Run only against
this synthetic lab and avoid running both experiment scripts concurrently.
A failed assertion exits nonzero; container startup alone is not acceptance.

For a bounded, fresh-evidence check without stopping any services:

```bash
./scripts/verify-observability.sh --availability-only \
  --availability-timeout 60 --poll-interval 1
# Only the Collector timeout + recovery cases (no PostgreSQL experiment):
./scripts/verify-observability.sh --availability-cases \
  --availability-timeout 60 --poll-interval 1 --timeout-case-seconds 8
```

The same focused checks can use Docker Desktop without changing Compose or business
configuration (stop Podman first to avoid port conflicts):

```bash
DOCKER_CONTEXT=desktop-linux ./scripts/verify-observability.sh \
  --runtime docker --availability-cases \
  --availability-timeout 60 --poll-interval 1 --timeout-case-seconds 8
```

The availability probe uses unique correlation and W3C trace IDs, requires logs from
all three services, a connected trace including JDBC evidence, counters advancing
with fresh scrape timestamps, and current health. One monotonic deadline bounds
network calls and polling. Missing families are listed in JSON; `--availability-only`
exits 1 on timeout and never calls missing evidence healthy. These probes belong to
verification only. A DOWN health response or error span is still available evidence;
availability does not mean business health. Business requests never wait for observability. The focused
cases restore the Collector in `finally`, then prove fresh telemetry after recovery.

For manual API inspection (use the actual correlation/trace ID printed by the script):

```bash
curl --fail http://localhost:13133/
curl --fail http://localhost:9090/-/ready
curl --fail http://localhost:3200/ready
curl --fail http://localhost:3100/ready
curl --fail --get http://localhost:9090/api/v1/query \
  --data-urlencode 'query=http_server_requests_seconds_count{uri=~"/api/payments|/api/customers/validate|/api/documents"}'
curl --fail --get http://localhost:3100/loki/api/v1/query_range \
  --data-urlencode 'query={service_name=~"payment-service|customer-service|document-service"} | correlation_id="REPLACE_WITH_CORRELATION_ID"' \
  --data-urlencode 'since=10m'
curl --fail -H 'Accept: application/json' \
  http://localhost:3200/api/traces/REPLACE_WITH_TRACE_ID
podman compose logs --tail=100 otel-collector prometheus tempo loki
podman stats --no-stream
```

There are no business startup dependencies on observability. Agent/exporter queues
are bounded and asynchronous; a backend outage may buffer briefly or lose telemetry,
but does not create fallback business success or application retries. Collector
health and Tempo/Loki `/ready` are checked through host APIs; their minimal upstream
images are not rebuilt just to add a health-check shell. Compose reports them as
running, while applications/PostgreSQL/Prometheus have container health checks.

All ten containers have memory limits (2,816 MiB total). Agent-enabled JVM heaps are
160 MiB, with bounded metaspace/code cache and Serial GC. The API heap is 96 MiB.
This is a low-throughput lab, not a capacity guarantee; see measured use in the
verification report. No Podman Machine settings are changed. Prometheus retains
2 hours / 128 MB of blocks, Tempo 1 hour and Loki 24 hours (its minimum retention
period); compaction/active data may exceed these targets temporarily. Observability
storage is disposable container storage and is lost on recreation. PostgreSQL uses
its existing named volume. Stop the lab when finished to stop traffic/disk growth.

Loki indexes only stable service/environment attributes, not correlation/trace/span
IDs. Collector allowlists resource/span attributes, removes exception messages and
stack traces, and exports only controlled application logger scopes. Header/body
capture and SQL parameter capture are disabled. Framework diagnostics remain local
container logs; centralized logs are intentionally not a full container-log archive.
No real data, production tokens or credentials belong in this lab.

## Repository and package boundaries

- `blast-radius-api/`: independently buildable Spring Boot API.
- `mock-services/`: independent payment, customer and document applications.
- `infrastructure/`: database documentation and Collector/Prometheus/Tempo/Loki configuration; reserved failure-driver folder.
- `traffic-generator/`: lightweight synthetic payment traffic and tests.
- `fixtures/`: synthetic payment request; reserved topology, telemetry, incident and experiment data.
- `scripts/`: repeatable Phase 2 and Phase 3 acceptance checks; `docker/`: reserved shared Docker assets.
- `docs/`: existing requirements, architecture and planning documents.

Within `com.madlanga.blastradius`, `controller` and `dto` will own HTTP contracts;
`service` will orchestrate use cases. `domain` contains independent model, graph,
correlation, evidence and severity logic. Future domain code must not depend on
Spring, controllers, adapters or vendor SDKs. `ports` will define integration
contracts; `adapters` will implement telemetry, topology, AI and persistence access.
`sanitization` reserves the redaction boundary; `config` and `exception` reserve
framework wiring and API error handling. No placeholder classes or speculative
interfaces are added at this stage.

PostgreSQL is only the first local test dependency. The domain remains open to
MongoDB, Oracle, IBM DB2, SQL Server, IBM MQ, AWS SQS/SNS, ActiveMQ, Lambda,
Step Functions, Spring Boot, Node.js, Angular and other technologies through generic
component types and separate technology metadata when models are introduced.

## Documentation
Start with [CODEX.md](CODEX.md).

- [Source Alignment](docs/SOURCE_ALIGNMENT.md)
- [Product Requirements](docs/PRODUCT_REQUIREMENTS.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Blast Radius Specification](docs/BLAST_RADIUS_SPEC.md)
- [Telemetry Contract](docs/TELEMETRY_CONTRACT.md)
- [Local Docker Lab](docs/LOCAL_LAB.md)
- [MadlangaAI Integration](docs/INTEGRATION.md)
- [Implementation Plan](docs/IMPLEMENTATION_PLAN.md)
- [Test Strategy](docs/TEST_STRATEGY.md)
- [UI/UX Concept](docs/UI_UX_CONCEPT.md)
- [Architecture Decisions](docs/DECISIONS.md)

## Scope
This repository must prove all capabilities required by Blast Radius locally, even when the current MadlangaAI MVP does not yet expose the corresponding telemetry source. It does not perform autonomous production remediation or own production chaos orchestration.

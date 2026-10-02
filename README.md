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

## Current status — Phase 2 synthetic service chain

The repository now contains the independent Blast Radius API plus three Java 21 /
Spring Boot 4.1.1 synthetic services, PostgreSQL and a lightweight Python traffic
generator. Real HTTP calls and document persistence form the dependency chain.
The API remains outside the business request path. No analysis algorithms, AI,
telemetry normalization or observability stack are implemented.

The real healthy → PostgreSQL failure → recovery sequence passed. See the
[Phase 2 verification report](docs/PHASE2_VERIFICATION.md) for exact test results,
recorded runtime evidence, the complete file inventory and component tree.

| Service | Responsibility / endpoint | Host and container port | Local image / container |
| --- | --- | --- | --- |
| blast-radius-api | Foundation; `GET /actuator/health` | 8080 | `blast-radius-api:latest` / `blast-radius-api` |
| payment-service | Validate synthetic payment; `POST /api/payments` | 8081 | `payment-service:latest` / `payment-service` |
| customer-service | Validate synthetic references; `POST /api/customers/validate` | 8082 | `customer-service:latest` / `customer-service` |
| document-service | Insert/read a document; `POST /api/documents` | 8083 | `document-service:latest` / `document-service` |
| postgres | Synthetic document storage | 5432 | `docker.io/library/postgres:17.6` / `postgres` |
| traffic-generator | Send transactions to payment only | None | `traffic-generator:latest` / `traffic-generator` |

All Spring applications expose health. The mock services additionally expose
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

## Repository and package boundaries

- `blast-radius-api/`: independently buildable Spring Boot API.
- `mock-services/`: independent payment, customer and document applications.
- `infrastructure/`: local database documentation; reserved observability/failure-driver folders.
- `traffic-generator/`: lightweight synthetic payment traffic and tests.
- `fixtures/`: synthetic payment request; reserved topology, telemetry, incident and experiment data.
- `scripts/`: repeatable Phase 2 acceptance check; `docker/`: reserved shared Docker assets.
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

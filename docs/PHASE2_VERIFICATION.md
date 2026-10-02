# Phase 2 implementation and verification report

Branch: `feat/local-synthetic-service-chain`. No commit, push, merge or PR.

## Implementation

The chain is traffic-generator → payment → customer → document → PostgreSQL.
The Blast Radius API stays outside this request path. No engine algorithms or
observability backends were added. Each mock service is independently built using
Java 21, Spring Boot 4.1.1 and Maven, with controller/service/client/DTO/config/error
boundaries. Document has a persistence package instead of an unused HTTP client.

## Endpoints, ports and names

| Service/container | Endpoint or responsibility | Host/container port | Image |
| --- | --- | --- | --- |
| blast-radius-api | GET /actuator/health; independent API foundation | 8080 | blast-radius-api:latest |
| payment-service | POST /api/payments; validate payment, invoke customer, return 201 | 8081 | payment-service:latest |
| customer-service | POST /api/customers/validate; validate synthetic references, invoke document, return 200 | 8082 | customer-service:latest |
| document-service | POST /api/documents; transactional document insert/read, return 201 | 8083 | document-service:latest |
| postgres | Real synthetic document persistence | 5432 | docker.io/library/postgres:17.6 |
| traffic-generator | Continuous synthetic requests to payment only | None | traffic-generator:latest |

Each mock service exposes GET /actuator/health, /actuator/health/liveness and
/actuator/health/readiness. Document readiness includes DB health. All applications
hide /actuator/env. Compose service names are internal DNS names; image/container
names are local convenience only. Host ports bind to loopback.

## Persistence and correlation

Document-service owns Flyway V1 and Spring JDBC. `synthetic_documents` stores UUID
`id`, `document_reference`, `customer_reference`, `created_at` (timestamp with time
zone) and `correlation_id`, indexed for verification. A transaction inserts then
reads the row and commits before responding. The named PostgreSQL volume retains
records across outages; credentials are supplied through environment configuration.
The `.env.example` values are explicitly synthetic, and `.env` is ignored.

Each service's request filter preserves a safe supplied X-Correlation-ID or creates
a UUID if absent, stores it in request attributes/MDC, returns it in the response
header and clears MDC afterwards. IDs are passed explicitly to downstream clients
and DTOs. Malformed IDs are rejected before processing. The document row stores the
same ID, allowing verification beyond the returned JSON.

## Tests and runtime results

**PASSED.** Acceptance ran on 2026-10-01 at approximately 18:41 UTC. A follow-up
check on 2026-10-02 confirmed all five health-checked containers healthy and the
traffic generator still emitting HTTP 201 transactions.

### Local builds and tests

All Maven commands ran with Java 21 (`JAVA_HOME` selected using `java_home -v 21`):

| Command | Tests | Failures / errors / skipped | Result |
| --- | ---: | --- | --- |
| `mvn -f blast-radius-api/pom.xml --batch-mode --no-transfer-progress clean verify` | 1 | 0 / 0 / 0 | BUILD SUCCESS |
| `mvn -f mock-services/payment-service/pom.xml --batch-mode --no-transfer-progress clean verify` | 16 | 0 / 0 / 0 | BUILD SUCCESS |
| `mvn -f mock-services/customer-service/pom.xml --batch-mode --no-transfer-progress clean verify` | 11 | 0 / 0 / 0 | BUILD SUCCESS |
| `mvn -f mock-services/document-service/pom.xml --batch-mode --no-transfer-progress clean verify` | 5 | 0 / 0 / 0 | BUILD SUCCESS |
| `python3 -m unittest discover -s traffic-generator -v` | 4 | 0 / 0 / 0 | OK |

Total: 33 Java tests and 4 Python tests passed. Image builds also ran their tests.
Payment tests cover positive/missing/unsafe correlation IDs, real client request
construction, invalid amounts/currency/references, sanitized downstream HTTP and
connection failures, empty responses, health and hidden Actuator env. Customer
covers the corresponding validation and downstream boundary. Document covers
migration/context health, real JDBC insert/read, invalid input/no insertion,
correlation generation/preservation and sanitized database errors.

Initial harness issues were resolved: Mockito's inline attach was unavailable in
the local sandbox, so the mock services use the subclass mock maker; a Python HTTP
error test needed a file-like response fixture. Final suites pass. Spring's test
listener can still emit an optional Mockito attach warning; it does not fail tests.
Python syntax compilation passed with its cache redirected to `/tmp` because the
sandbox does not permit writing the system Python cache. `git diff --check` passed.

### Podman Compose

Podman 6.0.2 used the existing running machine and its configured external Compose
provider, Docker Compose v5.5.1. This provider talks to Podman; Docker Desktop and
Docker socket mounts are not required. The Podman binary was outside the shell PATH
and was invoked as `/opt/podman/bin/podman`.

Executed successfully:

```bash
podman compose config --quiet
podman compose build
podman compose up -d
podman compose ps
```

All six services run with the requested explicit names. Podman displays short local
image tags as `docker.io/library/<service>:latest`; it does not add a project prefix.
The final source revisions were rebuilt before runtime acceptance. PostgreSQL used
the official pinned image, not a custom build.

All four `curl http://localhost:8080|8081|8082|8083/actuator/health` checks returned
HTTP 200 / UP (the ports were queried individually).

### Manual healthy request

The README payment curl with `X-Correlation-ID: phase2-manual-test-001` returned
HTTP 201 with `PROCESSED`. The response contained document UUID
`e475678d-f38c-41ab-a52a-da24222a2ad8`. A direct SQL query through `podman compose exec`
confirmed exactly one matching row:

```text
id:                 e475678d-f38c-41ab-a52a-da24222a2ad8
document_reference: SYNTH-DOC-001
customer_reference: SYNTH-CUST-001
created_at:         2026-10-01 18:41:03.685991+00
correlation_id:     phase2-manual-test-001
```

Traffic logs also showed multiple distinct synthetic transaction IDs with status 201.

### Mandatory healthy → failure → recovery experiment

`python3 scripts/verify-phase2.py` exited 0 and reported PASSED.

| Phase | Correlation ID | Observed outcome |
| --- | --- | --- |
| Healthy | `phase2-healthy-11583b410103` | HTTP 201; document `47518cf8-03ab-4a9c-a856-2741d9d63fe5`; exactly one stored row |
| PostgreSQL stopped | `phase2-failure-11583b410103` | Caller HTTP 502; correlated document/database, customer/document and payment/customer failures; zero stored rows |
| PostgreSQL restarted | `phase2-recovery-11583b410103` | HTTP 201; document `71a0e3a9-e11a-47a5-b6e7-bc54db3677ff`; exactly one new row; original healthy row retained |

Failure log excerpts for the same request:

```text
18:41:25.683Z document-service correlationId=phase2-failure-11583b410103
  event=dependency_failed dependency=postgres code=DATABASE_UNAVAILABLE
18:41:25.753Z customer-service correlationId=phase2-failure-11583b410103
  event=dependency_failed dependency=document-service downstreamStatus=503
18:41:25.821Z payment-service correlationId=phase2-failure-11583b410103
  event=dependency_failed dependency=customer-service downstreamStatus=502
```

During the outage, document readiness returned 503 / DOWN while its liveness and the
independent API remained 200 / UP. PostgreSQL was restarted with `podman compose
start postgres`; document health recovered and the next payment succeeded. No
application container was rebuilt or restarted during recovery. The script verified
that the failed request created no row and that the earlier successful row survived.

No required build, test or acceptance command remains unexecuted. The stack is left
running for review; use `podman compose down` to stop it while preserving the volume.

## Deliberate limitations and deferrals

- H2 Maven persistence tests complement, but do not replace, real PostgreSQL checks.
- Customer validation is synthetic reference validation, not a real customer lookup.
- Payments are not persisted; every successful request creates a fresh document.
- No automatic application retry, fallback or circuit breaker hides outages.
- Payment/customer readiness is local readiness; document readiness includes DB.
- Continuous traffic accumulates synthetic rows; normal shutdown retains the volume.
- Explicit names and `latest` application tags are local conventions, not production policy.
- Phase 3 adds OpenTelemetry and logs/metrics/traces collection. No Datadog, AI/LLM,
  graph traversal, impact/severity/origin logic, chaos platform or UI was introduced.

## File inventory

### Created (version-controlled candidates)

```text
.env.example
docs/PHASE2_VERIFICATION.md
fixtures/payment-request.json
infrastructure/database/postgres/README.md
mock-services/customer-service/.dockerignore
mock-services/customer-service/Dockerfile
mock-services/customer-service/pom.xml
mock-services/customer-service/src/main/java/com/madlanga/lab/customer/CustomerApplication.java
mock-services/customer-service/src/main/java/com/madlanga/lab/customer/client/DocumentClient.java
mock-services/customer-service/src/main/java/com/madlanga/lab/customer/config/CorrelationIdFilter.java
mock-services/customer-service/src/main/java/com/madlanga/lab/customer/config/HttpClientConfig.java
mock-services/customer-service/src/main/java/com/madlanga/lab/customer/controller/CustomerController.java
mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/ApiError.java
mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/CustomerRequest.java
mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/CustomerValidation.java
mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/DocumentReceipt.java
mock-services/customer-service/src/main/java/com/madlanga/lab/customer/dto/DownstreamRequest.java
mock-services/customer-service/src/main/java/com/madlanga/lab/customer/exception/ApiExceptionHandler.java
mock-services/customer-service/src/main/java/com/madlanga/lab/customer/exception/DownstreamException.java
mock-services/customer-service/src/main/java/com/madlanga/lab/customer/service/CustomerService.java
mock-services/customer-service/src/main/resources/application.yml
mock-services/customer-service/src/test/java/com/madlanga/lab/customer/CustomerApplicationTests.java
mock-services/customer-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker
mock-services/document-service/.dockerignore
mock-services/document-service/Dockerfile
mock-services/document-service/pom.xml
mock-services/document-service/src/main/java/com/madlanga/lab/document/DocumentApplication.java
mock-services/document-service/src/main/java/com/madlanga/lab/document/config/CorrelationIdFilter.java
mock-services/document-service/src/main/java/com/madlanga/lab/document/controller/DocumentController.java
mock-services/document-service/src/main/java/com/madlanga/lab/document/dto/ApiError.java
mock-services/document-service/src/main/java/com/madlanga/lab/document/dto/DocumentReceipt.java
mock-services/document-service/src/main/java/com/madlanga/lab/document/dto/DocumentRequest.java
mock-services/document-service/src/main/java/com/madlanga/lab/document/exception/ApiExceptionHandler.java
mock-services/document-service/src/main/java/com/madlanga/lab/document/persistence/DocumentRepository.java
mock-services/document-service/src/main/java/com/madlanga/lab/document/service/DocumentService.java
mock-services/document-service/src/main/resources/application.yml
mock-services/document-service/src/main/resources/db/migration/V1__create_synthetic_documents.sql
mock-services/document-service/src/test/java/com/madlanga/lab/document/DocumentApplicationTests.java
mock-services/document-service/src/test/java/com/madlanga/lab/document/DocumentFailureTests.java
mock-services/document-service/src/test/resources/application-test.yml
mock-services/document-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker
mock-services/payment-service/.dockerignore
mock-services/payment-service/Dockerfile
mock-services/payment-service/pom.xml
mock-services/payment-service/src/main/java/com/madlanga/lab/payment/PaymentApplication.java
mock-services/payment-service/src/main/java/com/madlanga/lab/payment/client/CustomerClient.java
mock-services/payment-service/src/main/java/com/madlanga/lab/payment/config/CorrelationIdFilter.java
mock-services/payment-service/src/main/java/com/madlanga/lab/payment/config/HttpClientConfig.java
mock-services/payment-service/src/main/java/com/madlanga/lab/payment/controller/PaymentController.java
mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/ApiError.java
mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/CustomerValidation.java
mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/DocumentReceipt.java
mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/DownstreamRequest.java
mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/PaymentRequest.java
mock-services/payment-service/src/main/java/com/madlanga/lab/payment/dto/PaymentResult.java
mock-services/payment-service/src/main/java/com/madlanga/lab/payment/exception/ApiExceptionHandler.java
mock-services/payment-service/src/main/java/com/madlanga/lab/payment/exception/DownstreamException.java
mock-services/payment-service/src/main/java/com/madlanga/lab/payment/service/PaymentService.java
mock-services/payment-service/src/main/resources/application.yml
mock-services/payment-service/src/test/java/com/madlanga/lab/payment/PaymentApplicationTests.java
mock-services/payment-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker
scripts/verify-phase2.py
traffic-generator/.dockerignore
traffic-generator/Dockerfile
traffic-generator/test_traffic.py
traffic-generator/traffic.py
```

### Modified

```text
.gitignore
README.md
blast-radius-api/Dockerfile
docker-compose.yml
docs/DECISIONS.md
docs/IMPLEMENTATION_PLAN.md
docs/LOCAL_LAB.md
```

### Removed empty-directory markers

```text
infrastructure/database/postgres/.gitkeep
mock-services/customer-service/.gitkeep
mock-services/document-service/.gitkeep
mock-services/payment-service/.gitkeep
scripts/.gitkeep
traffic-generator/.gitkeep
```

Local-only: `.env` copied from the synthetic example (ignored), Maven `target/` output and temporary verification logs.

## Final Phase 2 component tree

```text
mock-services/
├── customer-service/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/
│   │   │   │       └── madlanga/
│   │   │   │           └── lab/
│   │   │   │               └── customer/
│   │   │   │                   ├── client/
│   │   │   │                   │   └── DocumentClient.java
│   │   │   │                   ├── config/
│   │   │   │                   │   ├── CorrelationIdFilter.java
│   │   │   │                   │   └── HttpClientConfig.java
│   │   │   │                   ├── controller/
│   │   │   │                   │   └── CustomerController.java
│   │   │   │                   ├── dto/
│   │   │   │                   │   ├── ApiError.java
│   │   │   │                   │   ├── CustomerRequest.java
│   │   │   │                   │   ├── CustomerValidation.java
│   │   │   │                   │   ├── DocumentReceipt.java
│   │   │   │                   │   └── DownstreamRequest.java
│   │   │   │                   ├── exception/
│   │   │   │                   │   ├── ApiExceptionHandler.java
│   │   │   │                   │   └── DownstreamException.java
│   │   │   │                   ├── service/
│   │   │   │                   │   └── CustomerService.java
│   │   │   │                   └── CustomerApplication.java
│   │   │   └── resources/
│   │   │       └── application.yml
│   │   └── test/
│   │       ├── java/
│   │       │   └── com/
│   │       │       └── madlanga/
│   │       │           └── lab/
│   │       │               └── customer/
│   │       │                   └── CustomerApplicationTests.java
│   │       └── resources/
│   │           └── mockito-extensions/
│   │               └── org.mockito.plugins.MockMaker
│   ├── .dockerignore
│   ├── Dockerfile
│   └── pom.xml
├── document-service/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/
│   │   │   │       └── madlanga/
│   │   │   │           └── lab/
│   │   │   │               └── document/
│   │   │   │                   ├── config/
│   │   │   │                   │   └── CorrelationIdFilter.java
│   │   │   │                   ├── controller/
│   │   │   │                   │   └── DocumentController.java
│   │   │   │                   ├── dto/
│   │   │   │                   │   ├── ApiError.java
│   │   │   │                   │   ├── DocumentReceipt.java
│   │   │   │                   │   └── DocumentRequest.java
│   │   │   │                   ├── exception/
│   │   │   │                   │   └── ApiExceptionHandler.java
│   │   │   │                   ├── persistence/
│   │   │   │                   │   └── DocumentRepository.java
│   │   │   │                   ├── service/
│   │   │   │                   │   └── DocumentService.java
│   │   │   │                   └── DocumentApplication.java
│   │   │   └── resources/
│   │   │       ├── db/
│   │   │       │   └── migration/
│   │   │       │       └── V1__create_synthetic_documents.sql
│   │   │       └── application.yml
│   │   └── test/
│   │       ├── java/
│   │       │   └── com/
│   │       │       └── madlanga/
│   │       │           └── lab/
│   │       │               └── document/
│   │       │                   ├── DocumentApplicationTests.java
│   │       │                   └── DocumentFailureTests.java
│   │       └── resources/
│   │           ├── mockito-extensions/
│   │           │   └── org.mockito.plugins.MockMaker
│   │           └── application-test.yml
│   ├── .dockerignore
│   ├── Dockerfile
│   └── pom.xml
└── payment-service/
    ├── src/
    │   ├── main/
    │   │   ├── java/
    │   │   │   └── com/
    │   │   │       └── madlanga/
    │   │   │           └── lab/
    │   │   │               └── payment/
    │   │   │                   ├── client/
    │   │   │                   │   └── CustomerClient.java
    │   │   │                   ├── config/
    │   │   │                   │   ├── CorrelationIdFilter.java
    │   │   │                   │   └── HttpClientConfig.java
    │   │   │                   ├── controller/
    │   │   │                   │   └── PaymentController.java
    │   │   │                   ├── dto/
    │   │   │                   │   ├── ApiError.java
    │   │   │                   │   ├── CustomerValidation.java
    │   │   │                   │   ├── DocumentReceipt.java
    │   │   │                   │   ├── DownstreamRequest.java
    │   │   │                   │   ├── PaymentRequest.java
    │   │   │                   │   └── PaymentResult.java
    │   │   │                   ├── exception/
    │   │   │                   │   ├── ApiExceptionHandler.java
    │   │   │                   │   └── DownstreamException.java
    │   │   │                   ├── service/
    │   │   │                   │   └── PaymentService.java
    │   │   │                   └── PaymentApplication.java
    │   │   └── resources/
    │   │       └── application.yml
    │   └── test/
    │       ├── java/
    │       │   └── com/
    │       │       └── madlanga/
    │       │           └── lab/
    │       │               └── payment/
    │       │                   └── PaymentApplicationTests.java
    │       └── resources/
    │           └── mockito-extensions/
    │               └── org.mockito.plugins.MockMaker
    ├── .dockerignore
    ├── Dockerfile
    └── pom.xml
traffic-generator/
├── .dockerignore
├── Dockerfile
├── test_traffic.py
└── traffic.py
infrastructure/database/postgres/
└── README.md
scripts/
└── verify-phase2.py
fixtures/payment-request.json
.env.example
docker-compose.yml
```

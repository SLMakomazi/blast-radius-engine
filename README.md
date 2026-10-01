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

## Current status — Phase 1 skeleton

Implemented: one independently buildable Java 21 / Spring Boot 4.1.1 application,
Actuator health, a context smoke test, a Dockerfile and an API-only Compose stack.
No analysis endpoint or domain algorithms exist yet. All other service, infrastructure
and fixture folders are intentionally empty and retained with `.gitkeep` files.

The current delivery sequence is skeleton first, then the three mock services,
PostgreSQL and real service calls, followed by observability. The original roadmap
in `docs/IMPLEMENTATION_PLAN.md` is preserved as the planning baseline; its phase
numbers differ from this delivery sequence.

## Build, test and run locally

Prerequisites: JDK 21 and Maven 3.9.16 (or compatible Maven 3.9.x). Set `JAVA_HOME`
to your JDK 21 installation. Run all commands below from the repository root.

```bash
# Compile, run tests and package the executable JAR
mvn -f blast-radius-api/pom.xml clean verify

# Run only the tests
mvn -f blast-radius-api/pom.xml test

# Start from source (Ctrl-C to stop)
mvn -f blast-radius-api/pom.xml spring-boot:run
```

Alternatively, after building:

```bash
java -jar blast-radius-api/target/blast-radius-api.jar
```

In another terminal:

```bash
curl --fail --silent --show-error http://localhost:8080/actuator/health
```

Expected response includes `"status":"UP"` (Spring Boot also reports liveness/readiness
groups). Only the health Actuator endpoint is exposed;
health details are hidden. No database or external credentials are required.
For a different local port, run `SERVER_PORT=8081 java -jar blast-radius-api/target/blast-radius-api.jar`
and use port 8081 in the health request.

## Run with Docker Compose

Prerequisites: Docker Engine/Desktop running and Docker Compose v2 with `--wait`
support. Initial builds need network access for Maven dependencies and base images.
Stop any local instance using port 8080 first.

```bash
docker compose config --quiet
docker compose up --build --wait --wait-timeout 180
curl --fail --silent --show-error http://localhost:8080/actuator/health
docker compose logs blast-radius-api
docker compose down
```

The image builds and tests the application with Java 21, then runs it as a non-root
user on a Java 21 JRE. Its health check polls Actuator. Compose publishes port 8080
on loopback and places the API on a dedicated network. It currently starts only the
API; PostgreSQL, mocks and observability will be added when implemented.

Local lab containers use explicit, concise names: the current API container is
`blast-radius-api`, without a `blast-radius-engine-` prefix. Future names follow
the [local lab naming convention](docs/LOCAL_LAB.md#local-container-names).
Compose service names remain the canonical DNS names for application configuration;
never use container names, container IP addresses or localhost to reach another
container. These explicit container names are a local development convention only,
not a production deployment assumption.

Application images also have explicit local tags: `blast-radius-api:latest` for the
current API, and `<service-name>:latest` for future lab applications. Compose must
not generate project-prefixed application image names. Infrastructure keeps its
official upstream image names and pinned versions; see the
[image naming convention](docs/LOCAL_LAB.md#local-image-names).

To validate the same Compose file with Podman and an installed Compose provider:

```bash
podman compose config
```

To build the image independently:

```bash
docker build -t blast-radius-api:latest blast-radius-api
```

## Repository and package boundaries

- `blast-radius-api/`: independently buildable Spring Boot API.
- `mock-services/`: reserved payment, customer and document applications.
- `infrastructure/`: reserved observability, local database and failure-driver configuration.
- `traffic-generator/`: reserved traffic application.
- `fixtures/`: reserved synthetic topology, telemetry, incident and experiment data.
- `scripts/` and `docker/`: reserved shared developer scripts and Docker assets.
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

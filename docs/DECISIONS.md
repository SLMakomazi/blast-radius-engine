# Architecture Decision Log

## ADR-001 — Provider-neutral telemetry
**Status:** Accepted
Core logic consumes normalized telemetry; vendor schemas stay in adapters.

## ADR-002 — Deterministic graph calculation
**Status:** Accepted
Dependency traversal, not AI, determines theoretical blast radius.

## ADR-003 — Theoretical vs observed impact
**Status:** Accepted
Reachability is potential impact; runtime evidence is required for observed impact.

## ADR-004 — AI is advisory
**Status:** Accepted
AI explains sanitized evidence/recommends actions; deterministic results survive AI failure.

## ADR-005 — Java/Spring Boot
**Status:** Accepted for local implementation
Use Java/Spring Boot for the Blast Radius service while preserving integration-neutral contracts.

## ADR-006 — No autonomous production fixes
**Status:** Accepted
Remediation is advisory.

## ADR-007 — Full telemetry contract exceeds current MadlangaAI MVP
**Status:** Accepted
Blast Radius supports logs, metrics, traces and health even where current upstream integrations expose only a subset.

## ADR-008 — Complete Docker local lab
**Status:** Accepted
Mock services, PostgreSQL first, traffic, observability and a failure driver prove runtime behavior.

## ADR-009 — Sanitization before domain/AI use
**Status:** Accepted
Raw telemetry is sanitized before normalized evidence is persisted, logged, exported or sent to AI.

## ADR-010 — Incident severity is not Overall Health Score
**Status:** Accepted
Blast Radius incident severity does not alter MadlangaAI's weighted application health score without a future approved requirement.

## ADR-011 — Blast Radius and chaos injection are separate
**Status:** Accepted
The engine evaluates failure/experiment impact; local failure injection validates it while production chaos orchestration remains separate.

## ADR-012 — Partial telemetry is first-class
**Status:** Accepted
Telemetry families have explicit coverage status; missing data is reported, never fabricated.

## ADR-013 — Technology-neutral dependency graph
**Status:** Accepted

The core graph must support heterogeneous application components. The MadlangaAI-supported landscape includes frontend structures, Java/Spring Boot and Node.js applications, PostgreSQL/MongoDB/Oracle/DB2/SQL Server, IBM MQ/SQS/SNS/ActiveMQ, Step Functions and Lambda.

Node type and technology are separate concepts. PostgreSQL is the first local scenario, not a Blast Radius limitation. New supported technologies must be addable without rewriting traversal/correlation fundamentals.

---
Add new decisions for significant architectural choices.


## ADR-014 — Independently buildable API skeleton and revised delivery order
**Status:** Accepted — Phase 1 skeleton

The current delivery begins with a Java 21 / Spring Boot 4.1.1 Maven application
in `blast-radius-api`, followed by the mock service chain and PostgreSQL, then
observability. The original implementation roadmap is retained as the design
baseline, with its original phase numbering.

Use the requested `controller`/`dto` and `service` packages for transport and use-case
orchestration (the logical `api` and `application` layers in ARCHITECTURE.md).
Keep technology-neutral domain internals separate from ports and adapters;
adapters are grouped by telemetry, topology, AI and persistence responsibilities.
Do not introduce speculative domain types, provider interfaces or database dependencies.

Each future application owns its build and Docker context. Compose currently starts
only the implemented API on a dedicated network. The API exposes Actuator health
and runs as a non-root container user. Persistence, analysis, telemetry ingestion,
Datadog and AI remain deferred.


## ADR-015 — Synthetic chain persistence and explicit failure semantics
**Status:** Accepted — Phase 2

Use three independent Spring Boot applications and [Spring RestClient](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html) for the two
synchronous HTTP hops. Configure bounded timeouts; do not add fallback success,
circuit breakers or request retries. Return sanitized immediate-dependency/status
errors (document database failure 503; upstream HTTP dependency failure 502), and
use a propagated correlation ID to connect the hop logs.

Document-service alone owns Spring JDBC persistence and Flyway versioned migrations
against the target PostgreSQL database. This is smaller than an ORM for one table
and supports restart-safe schema evolution. The API/domain engine gains no database
or HTTP assumptions. Maven tests use H2 plus the same migration for fast local
checks; real PostgreSQL healthy/outage/recovery verification is separately required.

Keep the mock applications independently buildable, including their small transport
contracts and correlation filters, instead of adding a shared runtime library or
coupling them to the Blast Radius domain. Container startup follows health-based
Compose dependencies; document readiness includes DB health, liveness does not.
The Podman-primary lab uses standard Compose fields and OCI images, with no engine
socket mounts or Docker Desktop dependency.

## ADR-016 — Independent lightweight local evidence pipelines
**Status:** Accepted — Phase 3

Use the standard OpenTelemetry Java agent for the three independently built mock
services. It instruments their existing RestClient/JDK HTTP/JDBC path without
changing business logic or introducing telemetry vendors into the Blast Radius
domain. Preserve business correlation IDs independently of W3C trace context.
The agent is pinned and SHA-256 verified during each image build.

Route OTLP logs/traces through a small Collector to Loki's native OTLP endpoint and
Tempo. Scrape Micrometer directly with Prometheus so metrics remain available during
Collector loss. Actuator health is queried directly, with document readiness tied
to the DB and liveness tied to the process. Python traffic requires no tracing SDK;
the distributed trace begins at payment. No Grafana or normalized adapters yet.

Use pinned upstream ARM64 images and short local retention with bounded JVM/Go
memory/concurrency. Tempo 2.x provides the required monolithic local storage
without introducing Kafka or a production-scale architecture. Collector 0.157.0
was selected after 0.162.0 manifests were unavailable in the queried official
registries; compatibility is verified against the actual downloaded image.

Export only controlled application logger scopes, allowlisted resource/span
attributes and correlation metadata. Remove exception messages/stack traces and
SQL text/parameters from centralized telemetry. Loki indexes service/environment
only; high-cardinality request IDs stay structured metadata. This deliberately
limits centralized framework diagnostics for privacy and resource use.

Observability is never a business startup dependency. Bounded asynchronous export
may delay/drop evidence during outages; it must not break business processing.
These limits and disposable telemetry storage are local lab choices, not production
deployment assumptions. Phase 4 will interpret provider coverage and provenance.

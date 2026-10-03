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

## ADR-023 — Topology acquisition is a provider concern; traversal stays deterministic
**Status:** Accepted — Phase 5

The Blast Radius graph engine consumes a technology-neutral `DependencyTopology` and
does not discover dependencies itself. `DependencyTopologyProvider` is the boundary
between topology acquisition and graph calculation.

This avoids a manually maintained graph per monitored application without coupling the
core algorithm to a single discovery mechanism. MadlangaAI's architecture/dependency
model is the intended enterprise source when integrated. Runtime traces, service catalogs,
cloud/platform metadata, or local fixtures may be implemented as alternate adapters.

An edge `A -> B` means A depends on B. Theoretical blast radius traverses those edges
in reverse from the origin. Traversal is cycle-safe, retains minimum hop distance and a
deterministic shortest path, classifies one-hop dependents DIRECT and deeper dependents
INDIRECT, and excludes disconnected nodes. Duplicate edges are normalized at topology
construction.

Phase 5 does not infer the incident origin and does not use telemetry to mark observed
impact; those remain later analysis phases.

---
Add new decisions for significant architectural choices.


## ADR-018 — Normalized telemetry domain is Java record-style immutable builders
**Status:** Accepted — Phase 4

The four evidence models (LogEvidence, MetricEvidence, SpanEvidence, HealthEvidence)
and supporting types (TelemetryBundle, TelemetryCoverage, EvidenceProvenance,
TelemetryQuery) are implemented as final classes with private constructors and
nested Builder types rather than Java records. This preserves explicit `NullPointerException`
precondition enforcement at construction time, allows future inheritance-free extension,
and matches the project's existing Spring Boot / Maven toolchain without requiring
additional bytecode processors.

All collections returned from getters are unmodifiable. No raw provider DTOs are
referenced from domain classes.


## ADR-019 — Sanitization before evidence objects are constructed
**Status:** Accepted — Phase 4

The `TelemetrySanitizer` is called by each provider adapter before constructing
evidence objects (LogEvidence, MetricEvidence, SpanEvidence, HealthEvidence). This
means the domain model never contains unsanitized values; sanitization is not an
optional post-processing step. The built-in rule set (authorization header, password,
API key, access/refresh token, secret, cookie/session, SA ID number field) is
applied defensively to all attribute maps retrieved from provider responses.

Additional redaction rules can be injected at construction time without modifying
the core sanitizer, satisfying the extensibility requirement documented in Phase 4.


## ADR-020 — Provider adapters return typed result objects, not raw exceptions
**Status:** Accepted — Phase 4

Each adapter (LokiLogAdapter, PrometheusMetricsAdapter, TempoTraceAdapter,
ActuatorHealthAdapter) catches its own provider failures (RestClientException and
general Exception) and returns a typed result object (LogAdapterResult,
MetricAdapterResult, TraceAdapterResult, HealthAdapterResult) carrying the evidence
list, a CoverageStatus, and warnings. This means the composite LocalTelemetryProvider
always receives a well-typed result with no unchecked exception propagation from normal
provider failures.

An additional outer try/catch in LocalTelemetryProvider isolates the rare case of an
unchecked exception escaping an adapter, recording it as a warning and UNAVAILABLE
coverage for that family without terminating other adapter calls.


## ADR-021 — Actuator health adapter probes both root and readiness endpoints
**Status:** Accepted — Phase 4

The health adapter probes both `/actuator/health` (overall process health) and
`/actuator/health/readiness` (database-tied readiness for document-service) for each
configured component. This mirrors the Phase 3 verification evidence that showed
document readiness becomes DOWN during a PostgreSQL outage while liveness stays UP.
Probing both endpoints produces more precise HealthEvidence for the correlation engine
in later phases without requiring a provider-specific workaround at the domain level.


## ADR-022 — LocalTelemetryProvider is the sole TelemetryProvider implementation for Phase 4
**Status:** Accepted — Phase 4

A single composite Spring @Component implements the TelemetryProvider port by delegating
to the four local adapters. The domain and service layers depend only on the
TelemetryProvider interface. When a Datadog or other enterprise provider is introduced
in Phase 12, a new implementation of TelemetryProvider replaces or supplements
LocalTelemetryProvider without any change to domain or service code.


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


## ADR-017 — Business availability and telemetry availability are independent
**Status:** Accepted — Phase 3

Observability infrastructure readiness is not equivalent to end-to-end telemetry
availability. A running Collector or backend does not prove that application
exporters have reconnected or that fresh evidence exists.

Business requests must never wait for telemetry recovery. Analysis-side or
verification-side consumers may perform a bounded, configurable wait for required
evidence families using fresh evidence. On timeout, unavailable or partial evidence
is reported explicitly; missing evidence must never be interpreted as proof that a
dependency is healthy.

The Phase 3 verification probe proves this behavior without introducing the future
Phase 4 provider/domain API. Required evidence families remain a provider-neutral
concept rather than a Collector-specific contract.

## ADR-024 — Retain runtime topology separately from incident evidence
**Status:** Accepted — Phase 6 investigation and approved correction

The live outage proved that disposable Tempo history cannot serve as durable application topology. It also exposed a false second database node created from technology-only pool spans. Treat concrete peer identity and technology separately; do not infer identity from `db.system` or `db.system.name`.

Use `DependencyTopologyProvider` for analysis and a replaceable `TopologyStore` for scoped, timestamped knowledge. The single-instance local adapter stores snapshots atomically in a named volume, automatically merges runtime discovery, and expires observations after a configurable seven-day default. No vendor or persistence concerns enter deterministic graph traversal. Retained edges establish potential relationships, never current failure evidence. A future MadlangaAI architecture provider can replace the acquisition path.

Filter fetched trace spans and incident evidence to the requested interval. Historical spans cannot inflate origin scores or timelines. Keep span roles so server errors alone cannot trigger peer-less dependency inference, and refuse inference when multiple real dependencies remain possible.

Captured real traces are permitted only as regression/offline acceptance inputs when the original healthy evidence has expired. They are never the normal runtime topology mechanism. See [topology retention](TOPOLOGY_RETENTION.md) for freshness, scope, single-writer and partial-coverage limitations.

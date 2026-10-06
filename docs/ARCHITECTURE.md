# Architecture

Topology calculates potential impact. Runtime telemetry proves observed impact. AI explains sanitized evidence.

## Package boundaries

```text
com.madlanga.blastradius
├── BlastRadiusApplication
├── domain
│   ├── evidence     normalized telemetry, scope, coverage and provenance
│   ├── topology     components, dependencies and DeterministicGraphEngine
│   ├── incident     origin/impact/status and IncidentSeverityCalculator
│   ├── experiment   expected impact and FailureExperimentAssessmentService
│   └── diagnosis    provider-neutral diagnosis input/output
├── service          analysis/lifecycle/diagnosis use cases and context mapping
├── ports            seven external contracts
├── adapters
│   ├── telemetry    composite provider and Loki/Prometheus/Tempo/health adapters
│   ├── topology     trace discovery and retained runtime knowledge
│   ├── persistence  JDBC incident history and atomic topology files
│   ├── ai           Gemini, optional provider wiring and deterministic fallback
│   ├── experiment   local experiment metadata
│   └── scheduling   proactive lifecycle entry point
├── api
│   └── dto          HTTP request/response records
├── config           shared HTTP and topology wiring
└── sanitization     redaction rules and telemetry sanitizer
```

`service` is the application layer. Pure graph, severity and containment rules belong in the domain. A port describes an external need; its adapter supplies a technology-specific implementation. Provider response models and properties stay beside their adapter. See [the class audit](ARCHITECTURAL_AUDIT.md) for the original inventory and [the developer guide](DEVELOPER_GUIDE.md) for placement rules.

## Analysis flow

1. `IncidentAnalysisService` asks `TelemetryProvider` for sanitized, normalized logs, metrics, spans and health.
2. It asks `DependencyTopologyProvider` for scoped topology. Retained topology preserves observed relationships when raw traces expire.
3. It restricts evidence to the requested interval, finds the likely origin and correlates observed failures.
4. `DeterministicGraphEngine` traverses dependencies in reverse to find potential impact, minimum hop distance and stable paths. An edge A → B means A depends on B.
5. Observed impact requires current evidence. Partial coverage leaves unobserved potential impact UNKNOWN. Severity and optional containment are deterministic domain rules.
6. `IncidentLifecycleService` creates or updates an ACTIVE incident and stores its analysis snapshot through `IncidentRepository`.
7. The lifecycle scheduler requires consecutive fully covered healthy windows before resolving the same incident. AI does not participate in this path.
8. REST and the dashboard display stored results. On-demand diagnosis maps the snapshot, sanitizes free text again and calls `AiDiagnosisPort`. Provider failure returns the deterministic fallback.

## Technology-neutral domain

Component types include services, APIs, databases, brokers, queues, topics, serverless functions, workflows, frontends and external systems. Technology is metadata, separate from identity and type. New technologies do not require a graph rewrite.

Domain code has no Spring, MVC, JDBC, Gemini, Loki, Tempo or Prometheus imports. Diagnosis contracts may carry provider names as provenance; they do not import provider classes. Retained relationships establish potential dependencies, never current failure evidence.

## Local platform

The synthetic chain is `traffic-generator → payment-service → customer-service → document-service → postgres`. It generates realistic HTTP/JDBC telemetry and bounded controlled failures. The diagnostic `blast-radius-db` is separate so target outages do not prevent incident history writes.

Micrometer metrics are scraped directly. The OpenTelemetry Java agent exports logs/traces asynchronously through the Collector. Collector allowlists and adapter sanitization protect evidence. Business correlation IDs are separate from W3C trace IDs. Observability loss must not prevent business requests or prove recovery.

Four independent Maven projects build with Java 21 / Spring Boot 4.1.1. React/Vite displays deterministic results. Compose runs 12 services; migrations, observability configuration, runners and active fixtures remain runtime/test assets.

## Persistence

`JdbcIncidentRepository` implements `IncidentRepository` using explicit SQL, JSONB snapshots and Flyway migrations. `DocumentRepository` persists one synthetic table in the monitored lab. `FileTopologyStore` writes scoped snapshots atomically and supports one local writer. JDBC is retained; see ADR-029 in [DECISIONS.md](DECISIONS.md).

## Validation and integration

Stages 1–4 are implemented and the completed baseline local suite passed 18 / 18 scenarios. [E2E_VALIDATION.md](E2E_VALIDATION.md) lists the actual scenarios and runner order. Synthetic change markers are not real deployment integration; distributed lab faults do not imply general multi-origin or network-partition analysis.

MadlangaAI topology, Datadog/MCP telemetry, canonical IDs, multi-application scheduling, authentication/RBAC/audit and production retention still need enterprise integration. See [INTEGRATION.md](INTEGRATION.md).

## Remaining debt

Analysis combines orchestration and evidence policy, including lab markers and metric interpretation. Recovery policy still lives in the scheduler. Controllers read query ports directly and expose some domain results as JSON. Generic errors use message prefixes. Trace discovery has a mapper/helper lifecycle that could be clearer. These behavior-sensitive changes remain separate from package cleanup.

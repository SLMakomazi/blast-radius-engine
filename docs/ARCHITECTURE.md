# Architecture

Topology calculates potential impact. Runtime telemetry proves observed impact. AI explains sanitized evidence.

## Package boundaries

```text
com.madlanga.blastradius/
├── diagnosis/
│   ├── application/
│   │   └── port
│   ├── domain
│   └── infrastructure
├── incident/
│   ├── api/
│   │   └── dto
│   ├── application/
│   │   └── port
│   ├── domain/
│   │   └── containment
│   └── infrastructure/
│       ├── containment
│       └── persistence
├── lifecycle/
│   ├── application
│   └── infrastructure
├── shared/
│   ├── config
│   └── sanitization
├── telemetry/
│   ├── application/
│   │   └── port
│   ├── domain
│   └── infrastructure/
│       ├── config
│       ├── health
│       ├── loki
│       ├── prometheus
│       └── tempo
└── topology/
    ├── application/
    │   └── port
    ├── domain
    └── infrastructure/
        ├── config
        └── persistence
```

Code is organized by capability first and layer second. `incident`, `topology`, `telemetry`, `diagnosis` and `lifecycle` own their concepts and workflows. `shared` contains common redaction utilities and OpenAPI configuration. No global domain/service/ports/adapters packages remain.

API controllers call application services or application ports. Applications coordinate domain rules and external ports. Infrastructure supplies vendor implementations, persistence and Spring wiring/scheduling. Domain classes use plain Java and domain concepts only. Application and API code do not import infrastructure implementations. Existing domain records remain API data contracts where already exposed; this refactor preserves their JSON representation.

The seven existing ports belong to their capabilities: incident owns incident persistence and failure-validation metadata; topology owns dependency acquisition, retained storage and runtime-span acquisition; telemetry owns telemetry acquisition; diagnosis owns the advisory provider boundary. No new runtime interfaces were introduced.

Controlled failure metadata is incident containment, not a separate product capability. `incident.domain.containment` keeps `FailureExperiment`, `ExperimentAssessment`, `ContainmentStatus` and `FailureExperimentAssessmentService`. Its metadata provider is in `incident.application.port`, and the local catalogue is in `incident.infrastructure.containment`. The optional `experimentId` API and response fields are preserved. The catalogue describes failures; mock-service controls inject them.

See [CAPABILITY_REFACTOR.md](CAPABILITY_REFACTOR.md) for every class move, validation and remaining debt, and [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) for placement rules.

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

Stages 1–4 are implemented and the completed baseline local suite passed 18 / 18 scenarios. The capability refactor has Maven validation; the user will rerun the Stage suites locally before it is considered runtime-validated. [E2E_VALIDATION.md](E2E_VALIDATION.md) lists the actual scenarios and runner order. Synthetic change markers are not real deployment integration; distributed lab faults do not imply general multi-origin or network-partition analysis.

MadlangaAI topology, Datadog/MCP telemetry, canonical IDs, multi-application scheduling, authentication/RBAC/audit and production retention still need enterprise integration. See [INTEGRATION.md](INTEGRATION.md).

## Remaining debt

Analysis combines orchestration and evidence policy, including lab markers and metric interpretation. Recovery policy still lives in the scheduler. Controllers read query ports directly and expose some domain results as JSON. Generic errors use message prefixes. Trace discovery has a mapper/helper lifecycle that could be clearer. These behavior-sensitive changes remain separate from package cleanup. The architecture checks protect package ownership and import direction; they do not replace runtime validation.

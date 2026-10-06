# Developer instructions

## Purpose

Maintain the deterministic Blast Radius Engine and its complete local lab. Architecture cleanup improves package boundaries without changing working behavior.

Read [README.md](README.md), [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md), [the architecture](docs/ARCHITECTURE.md), [the developer guide](docs/DEVELOPER_GUIDE.md), [decisions](docs/DECISIONS.md) and [integration](docs/INTEGRATION.md). These are the active documents; old phase documents are available in Git history.

## Core rules

- Topology calculates potential impact. Runtime telemetry proves observed impact. AI explains sanitized evidence.
- Domain rules stay independent of vendors, controllers, JDBC, Docker and the frontend.
- Organize code by capability first: incident, topology, telemetry, diagnosis, lifecycle and shared. Within each capability use domain, application/port, infrastructure and API only where actual code needs them.
- Applications and APIs do not import infrastructure implementations. Plain Java domain code depends only on Java/domain concepts.
- Keep optional experiment metadata/assessment under incident containment; preserve its API fields and separate lab fault injection.
- Put new code in the package matching its responsibility, not simply beside its caller.
- Preserve canonical identities, scope, evidence timestamps and provenance.
- Missing telemetry is uncertainty, never proof of health or recovery.
- Keep AI optional and advisory. It does not select origin, impact, propagation, severity or lifecycle transitions.
- Sanitize telemetry before persistence, display, export, logs or AI. Never log secret values or raw provider payloads.
- Incident severity is separate from MadlangaAI Overall Health Score.
- No autonomous production fixes. Local fault controls are synthetic lab support.
- Keep mock services small and independently buildable. Keep JDBC unless a concrete problem justifies changing it.

## Runtime conventions

Use Java 21 and each module's Maven POM. Keep Docker/Podman Compose portability, bounded JVM/backend resources and asynchronous OpenTelemetry export. Metrics scrape directly with Micrometer; logs/traces use the Collector. Business correlation IDs are separate from W3C trace IDs.

Topology enters through `DependencyTopologyProvider`. `TopologyStore` retains observed scoped relationships with timestamps and expiry. Technology-only spans do not invent dependency identity. Read time and peer-less failures do not renew relationships. Historical topology does not become current incident evidence. Captured traces are regression/offline inputs only.

The scheduled lifecycle detector monitors the configured local application/environment. Recovery requires consecutive fully covered healthy windows. Multi-application scheduling and final MadlangaAI/Datadog contracts remain enterprise integration work.

## Validation and Git

Normal stable branch: `main`. Work on a separate branch, keep commits focused, run `git diff --check` before committing and never weaken tests to pass a refactor.

Compile and run relevant tests after each package change. `mvn clean verify` runs the standard suite; live `*LiveIT` tests require an explicit selection and a healthy lab. Run Compose configuration validation and build the complete stack.

The required validation model has only two stages: Stage 1 — Hard Failure / Blast Radius Detection (6/6) and Stage 2 — Degraded-But-Running Detection (4/4), TOTAL: 10/10 PASS. Runtime validation of a structural cleanup requires these suites. If the user reserves their execution, document them as pending and distinguish fresh Maven results from the historical 10 / 10 baseline. Follow [E2E_VALIDATION.md](docs/E2E_VALIDATION.md), including stage pauses, trace-export recovery and final ACTIVE-incident/Collector checks.

Do not delete migrations, runtime configuration, active fixtures, runners, test resources or frontend assets. Delete unused placeholders only after checking references. Use clear English; preserve established technical terms and public contracts.

Current class ownership, moves and results: [CAPABILITY_REFACTOR.md](docs/CAPABILITY_REFACTOR.md). No global service/ports/adapters/domain package structure or empty future package placeholders should be reintroduced.

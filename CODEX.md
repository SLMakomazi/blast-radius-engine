# Codex Project Instructions

## Mission
Build a **full-capability Blast Radius Engine** designed to become a Phase-4 capability inside **MadlangaAI** while remaining independently runnable in a complete local Docker lab.

The engine must answer:
1. What failed?
2. Where did the failure likely originate?
3. What components can be affected based on dependencies?
4. What components are actually affected according to runtime telemetry?
5. How did the incident propagate over time?
6. What logs, metrics, traces and endpoint-health evidence support the diagnosis?
7. What is the expected vs observed blast radius?
8. What should an engineer investigate or remediate?
9. When a chaos experiment is supplied, was failure contained as expected?

## Authoritative documents
Read before implementation:
1. `docs/SOURCE_ALIGNMENT.md`
2. `docs/PRODUCT_REQUIREMENTS.md`
3. `docs/ARCHITECTURE.md`
4. `docs/BLAST_RADIUS_SPEC.md`
5. `docs/TELEMETRY_CONTRACT.md`
6. `docs/LOCAL_LAB.md`
7. `docs/INTEGRATION.md`
8. `docs/IMPLEMENTATION_PLAN.md`
9. `docs/TEST_STRATEGY.md`
10. `docs/UI_UX_CONCEPT.md`
11. `docs/DECISIONS.md`

The MadlangaAI BRD is incomplete. Treat BRD/Test Plan statements as source constraints, but this repository deliberately adds Blast-Radius-specific capabilities where the current MVP does not define them.

## Non-negotiable engineering constraints
- Preferred implementation: Java + Spring Boot.
- Everything required to prove Blast Radius must run locally with Docker Compose.
- Provide synthetic mock services, a mock PostgreSQL database, generated traffic, and observability components sufficient to produce **logs, metrics, traces and endpoint-health signals**.
- Do not reduce the engine to only the telemetry currently exposed by MadlangaAI/Datadog. The Blast Radius normalized contract requires full logs + metrics + traces + endpoint-health capability.
- Keep domain logic independent of Datadog, OpenTelemetry backends, databases, LLM vendors and UI frameworks.
- All telemetry enters through provider interfaces/adapters.
- Local providers/adapters must work without external credentials.
- A future Datadog/MCP adapter may provide whatever subset MadlangaAI exposes; missing source capability must be represented as a data-quality gap, not silently invented.
- Blast-radius calculation is deterministic graph logic. Never ask an LLM to invent dependency relationships or observed impact.
- AI explains evidence, summarizes probable cause and recommends remediation only after sanitization.
- Always distinguish **theoretical**, **observed**, and, where applicable, **unexpected** impact.
- Preserve evidence/provenance and propagation timestamps.
- PII, credentials, tokens and secrets must be removed before persistence in Blast Radius evidence, application logs, exports or AI context.
- Blast Radius severity is separate from MadlangaAI Overall Health Score.
- Chaos injection is a separate responsibility. The local lab may include a controlled failure/chaos driver for validation, but the Blast Radius domain engine must only consume experiment/failure events and assess impact.
- No autonomous production fixes.

## Local proof requirement
The repository is not complete until a developer can run a documented Docker Compose command and obtain a working synthetic environment containing:
- Blast Radius API;
- payment-service;
- customer-service;
- document-service;
- PostgreSQL;
- traffic generator;
- logs pipeline;
- metrics pipeline;
- distributed tracing pipeline;
- health/endpoints;
- failure/chaos driver;
- local telemetry adapters/backends;
- optional AI stub.

The canonical dependency chain is:
`payment-service -> customer-service -> document-service -> postgres`.

## Definition of done
A locally injected PostgreSQL/service failure must create real synthetic runtime signals. The engine must correlate those signals, infer or accept a suspected origin, calculate direct/indirect theoretical impact, identify observed impact, construct propagation chronology, preserve evidence, calculate deterministic incident severity, expose a stable API response, and produce sanitized AI-ready diagnosis context.

## Working style
- Work in small feature branches/commits.
- Add tests with behavior changes.
- Prefer interfaces at integration boundaries.
- Keep all fixtures synthetic.
- Record significant decisions in `docs/DECISIONS.md`.
- Do not silently narrow requirements because MadlangaAI MVP lacks a source today.
- Keep APIs backward compatible unless deliberately versioned.

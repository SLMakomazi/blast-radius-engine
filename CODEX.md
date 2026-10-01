# Codex Project Instructions

## Mission
Build a **full-capability Blast Radius Engine** for MadlangaAI Phase 4, independently runnable in a complete local Docker lab.

The engine answers what failed, likely origin, theoretical/observed/unexpected impact, propagation, supporting logs/metrics/traces/health evidence, severity/confidence, remediation and optional chaos containment.

## Authoritative documents
Read in order: `SOURCE_ALIGNMENT.md`, `PRODUCT_REQUIREMENTS.md`, `ARCHITECTURE.md`, `BLAST_RADIUS_SPEC.md`, `TELEMETRY_CONTRACT.md`, `LOCAL_LAB.md`, `INTEGRATION.md`, `IMPLEMENTATION_PLAN.md`, `TEST_STRATEGY.md`, `UI_UX_CONCEPT.md`, `DECISIONS.md`.

The MadlangaAI BRD is incomplete. Do not silently narrow Blast Radius to current MVP integrations.

## Supported technology constraint
The core domain MUST remain technology-neutral. MadlangaAI currently lists Angular/JavaScript/TypeScript/NX/Micro Frontends; Spring Boot/Java/REST and Node.js; PostgreSQL, MongoDB, Oracle, IBM DB2 and SQL Server; IBM MQ, AWS SQS/SNS and ActiveMQ; AWS Step Functions and standalone AWS Lambdas.

PostgreSQL is only the canonical first Docker scenario. **Never encode PostgreSQL, relational DB, HTTP, Spring Boot, or synchronous-service assumptions into the graph engine.**

Use generic component types and technology metadata so additional supported technologies do not require core graph rewrites.

## Non-negotiable engineering constraints
- Java + Spring Boot for the local Blast Radius service.
- Everything required to prove Blast Radius runs locally with Docker Compose.
- Provide mock services, PostgreSQL first, traffic generation and logs/metrics/traces/health observability.
- Full telemetry contract remains required even if current MadlangaAI exposes a subset.
- Domain logic stays independent of telemetry vendors, databases, LLM vendors and UI frameworks.
- External systems enter through adapters/ports.
- Blast-radius calculation is deterministic graph logic.
- AI explains/recommends after sanitization; it never invents dependency/impact facts.
- Separate theoretical, observed and unexpected impact.
- Preserve evidence/provenance/timestamps.
- Sanitize PII/secrets/credentials before persistence/logging/export/AI.
- Incident severity is separate from MadlangaAI Overall Health Score.
- Local failure driver is for validation; production chaos orchestration is separate.
- No autonomous production fixes.

## Local proof requirement
Docker Compose must ultimately provide Blast Radius API, payment/customer/document mock services, PostgreSQL, traffic generator, logs, metrics, distributed tracing, health, failure driver and local telemetry adapters/backends.

Canonical chain: `payment-service -> customer-service -> document-service -> postgres`.

## Definition of done
A local injected failure creates real synthetic telemetry. The engine correlates it, assesses origin, calculates direct/indirect theoretical impact, identifies observed impact, builds propagation chronology/evidence, calculates deterministic severity, returns a stable API result and produces sanitized AI-ready context.

## Working style
Small branches/commits; tests with behavior changes; interfaces at integration boundaries; synthetic fixtures only; ADRs for significant choices; never invent answers to documented open questions.

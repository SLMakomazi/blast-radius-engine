# Codex Project Instructions

## Mission
Build a **Blast Radius Engine** that is designed to become a capability inside **MadlangaAI**, not a competing standalone platform.

The engine answers:
1. What failed?
2. Where did the failure originate?
3. What components can be affected based on dependencies?
4. What components are actually affected according to runtime telemetry?
5. What evidence supports the diagnosis?
6. What should an engineer investigate or remediate?

## Authoritative documents
Before implementing or changing behavior, read:
1. `docs/PRODUCT_REQUIREMENTS.md`
2. `docs/ARCHITECTURE.md`
3. `docs/BLAST_RADIUS_SPEC.md`
4. `docs/TELEMETRY_CONTRACT.md`
5. `docs/INTEGRATION.md`
6. `docs/IMPLEMENTATION_PLAN.md`
7. `docs/TEST_STRATEGY.md`
8. `docs/DECISIONS.md`

If code and documentation conflict, do not silently invent behavior. Update the design explicitly or raise the mismatch.

## Engineering constraints
- Preferred PoC implementation: Java + Spring Boot. This is a design choice for the PoC, not a confirmed statement about MadlangaAI's internal implementation.
- Keep domain logic independent of Datadog, LLM vendors, databases, and UI frameworks.
- All external telemetry must enter through provider interfaces/adapters.
- The initial provider is mock/file-backed telemetry. A Datadog/MCP provider is a later adapter.
- Blast-radius calculation must be deterministic graph logic. Do not ask an LLM to invent dependency relationships.
- AI may explain evidence, summarize probable causes, rank investigation steps, and recommend remediation.
- Always distinguish **theoretical impact** from **observed impact**.
- Preserve evidence/provenance for conclusions.
- Do not implement autonomous production fixes in this PoC.
- Do not implement chaos engineering in this repository; expose contracts that can consume chaos experiment events later.
- Never place secrets, PATs, API keys, credentials, PII, or real customer telemetry in fixtures.
- Logs and AI prompts must be designed for PII masking/redaction.

## Definition of done for the concept
Given mock telemetry plus a dependency graph, the service can identify a suspected origin, calculate direct/indirect theoretical impact, correlate observed degradation, return supporting evidence, and produce an AI-ready diagnosis context through a stable API contract.

## Working style
- Work in small feature branches/commits.
- Add tests with behavior changes.
- Prefer interfaces at integration boundaries.
- Keep fixtures realistic but synthetic.
- Record significant architectural choices in `docs/DECISIONS.md`.
- Keep the API backward compatible unless the specification is deliberately versioned.

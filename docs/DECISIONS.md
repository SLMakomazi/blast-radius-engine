# Architecture Decision Log

## ADR-001 — Provider-neutral telemetry
**Status:** Accepted for PoC

**Decision:** Core logic consumes normalized telemetry through `TelemetryProvider`; Datadog-specific payloads stay in adapters.

**Why:** Allows realistic mock data now and Datadog/MCP integration later without rewriting the engine.

## ADR-002 — Deterministic graph calculation
**Status:** Accepted for PoC

**Decision:** Dependency graph traversal, not an LLM, determines theoretical blast radius.

**Why:** Blast radius must be reproducible, testable and explainable.

## ADR-003 — Theoretical vs observed impact
**Status:** Accepted for PoC

**Decision:** Results explicitly separate components that could be impacted from components with telemetry evidence of degradation.

**Why:** Reachability does not prove runtime impact.

## ADR-004 — AI is advisory
**Status:** Accepted for PoC

**Decision:** AI explains evidence and recommends investigation/remediation; it does not replace deterministic impact/evidence calculations.

**Why:** The core result must survive AI unavailability and avoid unsupported dependency claims.

## ADR-005 — Java/Spring Boot for standalone concept
**Status:** Proposed pending team confirmation

**Decision:** Use Java/Spring Boot for the PoC unless the MadlangaAI team confirms a different integration constraint.

**Why:** It aligns with the team's common engineering stack, while the supplied MadlangaAI specification does not itself confirm the platform's implementation language.

## ADR-006 — No autonomous fixes
**Status:** Accepted for PoC

**Decision:** The engine recommends remediation but does not execute production changes.

**Why:** Automated bug fixing is a separate MadlangaAI roadmap capability and requires additional authorization, safety and audit controls.

---

Add new decisions here when implementation introduces a significant architectural choice. Do not silently change accepted decisions in code.

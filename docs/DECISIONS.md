# Architecture Decision Log

## ADR-001 — Provider-neutral telemetry
**Status:** Accepted

Core logic consumes normalized telemetry through `TelemetryProvider`; vendor schemas remain in adapters.

## ADR-002 — Deterministic graph calculation
**Status:** Accepted

Dependency traversal, not AI, determines theoretical blast radius.

## ADR-003 — Theoretical vs observed impact
**Status:** Accepted

Reachability is potential impact; runtime evidence is required for observed impact.

## ADR-004 — AI is advisory
**Status:** Accepted

AI explains sanitized evidence and recommends actions; deterministic results survive AI failure.

## ADR-005 — Java/Spring Boot
**Status:** Accepted for local implementation

Use Java/Spring Boot for the Blast Radius service while preserving integration-neutral contracts.

## ADR-006 — No autonomous production fixes
**Status:** Accepted

Remediation is advisory. Production changes require separate authorization/governance.

## ADR-007 — Full telemetry contract exceeds current MadlangaAI MVP
**Status:** Accepted

Blast Radius requires support for logs, metrics, distributed traces and endpoint/application health. Current MadlangaAI/Datadog requirements only guarantee a subset. Missing upstream capability must be implemented/proved locally and later supplied through an adapter, not removed from Blast Radius.

## ADR-008 — Complete Docker local lab
**Status:** Accepted

The repository will include mock services, PostgreSQL, traffic generation, observability backends and a failure driver so Blast Radius can be tested with real synthetic runtime signals.

## ADR-009 — Sanitization before domain/AI use
**Status:** Accepted

Raw telemetry is sanitized before normalized evidence is persisted, logged, exported or sent to AI.

## ADR-010 — Incident severity is not Overall Health Score
**Status:** Accepted

Blast Radius severity describes an incident. It does not alter MadlangaAI's weighted application health score without a future approved requirement.

## ADR-011 — Blast Radius and chaos injection are separate
**Status:** Accepted

The engine consumes failure/experiment metadata and evaluates expected, observed, unexpected impact and containment. A local failure driver is allowed for testing; production chaos orchestration is a separate component.

## ADR-012 — Partial telemetry is first-class
**Status:** Accepted

Each telemetry family has explicit coverage status. Analysis continues when evidence is sufficient and reports gaps instead of fabricating data.

---
Add new decisions when implementation introduces a significant architectural choice.

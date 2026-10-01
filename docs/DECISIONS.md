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

# Source Alignment

## Purpose
Separate confirmed MadlangaAI material from Blast-Radius-specific Phase-4 additions. The current BRD is a working baseline, not a complete Blast Radius specification.

## Confirmed from current MadlangaAI material
- Blast Radius / Chaos Engineering is a future Phase-4 capability.
- Architecture/dependency assessment and dependency maps exist.
- Datadog/MCP health covers traffic, latency, error rate, zero-traffic and high-risk endpoints.
- AI Diagnosis covers diagnosis/remediation.
- PII masking applies to reports, dashboards, logs and AI narrative.
- Current Overall Health Score is separate from Blast Radius.
- Supported application landscape includes:
  - Angular, JavaScript, TypeScript, NX Monorepos and Micro Frontends;
  - Spring Boot, Java, REST APIs and Node.js;
  - PostgreSQL, MongoDB, Oracle, IBM DB2 and SQL Server;
  - IBM MQ, AWS SQS/SNS and ActiveMQ;
  - AWS Step Functions and standalone AWS Lambda functions.

## Architectural implication
Blast Radius cannot be modeled as "Spring Boot services connected to PostgreSQL." The topology model must support heterogeneous nodes and dependencies. PostgreSQL remains the first local executable scenario only.

## Not currently guaranteed by MVP documents
- raw logs for Blast Radius;
- distributed traces/spans for Blast Radius;
- Phase-4 API schema;
- final chaos-event schema;
- production Blast Radius severity/containment rules;
- final UI interaction behavior.

## Blast Radius additions
This repository adds normalized logs/metrics/traces/health, telemetry coverage, propagation timeline, evidence provenance, incident severity, controlled-failure metadata, expected/observed/unexpected impact, containment assessment, complete Docker lab, mock services/database/traffic, sanitization and focused UI.

These are Blast Radius requirements/design decisions, not claims that current MVP already provides them.

## Integration rule
When MadlangaAI provides an equivalent capability, replace the local adapter with its enterprise adapter without rewriting deterministic domain logic. When it does not, retain the Blast Radius requirement and integrate an approved source.

## Assumptions requiring future confirmation
Final module/service deployment, exact Datadog MCP access, exact MadlangaAI dependency schema, final TsakaniQA/chaos integration, enterprise severity policy, persistence, auth/RBAC/audit and final dashboard/report placement remain open. Codex must not invent answers.

# Implementation Plan

## Phase 0 — Design playback
Goal: validate the concept with the team before changing MadlangaAI.

Deliver:
- architecture diagram;
- terminology;
- provider contracts;
- API contract;
- mock incident;
- integration path;
- open questions.

Exit: team agrees on responsibilities and integration boundaries.

## Phase 1 — Spring Boot skeleton
- Generate Spring Boot service.
- Establish package boundaries from ARCHITECTURE.md.
- Add health endpoint.
- Add API DTOs and validation.
- Add test framework.
- Add fixture loading.

Exit: service starts and can load synthetic topology/telemetry.

## Phase 2 — Deterministic graph engine
- Model nodes/edges.
- Validate topology.
- Implement reverse traversal.
- Handle cycles.
- Calculate minimum hop distance.
- Classify direct/indirect impact.

Exit: graph unit tests pass for chain, fan-out, fan-in, cycles and disconnected nodes.

## Phase 3 — Telemetry normalization/correlation
- Implement mock TelemetryProvider.
- Normalize logs, metrics and traces.
- Correlate incident window.
- Evaluate observed impact.
- Attach evidence.

Exit: fixture scenario distinguishes theoretical from observed impact.

## Phase 4 — Origin/severity
- Implement transparent origin-assessment rules.
- Return confidence/reasons.
- Implement configurable severity calculation.
- Add partial-data warnings.

Exit: output is deterministic and explainable.

## Phase 5 — API
- Implement `POST /api/v1/blast-radius/analyze`.
- Return versioned response.
- Add error contract.
- Add OpenAPI documentation if compatible with project stack.

Exit: end-to-end integration test proves sample incident.

## Phase 6 — AI diagnosis
- Create AiDiagnosisProvider boundary.
- Build sanitized structured DiagnosisContext.
- Start with deterministic/stub implementation if no approved AI endpoint exists.
- Later connect to MadlangaAI AI Diagnosis Engine.
- Validate AI output is advisory and does not overwrite deterministic evidence.

Exit: AI explanation can fail independently without losing blast-radius result.

## Phase 7 — MadlangaAI integration
After repo access/design confirmation:
- implement MadlangaAI topology adapter;
- implement Datadog/MCP telemetry adapter;
- use existing AI abstraction;
- map result into diagnosis report/dashboard;
- apply platform auth/RBAC/audit conventions.

## Backlog / future
- recent-deployment/change correlation;
- business-capability impact;
- topology learned from traces;
- historical comparison;
- chaos-experiment comparison;
- unexpected dependency detection;
- remediation workflow integration.

## Explicit non-goals
Do not build automated production fixes, chaos injection, a replacement monitoring platform, or a replacement MadlangaAI dashboard in this PoC.

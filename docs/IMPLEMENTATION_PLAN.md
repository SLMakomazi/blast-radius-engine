# Implementation Plan

## Phase 0 — Documentation/design baseline
- contracts, architecture, source alignment;
- local-lab topology;
- API and telemetry models;
- test strategy and UI concept.

Exit: no known requirement is silently delegated to unavailable MadlangaAI functionality.

## Phase 1 — Docker local lab foundation
Create Docker Compose with:
- PostgreSQL;
- document-service;
- customer-service;
- payment-service;
- traffic generator;
- health endpoints;
- network/dependency topology.

Instrument services from the start.

Exit: healthy calls traverse the complete chain and persist/read synthetic data.

## Phase 2 — Observability stack
Implement local collection for:
- application/container logs;
- Prometheus-style metrics;
- OpenTelemetry distributed traces;
- endpoint/application health.

Include an OpenTelemetry Collector and lightweight local backends/adapters appropriate to each signal.

Exit: one request can be followed across logs, metrics and trace data with health visible.

## Phase 3 — Blast Radius Spring Boot skeleton
- package boundaries;
- versioned API DTOs;
- provider ports;
- health endpoint;
- configuration;
- fixture support.

## Phase 4 — Sanitization and normalized telemetry
- log/metric/span/health models;
- coverage metadata;
- sanitization/redaction;
- local provider adapters;
- provenance.

Exit: full TelemetryBundle can be built from the local lab without secrets/PII leakage.

## Phase 5 — Deterministic graph engine
- topology validation;
- reverse traversal;
- cycles;
- minimum distance/path;
- direct/indirect classification.

## Phase 6 — Correlation, origin and observed impact
- multi-signal correlation;
- transparent origin rules/confidence;
- observed-impact evaluator;
- propagation timeline;
- partial-data warnings.

## Phase 7 — Severity and chaos/failure assessment
- configurable incident severity;
- local failure driver;
- FailureExperimentProvider;
- expected vs observed vs unexpected impact;
- containment assessment.

## Phase 8 — API and persistence
- `POST /api/v1/blast-radius/analyze`;
- error/warning contracts;
- incident/evidence persistence if needed for local history;
- OpenAPI docs.

If persistence is used, include its database/container in Docker Compose; do not reuse target PostgreSQL in a way that prevents analysis during its simulated outage.

## Phase 9 — AI diagnosis
- sanitized DiagnosisContext;
- deterministic stub first;
- optional approved AI adapter;
- Immediate/Medium-term/Strategic recommendations;
- AI failure isolation.

## Phase 10 — Local UI
Build a focused Blast Radius UI:
- incident KPIs;
- interactive topology;
- origin/direct/indirect/observed states;
- node evidence drawer;
- propagation timeline;
- telemetry coverage;
- AI diagnosis/remediation;
- chaos containment result.

## Phase 11 — End-to-end scenarios
Automate healthy baseline, PostgreSQL outage, partial observability, resilient upstream and containment scenarios.

Exit: a new developer can run the documented commands and reproduce expected results locally.

## Phase 12 — MadlangaAI integration
- MadlangaAI topology adapter;
- Datadog/MCP adapter for supported telemetry;
- additional approved adapter for any missing logs/traces;
- MadlangaAI AI adapter;
- RBAC/audit integration;
- dashboard/report integration.

## Not part of domain implementation
- autonomous production fixes;
- production chaos orchestration;
- replacement of unrelated MadlangaAI modules.

# Implementation Plan

## Current delivery sequence

The current execution sequence supersedes the original phase numbering below,
which is preserved as the planning baseline:

1. **Phase 1 — API skeleton:** Java 21 / Spring Boot foundation, package boundaries,
   Actuator, smoke test, OCI image and API Compose service.
2. **Phase 2 — Local synthetic service chain:** independent payment/customer/document
   apps, real HTTP calls, PostgreSQL with Flyway/JDBC, propagated correlation IDs,
   lightweight traffic, Podman Compose and sanitized failure responses.
   Acceptance requires a real persisted document, PostgreSQL outage reaching the
   caller, and recovery without rebuilding. Run `scripts/verify-phase2.py`; consult
   `PHASE2_VERIFICATION.md` for recorded execution results.
3. **Phase 3 — Observability:** Java agent OTLP logs/traces through Collector to
   Loki/Tempo, direct Micrometer/Prometheus scraping and Actuator health. Acceptance
   retrieves all four evidence families during healthy/PostgreSQL outage/recovery
   and proves business continuity during Collector loss. Run
   `scripts/verify-observability.sh`; see `PHASE3_VERIFICATION.md` for actual results.
4. **Phase 4 — Provider-neutral evidence:** normalized TelemetryBundle, sanitization,
   coverage/provenance and provider adapters. **Implementation and verification complete
   on `feat/normalized-telemetry-sanitization`.** Final regression: 109 Blast Radius
   API tests plus 39 supporting-service tests passed; the opt-in live provider integration
   also passed against real Loki, Prometheus, Tempo and Actuator backends. See
   `docs/PHASE4_VERIFICATION.md` and `docs/DECISIONS.md` ADR-018 through ADR-022.

5. **Phase 5 — Deterministic graph engine:** technology-neutral topology model,
   provider port, topology validation, reverse traversal, shortest path/minimum distance,
   direct/indirect classification and deterministic handling of fan-out/fan-in/cycles/
   disconnected nodes/duplicate edges. **Implementation and core Maven verification complete
   on `feat/deterministic-graph-engine`: 123 tests passed with zero failures/errors/skips.**
   Topology acquisition remains outside the graph algorithm. A local trace-discovery adapter
   now proves automatic topology acquisition from normalized distributed spans: its 2
   deterministic discovery tests passed and its opt-in live integration test passed against
   the running Docker/Tempo evidence, discovering the canonical service/database dependency
   chain without hand-authored graph edges. Production MadlangaAI integration will continue
   to use `DependencyTopologyProvider` so architecture analysis and approved runtime
   discovery sources can populate the graph without manual per-system maintenance.

Subsequent domain/telemetry/analysis work follows the requirements below.

## Original design roadmap

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

Implementation is staged on `feat/phase7-severity-chaos-assessment`: deterministic severity, the provider-neutral failure-experiment contract, expected/observed/unexpected comparison, and containment HELD/BREACHED/INCONCLUSIVE semantics are implemented. Manual verification is pending; see `PHASE7_VERIFICATION.md`.

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

### Phase 6 correction — verified 2026-10-03

Concrete dependency identity, automatic retained runtime topology, scoped seven-day expiry and incident-window integrity are implemented. The stopped-database acceptance passed with the expected four-node origin/observed-impact chain and no historical evidence in the incident timeline. See [actual results](PHASE6_VERIFICATION.md). The subsequent manual lifecycle validation proved healthy recovery, live database-edge relearning, a fresh no-hint PostgreSQL outage with the correct three-hop observed radius, and final recovery. Phase 7 now builds on that merged baseline.

# Product Requirements — Blast Radius Engine

## Context
This repository implements and proves the **Blast Radius Analysis** capability planned for MadlangaAI Phase 4.

The current MadlangaAI MVP already defines architecture/dependency analysis, data-flow mapping, SonarQube, JaCoCo, TsakaniQA, API evaluation, Datadog application health, AI diagnosis, remediation, scorecards and reports. Its current Datadog scope guarantees traffic, latency, error-rate and zero-traffic/endpoint-health information. It does **not** currently guarantee raw logs or distributed traces.

That limitation must not reduce the Blast Radius design. Where MadlangaAI does not yet supply a capability required by Blast Radius, this repository must implement/prove it locally behind a provider contract.

## Goal
Provide a machine-readable, evidence-backed and human-explainable blast-radius assessment for application incidents, with a complete local environment capable of producing logs, metrics, traces and endpoint-health telemetry.

## Primary user story
As an engineer investigating an incident, I want Blast Radius to correlate full runtime telemetry with dependency topology so I can understand origin, propagation, direct/indirect impact, observed degradation, evidence, confidence and recommended investigation/remediation.

## Functional requirements
### FR-01 Full telemetry ingestion
The engine MUST support normalized:
- logs;
- metrics;
- distributed traces/spans;
- endpoint/application health;
- traffic volume;
- latency;
- error rate;
- availability/zero-traffic signals.

A provider may return a subset, but the normalized Blast Radius model must support all categories and expose missing categories as data-quality warnings.

### FR-02 Dependency topology
Accept directed service/component dependencies from local fixtures/discovery and later from MadlangaAI architecture/dependency data.

### FR-03 Incident correlation
Correlate telemetry by time window, component, endpoint, trace/span IDs, dependency relationships, error signatures and degradation signals.

### FR-04 Suspected origin
Identify a suspected origin using transparent evidence/rules and return confidence plus reasons. Correlation must not be presented as proven causation.

### FR-05 Theoretical blast radius
Reverse-traverse dependencies from the origin to determine components that could be impacted.

### FR-06 Impact depth/path
Classify origin, direct and indirect impact and retain the dependency path and minimum hop distance.

### FR-07 Observed blast radius
Mark a component observed affected only when runtime evidence indicates degradation/error in the incident window.

### FR-08 Propagation timeline
Produce an ordered chronology showing origin signal and downstream degradation timing.

### FR-09 Evidence/provenance
Every origin/observed-impact assertion MUST reference supporting evidence with source, timestamp and component.

### FR-10 Deterministic severity
Calculate incident severity from configurable factors such as component criticality, number/depth of observed impacts and degradation magnitude. This is separate from MadlangaAI Overall Health Score.

### FR-11 AI diagnosis context
Produce sanitized structured context for explanation, likely-cause narrative and Immediate/Medium-term/Strategic remediation recommendations.

### FR-12 Versioned API
Expose a versioned API for local use and eventual MadlangaAI integration.

### FR-13 Partial-data behavior
Analysis MUST continue when one telemetry category/provider is unavailable when sufficient evidence remains. Results must expose coverage and warnings.

### FR-14 Chaos/failure validation
Accept a known injected-failure/chaos experiment event and compare expected/theoretical impact, observed impact, unexpected impact and containment success.

### FR-15 Local lab
The repository MUST include a Docker Compose environment that can generate and observe real synthetic failures end to end.

### FR-16 Sanitization
PII, secrets and credentials MUST be sanitized before Blast Radius persistence/logging/export and before AI context generation.

## Canonical local scenario
Topology:
`payment-service -> customer-service -> document-service -> postgres`

Scenario:
1. Traffic generator calls payment-service.
2. Calls propagate through customer-service and document-service to PostgreSQL.
3. A controlled failure makes PostgreSQL unavailable or unhealthy.
4. document-service emits DB errors, failed spans, HTTP 5xx and latency/error metrics.
5. customer-service emits downstream timeout/error evidence.
6. payment-service may remain healthy or degrade depending on the scenario.

Expected:
- postgres is suspected/known origin;
- document-service is direct theoretical impact;
- customer-service and payment-service are indirect theoretical impact;
- observed impact is based only on telemetry;
- propagation timeline and evidence are returned;
- a healthy but theoretically reachable service remains theoretical-only.

## Non-functional requirements
- Deterministic core analysis.
- Provider-neutral domain model.
- Offline/local testability.
- Docker Compose startup for the complete lab.
- Explainable evidence-backed output.
- Synthetic data only.
- POPIA-aligned sanitization.
- Graceful partial-data behavior.
- Cycle-safe graph traversal.
- Stable versioned contracts.
- Core analysis remains available when AI is unavailable.

## Scope boundary
This repository **does implement/prove everything required for Blast Radius**, including local mock services/database and observability. It does not rebuild unrelated MadlangaAI capabilities.

Not part of the Blast Radius domain engine:
- autonomous production remediation;
- enterprise production deployment;
- training an ML model;
- production chaos orchestration.

A controlled local failure/chaos driver is in scope because it is required to validate the engine.

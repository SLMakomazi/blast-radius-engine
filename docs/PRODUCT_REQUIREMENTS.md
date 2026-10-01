# Product Requirements — Blast Radius Engine

## Context
This repository implements and proves the **Blast Radius Analysis** capability planned for MadlangaAI Phase 4.

The current MadlangaAI MVP already defines architecture/dependency analysis, data-flow mapping, SonarQube, JaCoCo, TsakaniQA, API evaluation, Datadog application health, AI diagnosis, remediation, scorecards and reports. Its current Datadog scope guarantees traffic, latency, error-rate and zero-traffic/endpoint-health information. It does **not** currently guarantee raw logs or distributed traces.

That limitation must not reduce the Blast Radius design. Where MadlangaAI does not yet supply a capability required by Blast Radius, this repository must implement/prove it locally behind a provider contract.

## MadlangaAI supported technology landscape
Blast Radius MUST remain technology-neutral across the application types explicitly supported by the current MadlangaAI specification:

### Frontend/application structures
- Angular
- JavaScript
- TypeScript
- NX Monorepos
- Micro Frontends

### Backend/API
- Spring Boot
- Java
- REST APIs
- Node.js

### Databases
- PostgreSQL
- MongoDB
- Oracle
- IBM DB2
- SQL Server

### Messaging/event infrastructure
- IBM MQ
- AWS SQS
- AWS SNS
- ActiveMQ

### AWS/serverless/orchestration
- AWS Step Functions
- standalone AWS Lambda functions

PostgreSQL is the **canonical first local failure scenario only**. It is not a domain limitation. Core topology, evidence, correlation, origin and blast-radius models MUST NOT encode assumptions that every dependency is PostgreSQL, relational, HTTP-based or a Spring Boot service.

The component model must be extensible enough to represent at minimum services/APIs, databases, queues/topics/brokers, serverless functions, workflow/orchestration components, frontend/application components and unknown/external dependencies.

## Goal
Provide a machine-readable, evidence-backed and human-explainable blast-radius assessment for application incidents, with a complete local environment capable of producing logs, metrics, traces and endpoint-health telemetry.

## Primary user story
As an engineer investigating an incident, I want Blast Radius to correlate full runtime telemetry with dependency topology so I can understand origin, propagation, direct/indirect impact, observed degradation, evidence, confidence and recommended investigation/remediation.

## Functional requirements
### FR-01 Full telemetry ingestion
The engine MUST support normalized logs, metrics, distributed traces/spans, endpoint/application health, traffic volume, latency, error rate and availability/zero-traffic signals. A provider may return a subset, but missing categories must be explicit data-quality warnings.

### FR-02 Technology-neutral dependency topology
Accept directed dependencies from local fixtures/discovery and later MadlangaAI. Nodes must not be restricted to HTTP services/databases and must support the technology landscape above.

### FR-03 Incident correlation
Correlate telemetry by time window, component, endpoint/operation, trace/span IDs, dependency relationships, error signatures and degradation signals.

### FR-04 Suspected origin
Identify a suspected origin using transparent evidence/rules and return confidence plus reasons. Correlation must not be presented as proven causation.

### FR-05 Theoretical blast radius
Reverse-traverse dependencies from the origin to determine components that could be impacted.

### FR-06 Impact depth/path
Classify origin, direct and indirect impact and retain dependency path and minimum hop distance.

### FR-07 Observed blast radius
Mark a component observed affected only when runtime evidence indicates degradation/error.

### FR-08 Propagation timeline
Produce an ordered chronology showing origin signal and downstream degradation timing.

### FR-09 Evidence/provenance
Every origin/observed-impact assertion MUST reference supporting evidence with source, timestamp and component.

### FR-10 Deterministic severity
Calculate configurable incident severity. This remains separate from MadlangaAI Overall Health Score.

### FR-11 AI diagnosis context
Produce sanitized structured context for explanation and Immediate/Medium-term/Strategic remediation.

### FR-12 Versioned API
Expose a versioned API for local use and eventual MadlangaAI integration.

### FR-13 Partial-data behavior
Continue when one telemetry category/provider is unavailable if sufficient evidence remains; expose coverage and warnings.

### FR-14 Chaos/failure validation
Accept an injected-failure/chaos event and compare expected/theoretical, observed, unexpected impact and containment.

### FR-15 Local lab
Include Docker Compose capable of generating/observing real synthetic failures end to end.

### FR-16 Sanitization
Sanitize PII, secrets and credentials before persistence/logging/export and AI context.

## Canonical local scenario
`payment-service -> customer-service -> document-service -> postgres`

PostgreSQL is deliberately used to prove the first vertical slice. Later scenarios may add MongoDB, messaging or serverless-shaped mock dependencies without changing the core domain.

## Non-functional requirements
- Deterministic core analysis.
- Technology/provider-neutral domain model.
- Offline/local testability.
- Docker Compose startup.
- Explainable evidence-backed output.
- Synthetic data only.
- POPIA-aligned sanitization.
- Graceful partial-data behavior.
- Cycle-safe graph traversal.
- Stable versioned contracts.
- Core works without AI.

## Scope boundary
This repository implements/proves everything required for Blast Radius, including local mock services/database and observability. It does not rebuild unrelated MadlangaAI capabilities.

Not part of the Blast Radius domain engine: autonomous production remediation, enterprise production deployment, ML model training, or production chaos orchestration. A controlled local failure driver is in scope for validation.

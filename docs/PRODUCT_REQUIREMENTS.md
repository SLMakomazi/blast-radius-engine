# Product Requirements — Blast Radius Engine

## Context
This repository is a design/PoC for the **Blast Radius Analysis** capability planned for MadlangaAI.

MadlangaAI is an AI-powered software application diagnosis platform. Existing/planned platform capabilities described by the MadlangaAI v1.0 technical specification include source-code assessment, architecture visualization, dependency analysis, data-flow mapping, SonarQube, JaCoCo, TsakaniQA, API evaluation, Datadog application health, risk assessment, AI root-cause analysis, remediation recommendations, scorecards and diagnosis reports.

This engine should reuse those capabilities through integration contracts rather than duplicate the whole platform.

## Problem
An application incident can produce thousands of logs and alerts while engineers still need to determine:
- the likely origin;
- which dependencies may be impacted;
- which services are demonstrably degraded;
- how far failure propagation extends;
- the evidence behind that conclusion;
- what should be investigated next.

## Goal
Provide a machine-readable and human-explainable blast-radius assessment for an application incident.

## Primary user story
As an engineer investigating an incident, I want MadlangaAI to correlate telemetry with the application's dependency topology so I can understand the likely origin, direct impact, indirect impact, observed impact, evidence and recommended investigation steps.

## Functional requirements
### FR-01 Telemetry ingestion
Accept normalized logs, metrics and traces from a `TelemetryProvider`.

### FR-02 Dependency ingestion
Accept a service/component dependency graph supplied by mock fixtures initially and by MadlangaAI architecture/dependency data later.

### FR-03 Incident correlation
Correlate related telemetry by time window, service/component, trace identifiers and error/degradation signals.

### FR-04 Suspected origin
Identify a suspected origin from evidence and return a confidence value/reasons. The engine must not present uncertain correlation as proven causation.

### FR-05 Theoretical blast radius
Traverse the dependency graph from the failed component to determine components that could be impacted.

### FR-06 Impact depth
Classify impact at minimum as:
- origin;
- direct (one dependency hop);
- indirect (two or more hops).

### FR-07 Observed blast radius
Mark components as observed affected only when runtime evidence indicates degradation/error within the incident window.

### FR-08 Evidence
Every observed-impact and root-origin assertion must carry evidence references.

### FR-09 Severity
Calculate a deterministic severity from configurable factors such as affected criticality, number/depth of impacted components and observed degradation.

### FR-10 AI diagnosis context
Create a compact, structured context that an AI diagnosis component can use to explain probable cause and recommended investigation/remediation.

### FR-11 API
Expose a versioned API suitable for eventual MadlangaAI integration.

## Initial PoC scenario
Dependency topology:
`payment-service -> customer-service -> document-service -> postgres`

Synthetic incident:
1. PostgreSQL becomes unavailable.
2. Document Service reports database connection errors and HTTP 5xx.
3. Customer Service reports downstream timeouts.
4. Payment Service may show degraded/error behavior.

Expected result:
- origin: postgres;
- direct theoretical impact: document-service;
- indirect theoretical impact: customer-service and payment-service;
- observed impact derived only from supplied telemetry;
- evidence and investigation recommendations returned.

## Non-functional requirements
- Deterministic core analysis.
- Provider-neutral domain model.
- Unit-testable without network access.
- Explainable results with evidence.
- No secrets/PII in logs or fixtures.
- Designed for POPIA-compatible integration.
- API response should be suitable for MadlangaAI UI/report consumption.

## Out of scope for this PoC
- Autonomous production remediation.
- Infrastructure diagnosis as a broad product.
- Full Datadog integration.
- Full MadlangaAI UI.
- Chaos injection.
- Production deployment.
- Training an ML model.
- Rebuilding MadlangaAI architecture analysis.

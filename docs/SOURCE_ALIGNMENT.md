# Source Alignment

## Purpose
Separate what is confirmed by current MadlangaAI documents from what this Blast Radius repository deliberately adds for the Phase-4 capability.

The current BRD is a working baseline, not a complete Blast Radius specification.

## Confirmed from current MadlangaAI material
The supplied BRD/Test Plan establish:
- Blast Radius / Chaos Engineering is a future Phase-4 capability, not current MVP scope.
- MadlangaAI has architecture/dependency assessment and report dependency maps.
- Datadog/MCP application health covers traffic volume, latency, error rate, zero-traffic and high-risk endpoint information.
- The AI Diagnosis Engine covers diagnosis/remediation.
- Reports require architecture, data-flow, application-health, AI diagnosis and remediation content.
- PII must be masked in reports, dashboards, logs and AI-generated narrative.
- Current report behavior is intended to tolerate missing data sources by marking affected sections incomplete, although the BRD labels exact partial-data behavior as an engineering assumption.
- Current MadlangaAI Overall Health Score has its own weighted categories and does not include Blast Radius.

## Not currently guaranteed by the MadlangaAI MVP documents
The supplied documents do not guarantee:
- raw log retrieval for Blast Radius;
- distributed trace/span retrieval for Blast Radius;
- a Phase-4 Blast Radius API schema;
- a final Phase-4 chaos-event schema;
- production Blast Radius severity rules;
- production containment rules;
- final UI interaction behavior.

## Blast Radius design additions
To build a full-capability engine, this repository therefore adds:
- normalized logs;
- normalized metrics;
- normalized distributed traces/spans;
- normalized endpoint/application health;
- telemetry coverage/data-quality metadata;
- propagation timeline;
- evidence provenance;
- incident-specific deterministic severity;
- optional controlled-failure/chaos event;
- expected vs observed vs unexpected impact;
- containment assessment;
- complete Docker local lab;
- mock services/database/traffic generation;
- sanitization before evidence/AI;
- focused interactive Blast Radius UI concept.

These are **Blast Radius requirements/design decisions**, not claims that the current MadlangaAI MVP already provides them.

## Integration rule
When MadlangaAI later provides an equivalent capability, replace the local adapter with the MadlangaAI/enterprise adapter. Do not rewrite deterministic domain logic.

When MadlangaAI does not provide an equivalent capability, retain the Blast Radius requirement and integrate an approved source.

## Assumptions requiring future confirmation
- final module vs standalone-service deployment model;
- exact Datadog MCP access available in the target environment;
- exact MadlangaAI dependency schema;
- final chaos/TsakaniQA integration;
- enterprise severity/criticality policy;
- persistence location;
- auth/RBAC/audit integration;
- final dashboard/report placement.

Codex must not convert these unknowns into invented facts.

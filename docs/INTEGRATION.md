# MadlangaAI Integration Design

## Objective
Make this PoC easy to transplant into or expose as a module/service to MadlangaAI once the actual repository contracts are known.

## Existing MadlangaAI capabilities to leverage
From the v1.0 technical specification:
- architecture visualization;
- dependency analysis;
- data-flow mapping;
- Datadog application health;
- AI root-cause analysis;
- risk generation;
- remediation recommendations;
- diagnosis reports/dashboard.

Blast Radius should compose these capabilities rather than recreate them.

## Adapter mapping

| PoC port | PoC adapter | MadlangaAI adapter later |
|---|---|---|
| TelemetryProvider | JSON/mock fixtures | Datadog/MCP application-health telemetry |
| DependencyTopologyProvider | JSON/mock topology | MadlangaAI architecture/dependency model |
| AiDiagnosisProvider | Stub/template or optional LLM | MadlangaAI AI Diagnosis Engine |
| Result consumer | REST response | MadlangaAI report/dashboard/API |

## Integration contract
Core analysis accepts normalized models only. Vendor payloads must be mapped in adapters.

This means a Datadog payload should never leak throughout domain classes.

## UI/report fields MadlangaAI can consume
- incident/origin;
- confidence;
- graph path from origin to affected component;
- direct vs indirect classification;
- theoretical vs observed state;
- severity;
- evidence;
- warnings/data gaps;
- AI explanation;
- recommended investigation/remediation.

## Operational integration
Later, MadlangaAI may invoke analysis:
- manually from a diagnosis screen;
- for a selected historical incident/time window;
- from a monitoring event;
- from a CI/CD or synthetic-monitoring workflow;
- during a chaos experiment.

The PoC should not assume only one trigger.

## Open integration questions
These must be answered after access to the MadlangaAI repository/team design:
1. What is MadlangaAI's actual implementation stack and module structure?
2. Is Blast Radius an internal module or separately deployed service?
3. What exact schema does its architecture/dependency analyzer expose?
4. What Datadog MCP capabilities/endpoints are already implemented?
5. What AI provider/agent abstraction already exists?
6. What application/environment identifiers are canonical?
7. Where are diagnosis results persisted?
8. What authentication/RBAC contract must the endpoint use?
9. What report/dashboard schema should Blast Radius extend?
10. What enterprise severity/criticality rules should replace PoC defaults?

Do not guess these answers in implementation.

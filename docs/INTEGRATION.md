# MadlangaAI Integration Design

## Objective
Build Blast Radius as a complete capability that can integrate with MadlangaAI without being limited by today's MVP integrations.

## Confirmed MadlangaAI capabilities relevant to Blast Radius
Current project documents support:
- architecture/dependency assessment and dependency maps;
- data-flow analysis;
- Datadog/MCP application health;
- traffic, latency, error rate and zero-traffic/high-risk endpoint information;
- AI diagnosis/remediation;
- diagnosis reports/dashboard;
- historical diagnosis runs.

Blast Radius / Chaos Engineering is identified as a later Phase-4 capability, so this repository defines the additional contracts needed for that phase.

## Capability-gap rule
If MadlangaAI does not currently expose a Blast Radius requirement, **do not remove the requirement**.

Instead:
1. keep the normalized Blast Radius contract;
2. implement/prove it in the local lab;
3. expose a provider/adapter boundary;
4. later map to MadlangaAI/Datadog if the source becomes available;
5. otherwise integrate an approved additional source.

This applies specifically to raw logs and distributed traces.

## Adapter mapping
| Blast Radius port | Local implementation | MadlangaAI/enterprise integration |
|---|---|---|
| TelemetryProvider | local logs + metrics + traces + health adapters | Datadog/MCP plus additional approved source(s) for missing telemetry |
| DependencyTopologyProvider | synthetic topology | MadlangaAI architecture/dependency model |
| FailureExperimentProvider | local failure/chaos driver | Phase-4 chaos/TsakaniQA integration when defined |
| AiDiagnosisProvider | deterministic stub/approved local option | MadlangaAI AI Diagnosis Engine |
| Result consumer | REST + local UI | MadlangaAI report/dashboard/API |

## Integration fields
- incident ID/window;
- suspected origin and confidence;
- dependency path/hop distance;
- direct/indirect classification;
- theoretical/observed/unexpected state;
- propagation timeline;
- incident severity;
- telemetry coverage/data gaps;
- evidence/provenance;
- AI explanation;
- remediation recommendations;
- optional experiment/containment result.

## Security/compliance
All source data must pass sanitization before Blast Radius persistence/logging/export/AI. MadlangaAI POPIA masking requirements remain applicable.

## Scoring separation
Blast Radius incident severity must not modify MadlangaAI's weighted Overall Health Score unless a future approved requirement explicitly changes that scoring framework.

## Open integration questions
1. Is Blast Radius ultimately an internal module or separate service?
2. What exact MadlangaAI dependency schema will be exposed?
3. Which Datadog/MCP APIs are available for logs, metrics, APM traces and health?
4. If logs/traces are unavailable through current MCP, which approved source will supply them?
5. What canonical application/environment/component IDs should adapters use?
6. What auth/RBAC/audit contract applies?
7. Where should incident analyses be persisted?
8. What UI/report schema will Phase 4 extend?
9. What enterprise severity/criticality rules replace local defaults?
10. What is the final Chaos Engineering/TsakaniQA contract?

These are integration unknowns, not reasons to narrow the local Blast Radius capability.

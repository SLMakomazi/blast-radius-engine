# Phase 7 — Severity and Failure/Chaos Assessment

Status: **implemented on feature branch; manual verification pending**.

Phase 7 adds deterministic incident severity and controlled failure/chaos assessment on top of the evidence-backed Phase 6 incident model.

## Deterministic severity

Severity is calculated without AI and remains separate from the MadlangaAI Overall Health Score.

Current scoring factors:

| Factor | Score |
|---|---:|
| HIGH/CRITICAL origin metadata | +20 |
| each observed dependent | +10, capped at +30 |
| propagation depth >= 2 | +10 |
| propagation depth >= 3 | +20 instead |
| each unexpected component | +20, capped at +40 |
| observed health/availability degradation | +15 |

Score is capped at 100.

Levels:
- LOW: 0–19
- MEDIUM: 20–39
- HIGH: 40–69
- CRITICAL: 70–100

The response now carries an `IncidentSeverity` with level, numeric score and human-readable deterministic reasons.

## Failure experiment contract

`FailureExperimentProvider` is the provider-neutral port for experiment metadata. It deliberately does not inject failures.

`FailureExperiment` declares:
- experiment ID;
- known origin component;
- expected affected components;
- containment boundary.

`FailureExperimentAssessmentService` compares experiment expectations with observed Phase 6 component states.

Assessment returns:
- expected impact;
- observed expected impact;
- expected but unobserved impact;
- unexpected impact;
- containment status.

Containment states:
- `HELD`: observed failure stays inside the declared boundary and telemetry is complete;
- `BREACHED`: an observed failed component is outside the declared boundary;
- `INCONCLUSIVE`: no breach is observed, but telemetry is incomplete.

Partial telemetry can never prove containment success.

## Boundary

Blast Radius evaluates experiments; it does not own production chaos orchestration. A local failure driver or future TsakaniQA/chaos adapter can implement experiment execution independently.

No AI remediation or autonomous fix behavior is introduced in Phase 7.

## Manual verification to run

After pulling this branch:

```bash
mvn -f blast-radius-api/pom.xml -B -ntp \
  -Dtest=IncidentSeverityCalculatorTest,FailureExperimentAssessmentServiceTest,IncidentAnalysisServiceTest test
```

Then run the existing Docker PostgreSQL outage lifecycle and verify the analysis response contains deterministic severity while Phase 6 origin/impact behavior remains unchanged.

Do not merge until the manual verification is recorded.

# Phase 7 — Severity and Failure/Chaos Assessment

Status: **implemented and manually verified on the feature branch on 3 October 2026**.

Phase 7 adds deterministic incident severity and controlled failure/chaos assessment on top of the evidence-backed Phase 6 incident model.

## Deterministic severity

Severity is calculated without AI and remains separate from the MadlangaAI Overall Health Score.

Current scoring factors:

| Factor | Score |
|---|---:|
| HIGH/CRITICAL origin metadata | +20 |
| each observed dependent | +10, capped at +30 |
| observed propagation depth >= 2 | +10 |
| observed propagation depth >= 3 | +20 instead |
| each unexpected component | +20, capped at +40 |
| observed health/availability degradation | +15 |

Score is capped at 100.

Levels:
- LOW: 0–19
- MEDIUM: 20–39
- HIGH: 40–69
- CRITICAL: 70–100

Propagation depth is calculated only from components with an `OBSERVED` state. A deep theoretical dependency radius by itself does not escalate incident severity.

The response carries an `IncidentSeverity` with level, numeric score and human-readable deterministic reasons.

## Failure experiment contract

`FailureExperimentProvider` is the provider-neutral port for experiment metadata. It deliberately does not inject failures.

`FailureExperiment` declares:
- experiment ID;
- known origin component;
- expected affected components;
- containment boundary.

The local Phase 7 catalogue includes:

`postgres-outage-local`
- origin: `postgres`
- expected affected components: `document-service`, `customer-service`, `payment-service`
- containment boundary: `postgres`, `document-service`, `customer-service`, `payment-service`

The analysis endpoint accepts an optional `experimentId`. When a known experiment is supplied, its declared origin is used as the controlled failure origin while telemetry still supplies the evidence and confidence. Unknown experiments are rejected.

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

Blast Radius evaluates experiments; it does not own production chaos orchestration. The local verification deliberately used `docker compose stop postgres` as the controlled failure driver. A future TsakaniQA/chaos adapter can implement experiment execution independently.

No AI remediation or autonomous fix behavior is introduced in Phase 7.

## Automated verification

Focused Phase 7 tests were run on 3 October 2026:

```bash
mvn -f blast-radius-api/pom.xml -B -ntp \
  -Dtest=IncidentSeverityCalculatorTest,FailureExperimentAssessmentServiceTest,IncidentAnalysisServiceTest test
```

Result:

```text
Tests run: 23
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

Coverage includes:
- deterministic severity calculation;
- theoretical dependency depth does not escalate severity without observed propagation;
- `HELD` containment;
- `BREACHED` containment;
- `INCONCLUSIVE` containment under partial telemetry;
- controlled experiment integration into incident analysis;
- rejection of unknown experiment IDs.

## Manual verification — healthy baseline

A healthy narrow-window analysis with `originHint=postgres` retained the learned dependency chain:

```text
postgres -> document-service -> customer-service -> payment-service
```

All dependents were `THEORETICAL_ONLY`. Telemetry coverage was complete and severity was:

```text
level: LOW
score: 0
reason: no configured severity escalation factor was observed
```

This verifies that potential blast-radius depth is not treated as actual incident severity.

## Manual verification — controlled PostgreSQL outage

Experiment:

```text
experimentId: postgres-outage-local
outage window: 2026-10-03T05:20:12Z -> 2026-10-03T05:20:54Z
correlationId: phase7-experiment-1791004821
```

PostgreSQL was stopped manually. A fresh request through `payment-service` returned HTTP 502 with `DOWNSTREAM_FAILURE`.

The experiment-aware analysis was executed without an explicit `originHint`.

Observed result:

```text
origin: postgres
origin confidence: HIGH
coverage.fullyCovered: true
health: AVAILABLE
logs: AVAILABLE
metrics: AVAILABLE
traces: AVAILABLE

postgres          ORIGIN    distance 0
document-service  OBSERVED  distance 1
customer-service  OBSERVED  distance 2
payment-service   OBSERVED  distance 3

severity: HIGH
severity score: 50
```

Experiment assessment:

```text
experimentId: postgres-outage-local
containment: HELD

expected impact:
  postgres
  document-service
  customer-service
  payment-service

observed expected impact:
  postgres
  document-service
  customer-service
  payment-service

expected but unobserved: none
unexpected impact: none
```

The `HELD` result is valid because all telemetry families were available and the observed failure stayed within the declared containment boundary.

## Recovery verification

PostgreSQL was restarted after the experiment. Both `postgres` and `document-service` returned healthy.

A fresh end-to-end payment request with correlation ID `phase7-final-recovery-1791005445` returned:

```text
HTTP 201
status: PROCESSED
```

This confirms recovery of the synthetic business path after the controlled failure.

## Phase 7 acceptance

Phase 7 manual acceptance is complete:

- healthy theoretical radius does not inflate incident severity;
- a real PostgreSQL outage is correlated to the correct origin;
- observed propagation is reconstructed across three dependency hops;
- deterministic incident severity reflects observed impact;
- complete telemetry allows containment to be proven as `HELD`;
- unit tests cover `BREACHED` and partial-telemetry `INCONCLUSIVE` behavior;
- recovery is verified after the controlled experiment;
- failure injection remains outside the Blast Radius Engine boundary.

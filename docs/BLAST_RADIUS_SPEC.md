# Blast Radius Analysis Specification

## Impact states
- **Origin** — suspected earliest/root failure, evidence-backed unless known from a controlled experiment.
- **Theoretical** — component could be affected based on dependency topology.
- **Observed** — runtime telemetry proves degradation/error during the incident window.
- **Unexpected** — observed affected but outside the expected/theoretical or declared containment model.
- **Direct** — reverse-dependent one hop from origin.
- **Indirect** — reverse-dependent two or more hops.

## Graph semantics
For `A -> B -> C`, A depends on B and B depends on C. If C fails, reverse traversal returns B at distance 1 and A at distance 2. Use a visited set and retain minimum distance/path.

## Full analysis algorithm
1. Load dependency topology.
2. Load logs, metrics, traces and health telemetry with coverage metadata.
3. Sanitize/redact raw evidence.
4. Correlate signals by time, component, endpoint, trace and dependency.
5. Determine suspected origin/confidence/reasons or record controlled-failure ground truth.
6. Reverse-traverse topology.
7. Record minimum hop distance, path and direct/indirect classification.
8. Evaluate observed degradation using available telemetry.
9. Construct propagation timeline.
10. Calculate deterministic incident severity.
11. If experiment metadata exists, compare expected/theoretical/observed/unexpected impact and containment.
12. Build evidence-backed result.
13. Build sanitized AI diagnosis context.
14. Optionally request AI explanation/remediation.

## Observed-impact signals
Configurable evidence includes:
- error logs;
- failed spans;
- trace latency/failure propagation;
- HTTP 5xx/error-rate increase;
- timeout increase;
- latency degradation;
- availability/health failure;
- unexpected zero traffic;
- dependency-specific connection failures.

A component must list the evidence used to mark it observed.

## Origin assessment
Initial transparent factors may include:
- earliest strong failure signal;
- dependency position;
- DB/dependency connection errors;
- trace peer-service failures;
- health transition;
- correlated downstream symptoms.

Return confidence and reasons. Do not claim proven causation from correlation alone.

## Propagation timeline
Each timeline event contains timestamp, component, signal type, evidence ID and relative offset from the origin signal.

## Severity
Configurable factors:
- criticality of origin/affected nodes;
- observed affected count;
- propagation depth;
- critical business component;
- error/latency/availability magnitude;
- containment breach.

Severity is an incident concept and is not the MadlangaAI Overall Health Score.

## API
### POST /api/v1/blast-radius/analyze
```json
{
  "applicationId":"document-platform",
  "environment":"local",
  "from":"2026-10-01T10:30:00Z",
  "to":"2026-10-01T10:35:00Z",
  "originHint":"postgres",
  "experimentId":"chaos-001"
}
```

Example response shape:
```json
{
  "incidentId":"INC-001",
  "origin":{"component":"postgres","confidence":0.91,"evidenceIds":["health-db-1"]},
  "telemetryCoverage":{"logs":"AVAILABLE","metrics":"AVAILABLE","traces":"AVAILABLE","health":"AVAILABLE"},
  "theoreticalImpact":[
    {"component":"document-service","distance":1,"classification":"DIRECT","path":["postgres","document-service"]},
    {"component":"customer-service","distance":2,"classification":"INDIRECT","path":["postgres","document-service","customer-service"]}
  ],
  "observedImpact":[
    {"component":"document-service","evidenceIds":["log-1","span-1","metric-1"]}
  ],
  "propagationTimeline":[
    {"timestamp":"2026-10-01T10:31:00Z","component":"postgres","signal":"HEALTH_DOWN","offsetMs":0},
    {"timestamp":"2026-10-01T10:31:02Z","component":"document-service","signal":"DB_ERROR","offsetMs":2000}
  ],
  "severity":"HIGH",
  "experimentAssessment":{"containment":"HELD","unexpectedImpact":[]},
  "diagnosis":{"summary":"...","recommendations":[]},
  "warnings":[]
}
```

## Explainability
The UI/report must answer:
- Why is this component in the theoretical radius?
- Why is it marked observed?
- What path connects it to the origin?
- Which evidence proves degradation?
- When did degradation begin relative to the origin?
- What telemetry was unavailable?
- Did impact escape the expected containment boundary?

## Local validation
The canonical Docker lab must produce real synthetic telemetry from service-to-service calls and controlled failures. Static JSON fixtures remain useful for unit tests but are not sufficient as the sole proof.

# Blast Radius Analysis Specification

## Terminology

### Origin
The component suspected to be the earliest/root failure for the analyzed incident. This is a hypothesis supported by evidence unless causation is independently known.

### Theoretical blast radius
Components that **could** be affected based on dependency topology.

### Observed blast radius
Components for which runtime telemetry provides evidence of actual degradation/error during the incident window.

### Direct impact
A reverse-dependent one hop from the origin.

### Indirect impact
A reverse-dependent two or more hops from the origin.

## Graph semantics
If:
```text
A -> B -> C
```
then A depends on B and B depends on C.

If C fails, reverse traversal produces:
- B: distance 1/direct;
- A: distance 2/indirect.

Use a visited set so cycles terminate.

## Analysis algorithm — PoC
1. Load topology.
2. Load normalized telemetry for the requested window.
3. Correlate error/degradation signals.
4. determine suspected origin and evidence.
5. Reverse-traverse topology from origin.
6. Record each reachable component and minimum hop distance.
7. Evaluate runtime evidence for each reachable component.
8. Calculate severity deterministically.
9. Build evidence-backed result.
10. Build sanitized AI diagnosis context.
11. Optionally request an AI explanation/recommendation.

## Observed-impact rules — initial
A service may be marked observed affected when at least one configured signal exists in the incident window, for example:
- error log;
- failed trace/span;
- HTTP 5xx increase;
- timeout increase;
- availability/health failure.

The result must list the signals used.

## Severity — initial design
Keep thresholds configurable. Suggested factors:
- criticality of origin/affected nodes;
- number of observed affected components;
- maximum propagation depth;
- presence of a critical business/service component;
- magnitude of degradation where metrics are available.

Do not hard-code an unchangeable enterprise policy into the PoC.

## API

### POST /api/v1/blast-radius/analyze
Example request:
```json
{
  "applicationId": "document-platform",
  "environment": "dev",
  "from": "2026-10-01T10:30:00Z",
  "to": "2026-10-01T10:35:00Z",
  "originHint": "postgres"
}
```

`originHint` is optional and useful for deterministic PoC/demo scenarios.

Example response:
```json
{
  "incidentId": "INC-001",
  "applicationId": "document-platform",
  "origin": {
    "component": "postgres",
    "confidence": 0.91,
    "evidenceIds": ["log-db-001", "span-db-001"]
  },
  "theoreticalImpact": [
    {"component":"document-service","distance":1,"classification":"DIRECT"},
    {"component":"customer-service","distance":2,"classification":"INDIRECT"},
    {"component":"payment-service","distance":3,"classification":"INDIRECT"}
  ],
  "observedImpact": [
    {
      "component":"document-service",
      "evidenceIds":["log-001","metric-001"]
    },
    {
      "component":"customer-service",
      "evidenceIds":["log-002"]
    }
  ],
  "severity": "HIGH",
  "diagnosis": {
    "summary": "Database connectivity failure with propagated service degradation.",
    "recommendations": [
      "Verify database availability",
      "Inspect connectivity and connection-pool health",
      "Review relevant recent configuration/deployment changes"
    ]
  },
  "warnings": []
}
```

## Explainability requirement
The UI/report must be able to answer "Why is this service shown as affected?" using evidence IDs and graph path.

## Future extension: chaos validation
A chaos experiment can supply a known injected failure. The engine can compare:
- expected/theoretical impact;
- observed impact;
- unexpected impact;
- containment success/failure.

This repository does not inject chaos itself.

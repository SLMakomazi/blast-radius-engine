# Phase 6 — Evidence Correlation and Observed Impact

Status: implementation ready for manual verification.

Phase 6 combines the normalized Phase 4 telemetry boundary with the Phase 5 dependency graph to distinguish potential impact from evidence-backed observed impact.

## Implemented

- deterministic correlation of sanitized error logs, failed spans, health degradation and metric deltas;
- dependency-error evidence for peer components;
- suspected-origin selection using the deepest failing dependency represented in the discovered topology;
- optional `originHint` for controlled experiments and theoretical analysis;
- confidence levels and evidence scores;
- theoretical versus observed versus unexpected versus unknown classification;
- propagation timeline ordered from evidence timestamps;
- partial-observability handling: missing evidence is never treated as healthy;
- counter metrics require an increase inside the requested window rather than a historical non-zero absolute value;
- read-only HTTP analysis endpoint.

## HTTP API

`GET /api/v1/blast-radius/analyze`

Parameters:

- `applicationId` required;
- `environment` defaults to `local`;
- `from` optional ISO-8601 instant;
- `to` optional ISO-8601 instant;
- `originHint` optional component ID.

If no time range is supplied the endpoint analyzes the previous 15 minutes.

Example:

```bash
curl -sS "http://localhost:8080/api/v1/blast-radius/analyze?applicationId=document-platform&environment=local&originHint=postgres"
```

The endpoint is read-only. It does not execute remediation, mutate monitored services or invoke AI.

## Interpretation

- `ORIGIN`: suspected or explicitly supplied origin.
- `OBSERVED`: component is in the theoretical radius and has failure evidence.
- `THEORETICAL_ONLY`: component is in the theoretical radius but no failure evidence was found while telemetry coverage is complete.
- `UNKNOWN`: component is theoretical but telemetry coverage is incomplete and no failure evidence exists.
- `UNEXPECTED`: failure evidence exists for a component outside the theoretical radius.

## Verification plan

1. run the complete Blast Radius API Maven regression;
2. rebuild only `blast-radius-api`;
3. baseline curl with `originHint=postgres`;
4. stop PostgreSQL while traffic continues;
5. wait for failure evidence;
6. curl analysis without `originHint` and verify suspected origin and propagation;
7. restore PostgreSQL;
8. verify business recovery;
9. record final results here before merge.

The deterministic engine remains advisory/read-only and separate from AI diagnosis or autonomous fixes.

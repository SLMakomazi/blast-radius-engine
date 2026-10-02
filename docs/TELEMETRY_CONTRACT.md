# Telemetry Contract

## Purpose
Define the **full Blast Radius normalized telemetry model**. The contract is intentionally broader than the current MadlangaAI Datadog MVP contract.

Blast Radius supports four primary evidence families:
1. logs;
2. metrics;
3. distributed traces/spans;
4. endpoint/application health.

Traffic, latency, error rate, availability and zero-traffic are represented through metrics/health observations.

A provider may not support every family. Unsupported/unavailable families must be reported in `coverage` and `warnings`; they must never be fabricated.

## Query
```json
{
  "applicationId": "document-platform",
  "environment": "local",
  "from": "2026-10-01T10:30:00Z",
  "to": "2026-10-01T10:35:00Z"
}
```

## TelemetryBundle
```json
{
  "logs": [],
  "metrics": [],
  "spans": [],
  "health": [],
  "coverage": {
    "logs": "AVAILABLE",
    "metrics": "AVAILABLE",
    "traces": "AVAILABLE",
    "health": "AVAILABLE"
  },
  "warnings": []
}
```

Coverage values: `AVAILABLE`, `PARTIAL`, `UNAVAILABLE`, `NOT_SUPPORTED`.

## Normalized log event
```json
{
  "id": "log-001",
  "timestamp": "2026-10-01T10:31:02Z",
  "service": "document-service",
  "environment": "local",
  "level": "ERROR",
  "message": "Database connection failed",
  "traceId": "trace-1001",
  "attributes": {"error.type":"ConnectionException"},
  "provenance": {"provider":"local-logs","sourceRef":"..."}
}
```

## Normalized metric sample
```json
{
  "id": "metric-001",
  "timestamp": "2026-10-01T10:31:05Z",
  "service": "document-service",
  "name": "http.server.error.rate",
  "value": 0.74,
  "unit": "ratio",
  "attributes": {"endpoint":"POST /documents"},
  "provenance": {"provider":"local-metrics","sourceRef":"..."}
}
```

Required metric concepts for the lab include request/traffic volume, latency, error rate and availability/health where applicable.

## Normalized trace/span
```json
{
  "id": "span-001",
  "traceId": "trace-1001",
  "spanId": "span-a",
  "parentSpanId": "span-parent",
  "timestamp": "2026-10-01T10:31:02Z",
  "durationMs": 1250,
  "service": "document-service",
  "operation": "POST /documents",
  "status": "ERROR",
  "peerService": "postgres",
  "attributes": {"error.type":"ConnectionException"},
  "provenance": {"provider":"local-traces","sourceRef":"..."}
}
```

## Normalized health observation
```json
{
  "id": "health-001",
  "timestamp": "2026-10-01T10:31:06Z",
  "service": "document-service",
  "endpoint": "/actuator/health",
  "status": "DEGRADED",
  "latencyMs": 4200,
  "errorRate": 0.74,
  "trafficCount": 12493,
  "zeroTraffic": false,
  "provenance": {"provider":"local-health","sourceRef":"..."}
}
```

## Dependency topology
Edge semantics: `from` depends on `to`.
```json
{
  "applicationId": "document-platform",
  "nodes": [
    {"id":"payment-service","type":"SERVICE","criticality":"HIGH"},
    {"id":"customer-service","type":"SERVICE","criticality":"HIGH"},
    {"id":"document-service","type":"SERVICE","criticality":"HIGH"},
    {"id":"postgres","type":"DATABASE","criticality":"CRITICAL"}
  ],
  "edges": [
    {"from":"payment-service","to":"customer-service"},
    {"from":"customer-service","to":"document-service"},
    {"from":"document-service","to":"postgres"}
  ]
}
```

## Chaos/failure event
```json
{
  "experimentId": "chaos-001",
  "timestamp": "2026-10-01T10:31:00Z",
  "targetComponent": "postgres",
  "failureType": "SERVICE_STOP",
  "expectedContainmentBoundary": ["document-service","customer-service"],
  "source": "local-failure-driver"
}
```

## Provider interfaces
```java
public interface TelemetryProvider {
    TelemetryBundle getTelemetry(TelemetryQuery query);
}

public interface DependencyTopologyProvider {
    DependencyTopology getTopology(String applicationId, String environment);
}

public interface FailureExperimentProvider {
    Optional<FailureExperiment> getExperiment(String experimentId);
}
```

## Data quality warnings
Providers must report, where relevant:
- incomplete time range;
- unavailable/not-supported telemetry family;
- unknown service mapping;
- duplicate events;
- clock skew;
- provider timeout;
- missing trace context;
- dropped telemetry;
- stale health data.

## Sanitization boundary
Raw provider data is untrusted.

Required flow:
`provider -> sanitization/redaction -> normalized evidence -> correlation -> persistence/export/AI`.

Never expose credentials/tokens. PII and secret-like values must be redacted before normalized evidence is logged, persisted or sent to AI. Preserve safe provider/source references for auditability.

---

## Phase 4 implementation status

**Implementation complete on `feat/normalized-telemetry-sanitization`.
Manual test verification pending — tests are written but not yet executed.**

### Java types implemented

| Contract element | Java type | Package |
|---|---|---|
| TelemetryQuery | `TelemetryQuery` | `domain.evidence` |
| TelemetryBundle | `TelemetryBundle` | `domain.evidence` |
| Coverage | `TelemetryCoverage`, `CoverageStatus` | `domain.evidence` |
| Log evidence | `LogEvidence` | `domain.evidence` |
| Metric evidence | `MetricEvidence` | `domain.evidence` |
| Span evidence | `SpanEvidence`, `SpanStatus` | `domain.evidence` |
| Health evidence | `HealthEvidence`, `HealthState` | `domain.evidence` |
| Provenance | `EvidenceProvenance`, `EvidenceFamily` | `domain.evidence` |
| Port | `TelemetryProvider` | `ports` |
| Sanitization | `TelemetrySanitizer`, `RedactionRule`, `BuiltInRedactionRules`, `RedactionPlaceholders` | `sanitization` |
| Loki adapter | `LokiLogAdapter` | `adapters.telemetry.loki` |
| Prometheus adapter | `PrometheusMetricsAdapter` | `adapters.telemetry.prometheus` |
| Tempo adapter | `TempoTraceAdapter` | `adapters.telemetry.tempo` |
| Health adapter | `ActuatorHealthAdapter` | `adapters.telemetry.health` |
| Composite | `LocalTelemetryProvider` | `adapters.telemetry` |

### Critical invariant
`CoverageStatus.UNAVAILABLE` for any family does **not** mean the corresponding
services are healthy. Telemetry availability and application health are entirely
separate concepts (ADR-017). All callers must inspect `TelemetryCoverage`, not
infer health from empty evidence lists.

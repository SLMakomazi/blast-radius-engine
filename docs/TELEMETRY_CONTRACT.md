# Telemetry Contract

## Purpose
Define a provider-neutral model so mock data can later be replaced by Datadog/MCP or another observability source without rewriting blast-radius logic.

## Query
```json
{
  "applicationId": "document-platform",
  "environment": "dev",
  "from": "2026-10-01T10:30:00Z",
  "to": "2026-10-01T10:35:00Z"
}
```

## Normalized log event
```json
{
  "id": "log-001",
  "timestamp": "2026-10-01T10:31:02Z",
  "service": "document-service",
  "environment": "dev",
  "level": "ERROR",
  "message": "Connection refused to PostgreSQL",
  "traceId": "trace-1001",
  "attributes": {
    "error.type": "ConnectionException"
  }
}
```

## Normalized metric event
```json
{
  "id": "metric-001",
  "timestamp": "2026-10-01T10:31:05Z",
  "service": "document-service",
  "name": "http.server.errors",
  "value": 82,
  "unit": "count",
  "attributes": {}
}
```

## Normalized trace/span event
```json
{
  "id": "span-001",
  "traceId": "trace-1001",
  "spanId": "span-a",
  "parentSpanId": null,
  "timestamp": "2026-10-01T10:31:02Z",
  "durationMs": 1250,
  "service": "document-service",
  "operation": "POST /documents",
  "status": "ERROR",
  "peerService": "postgres",
  "attributes": {
    "error.type": "ConnectionException"
  }
}
```

## Dependency topology
Edge semantics are important: `from` depends on `to`.

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

## Provider interface
Conceptual Java contract:

```java
public interface TelemetryProvider {
    TelemetryBundle getTelemetry(TelemetryQuery query);
}
```

```java
public interface DependencyTopologyProvider {
    DependencyTopology getTopology(String applicationId, String environment);
}
```

## Data quality
Providers should return warnings for:
- incomplete time range;
- unavailable telemetry type;
- unknown service mapping;
- duplicate events;
- clock skew concerns;
- provider timeout.

## Privacy/security
- Never put credentials or tokens into normalized attributes.
- Redact/mask PII before persistence, logs or AI prompts.
- Preserve provider IDs where safe so evidence can be traced back.
- Synthetic fixtures must contain no real customer information.

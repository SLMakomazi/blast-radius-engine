# Test Strategy

## Principle
Blast Radius must be proven end to end locally, not only through static fixtures.

Testing has two layers:
1. deterministic unit/contract tests;
2. Docker-based system tests using real synthetic service calls, database failures and emitted telemetry.

## Unit tests
### Graph traversal
- chain, fan-out, fan-in;
- direct/indirect;
- multiple paths/minimum distance;
- cycle;
- disconnected/unknown node;
- duplicate edge.

### Correlation and observed impact
- error log;
- failed span;
- latency degradation;
- error-rate increase;
- zero traffic;
- health failure;
- healthy telemetry;
- mixed evidence;
- evidence IDs/provenance retained;
- propagation ordering.

### Partial telemetry
Test every family missing independently:
- logs unavailable;
- metrics unavailable;
- traces unavailable;
- health unavailable;
- multiple unavailable;
- all unavailable -> explicit insufficient-evidence result/error.

### Origin/confidence
- known earliest DB failure;
- downstream symptom must not automatically become origin;
- conflicting signals;
- origin hint;
- low-confidence result.

### Severity
- threshold boundaries;
- critical component;
- propagation depth;
- degradation magnitude;
- no observed impact;
- configuration changes;
- prove separation from MadlangaAI health score.

### Privacy/security
- PII redaction;
- credential/token redaction;
- secret-like attribute filtering;
- sanitized AI context;
- sanitized exports/logging.

### Chaos comparison
- expected == observed;
- theoretical but unobserved;
- unexpected affected node;
- containment boundary held;
- containment boundary breached.

## Contract tests
Each TelemetryProvider must satisfy the same normalized contract and coverage semantics.

## Docker system tests
The canonical lab must start with Docker Compose and include:
- blast-radius-engine;
- payment-service;
- customer-service;
- document-service;
- postgres;
- traffic generator;
- log collection/backend;
- metrics backend;
- OpenTelemetry Collector + trace backend;
- health probes/collector;
- failure driver.

### Scenario A — healthy baseline
Generate traffic and verify logs, metrics, traces and health exist for all expected services.

### Scenario B — PostgreSQL outage
Stop/block PostgreSQL while traffic continues. Verify:
- document-service DB errors/failed spans/5xx or health degradation;
- customer-service downstream errors/timeouts;
- payment-service behavior according to scenario;
- Blast Radius returns postgres origin/ground-truth comparison;
- document direct theoretical;
- customer/payment indirect theoretical;
- observed nodes supported by evidence;
- propagation timeline ordered correctly.

### Scenario C — theoretical but healthy upstream
Configure resilience so payment-service remains healthy. Verify it remains theoretical-only.

### Scenario D — partial observability
Disable one telemetry backend/exporter and verify analysis continues with a clear coverage warning.

### Scenario E — containment
Inject failure with an expected boundary and verify containment success/failure calculation.

### Scenario F — sanitization
Emit synthetic PII/secret-like test values and verify they never appear in normalized evidence, Blast Radius logs, export or AI context.

## PoC acceptance criteria
1. One documented Docker Compose startup creates the full lab.
2. No external Datadog credentials are required.
3. Healthy traffic produces all four telemetry families.
4. Controlled failure produces correlatable evidence.
5. Graph cycles are safe.
6. Theoretical and observed impact are distinct.
7. Propagation timeline is generated.
8. Evidence/provenance supports observed impact.
9. Partial telemetry is explicit.
10. AI can be disabled.
11. PII/secrets are sanitized.
12. Chaos/failure event comparison works.
13. Stable versioned JSON API is produced.
14. Synthetic fixtures only.
15. Mock/local providers can later be replaced without domain rewrite.

## Implemented Phase 3 checks

Preserve all Phase 1/2 behavior tests. Mock-service context tests additionally assert
Prometheus metrics exist and `/actuator/env`, `/actuator/configprops` and
`/actuator/heapdump` return 404. These tests run without the Java agent; actual agent
compatibility is exercised by the container experiment, not inferred from Maven.

`./scripts/verify-observability.sh --output /tmp/blast-radius-phase3` runs against
the real Podman lab. It checks all application scrape targets, backend readiness,
controlled endpoint exposure, response correlation IDs and persisted documents.
For healthy, PostgreSQL outage and recovery requests it retrieves correlated Loki
logs, follows their trace ID to Tempo, checks parent ancestry across all three
services, JDBC evidence and failure span statuses, and checks increasing HTTP
status counters plus latency/client/pool metrics. Health records distinguish DB
readiness, process liveness and the independent API.

Synthetic authorization/password-header canaries must not appear in retrieved
telemetry. Resource/span allowlists and Loki index-label checks supplement that
probe; no test claims arbitrary PII detection. During Collector loss the script
checks successful business persistence, ongoing metrics/health and absent new
logs/trace, then restores export and retrieves fresh evidence. PostgreSQL and the
Collector are restored in finally blocks. Do not run competing outage scripts
concurrently. Inspect container/VM memory and OOM status under traffic separately.

Actual counts, commands, IDs and limitations are in `PHASE3_VERIFICATION.md`. The
original engine tests above remain planned; Phase 3 does not implement graph or
TelemetryBundle contracts to make those tests pass prematurely.

## Implemented Phase 4 unit tests

Tests are written on `feat/normalized-telemetry-sanitization` and staged for manual
execution. **Tests have NOT been run; results are not claimed.** Run with:

```bash
mvn -f blast-radius-api/pom.xml test
```

| Test class | Covers |
|---|---|
| `TelemetryQueryTest` | Required fields, optional filters, from/to validation, null rejection |
| `EvidenceProvenanceTest` | Field preservation, null sourceRef default, equality/hashCode |
| `LogEvidenceTest` | Builder, correlationId, traceId/spanId preservation, timestamp, sanitized attributes, immutability |
| `MetricEvidenceTest` | Builder, timestamp, error rate, latency, Hikari pool, dimension immutability |
| `SpanEvidenceTest` | Builder, traceId/spanId, root span detection, timestamp, duration, error+errorType, JDBC peer service, cross-service propagation, immutability |
| `HealthEvidenceTest` | UP/DOWN/DEGRADED/UNKNOWN mapping, timestamp, latencyMs, details, immutability |
| `TelemetryBundleTest` | Full bundle, partial bundle, UNAVAILABLE≠healthy invariant, NOT_SUPPORTED vs UNAVAILABLE, warnings, list immutability |
| `TelemetrySanitizerTest` | Authorization header, Bearer redaction in free text, password, api-key, client-secret, access/refresh token, cookie/session, SA ID number, safe fields preserved, null map/message, custom rule injection |
| `LokiLogAdapterTest` | Unreachable provider, null response, stream→LogEvidence mapping, correlationId/traceId/spanId preservation, authorization attribute sanitization |
| `PrometheusMetricsAdapterTest` | Unreachable provider, matrix series mapping, timestamp preservation, empty series=UNAVAILABLE, label sanitization |
| `TempoTraceAdapterTest` | Unreachable provider, OTLP batch→SpanEvidence mapping, traceId/spanId preservation, error span+errorType, empty batches=UNAVAILABLE |
| `ActuatorHealthAdapterTest` | UP→UP mapping, unreachable=UNKNOWN (not silent), DOWN=real evidence, timestamp preservation, component filter, provenance provider ID |
| `LocalTelemetryProviderTest` | Full bundle assembly, logs-unavailable does not suppress other families, all-unavailable with warnings, unhandled adapter exception is isolated, missing evidence is not interpreted as healthy |

Total new Phase 4 test methods: approximately **60** across 13 test classes.
These supplement the existing 36 Java + 4 Python tests from Phases 1–3.

## Phase 6 retained-topology regression

Use the focused test command in [Phase 6 verification](PHASE6_VERIFICATION.md#tests-actually-executed) before an API-only image build. Captured OTLP fixtures reproduce the healthy technology-only pool span and the failed peer-less span. Tests must distinguish knowledge retention from current failure evidence, cover expiry/restart/scope/duplicates/ambiguous dependencies, and reject out-of-window evidence. Live acceptance must report actual topology, current origin/paths, provider coverage and timestamp bounds. Recovery is a separate approved experiment; never start an intentionally stopped database as part of these regressions.

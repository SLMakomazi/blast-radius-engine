# Phase 4 Verification — Normalized Telemetry and Sanitization

**Acceptance: PASSED on 2026-10-02.**

Phase 4 proves the provider-neutral evidence boundary required before deterministic Blast Radius analysis. The implementation normalizes Loki logs, Prometheus metrics, Tempo spans and Actuator health into domain evidence through the `TelemetryProvider` port, sanitizes evidence before domain use, preserves provenance and reports per-family coverage explicitly.

Branch: `feat/normalized-telemetry-sanitization`.

## Scope verified

Phase 4 adds normalized log, metric, span and health evidence; `TelemetryBundle` and `TelemetryCoverage`; provenance; provider adapters; sanitization/redaction; and `LocalTelemetryProvider` aggregation.

It does **not** implement Phase 5 graph traversal, origin inference, observed-impact classification, incident severity, AI diagnosis, persistence or UI.

## Final verification results

| Verification | Result |
| --- | --- |
| Blast Radius API regression suite | 109 tests, 0 failures, 0 errors, 0 skipped |
| Payment service regression | 17 tests passed |
| Customer service regression | 12 tests passed |
| Document service regression | 6 tests passed |
| Traffic generator regression | 4 tests passed |
| Live `LocalTelemetryProvider` integration | 1 test passed against real Docker providers |

Regular regression total: **148 tests passed**. Including the separately invoked live integration test: **149 successful verification checks/tests**.

Commands used:

```bash
mvn -f blast-radius-api/pom.xml clean test
mvn -f mock-services/payment-service/pom.xml test
mvn -f mock-services/customer-service/pom.xml test
mvn -f mock-services/document-service/pom.xml test
cd traffic-generator && python3 -m unittest -v test_traffic.py
mvn -f blast-radius-api/pom.xml -Dtest=LocalTelemetryProviderLiveIT test
```

The live integration test is intentionally opt-in and is not discovered by the normal Surefire `*Test` pattern. It starts the Spring context without a web server, autowires the `TelemetryProvider` port and points provider configuration at the already-running Docker lab on localhost. No temporary REST/debug endpoint was added.

## Real-provider compatibility proven

### Loki logs

The live test initially failed with logs reported `UNAVAILABLE`. Runtime investigation exposed two integration assumptions that mocked tests had not caught:

1. raw LogQL containing `{...}` was being interpreted by Spring URI construction as URI-template syntax;
2. real OTel/Loki correlation, trace and span values are exposed as structured metadata. The trace field is `trace_id`, not `traceId`.

The adapter now safely encodes LogQL query parameters and maps `correlation_id`, `trace_id`, `span_id` and `severity_text` from the observed Loki structured-metadata shape, with parsed log-body fields as fallback.

Loki index labels remain the low-cardinality `service_name` and `deployment_environment_name`; correlation/trace/span values are treated as structured metadata rather than claimed as index labels.

### Prometheus metrics

Runtime queries confirmed the configured application metrics expose the `service` label using the expected values:

- `blast-radius-api`
- `payment-service`
- `customer-service`
- `document-service`

The current component filtering therefore matches the actual local Prometheus series. No runtime compatibility change was required.

### Tempo traces

Runtime inspection of `/api/traces/{traceId}` confirmed Tempo returns OTLP JSON byte fields `traceId`, `spanId` and `parentSpanId` as Base64. Loki and Tempo search use hexadecimal identifiers.

The adapter now normalizes valid 8-byte span IDs and 16-byte trace IDs from Base64 to lowercase hexadecimal before producing `SpanEvidence`. Already-valid hexadecimal IDs are preserved/normalized. Regression coverage uses the observed Tempo payload shape.

This is required for later deterministic cross-provider correlation; the domain must not compare Loki hexadecimal IDs with raw Tempo Base64 IDs.

### Actuator health

Real Docker endpoints returned HTTP 200 with `status=UP` for the API, payment and customer health endpoints and document readiness. The existing Actuator adapter was compatible with the observed payloads and required no healthy-state mapping change.

## Whole-provider live acceptance

After the runtime fixes, `LocalTelemetryProviderLiveIT` passed against the real running providers:

- Loki logs: `AVAILABLE` and non-empty;
- Prometheus metrics: `AVAILABLE` and non-empty;
- Tempo traces: `AVAILABLE` and non-empty;
- Actuator health: `AVAILABLE` and non-empty;
- returned Tempo trace IDs matched lowercase 32-hex format;
- returned Tempo span IDs matched lowercase 16-hex format.

This proves the full path:

```text
TelemetryProvider
    -> LocalTelemetryProvider
       -> LokiLogAdapter
       -> PrometheusMetricsAdapter
       -> TempoTraceAdapter
       -> ActuatorHealthAdapter
    -> normalized TelemetryBundle
```

The test does not claim Phase 5 analysis capability. It verifies evidence acquisition, normalization, aggregation and coverage only.

## Sanitization and failure semantics

The regression suite verifies the sanitization boundary and provider-failure isolation. Provider failures return explicit coverage/warnings rather than being interpreted as healthy. Negative-path tests intentionally produce warning/error log entries for simulated unreachable providers and adapter exceptions; those messages are expected and the tests pass.

Phase 3's availability rule remains authoritative: business availability and telemetry availability are independent. Missing evidence is never equivalent to healthy evidence.

## Defects found by live verification

Live validation materially improved the implementation rather than merely confirming mocks:

- Loki structured metadata did not match the original body-only mapping assumption.
- Loki trace metadata uses `trace_id`.
- LogQL required safe URI parameter handling.
- Tempo OTLP byte IDs required Base64-to-hex normalization.

Each discovered mismatch received a regression test before final acceptance.

## Exit assessment

Phase 4 exit criterion is satisfied: the local lab can build a provider-neutral `TelemetryBundle` from real logs, metrics, traces and health evidence, while retaining explicit coverage/provenance and the sanitization boundary.

Phase 5 can therefore begin with deterministic topology/graph logic consuming provider-neutral evidence contracts rather than backend-specific payloads.

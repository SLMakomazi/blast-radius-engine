# Test Strategy

## Principle
The deterministic blast-radius core must be fully testable offline with synthetic data.

## Unit tests

### Graph traversal
- single chain;
- fan-out/fan-in;
- direct dependency;
- multiple indirect levels;
- circular dependency;
- disconnected node;
- duplicate edge;
- unknown origin;
- minimum distance when multiple paths exist.

### Observed impact
- theoretical but no telemetry => not observed;
- error log => observed;
- failed trace => observed;
- metric degradation => observed;
- healthy telemetry => not observed;
- evidence IDs retained.

### Severity
- threshold boundaries;
- critical component;
- propagation depth;
- no observed impact;
- configuration changes.

### Privacy
- redaction/masking helper tests;
- diagnosis context excludes disallowed attributes/secrets.

## Integration tests
- fixture provider -> correlation -> graph -> response;
- partial telemetry availability;
- provider exception produces warning/error contract;
- AI adapter unavailable does not break core result.

## Canonical demo fixture
Topology:
```text
payment-service -> customer-service -> document-service -> postgres
```

Incident:
- postgres error begins first;
- document-service has connection/5xx evidence;
- customer-service has timeout evidence;
- choose whether payment-service has evidence to demonstrate the difference between theoretical and observed impact.

Expected:
- postgres origin;
- document-service direct theoretical;
- customer/payment indirect theoretical;
- only components with telemetry evidence observed.

## Acceptance criteria for PoC
1. Runs locally without Datadog credentials.
2. Uses synthetic fixtures only.
3. Produces stable JSON response.
4. Handles graph cycles safely.
5. Separates theoretical and observed impact.
6. Provides evidence for observed impact.
7. Core works when AI is disabled.
8. Tests demonstrate the canonical scenario.
9. No secrets or PII are committed.
10. Integration boundaries are clear enough to replace mock providers without rewriting domain logic.

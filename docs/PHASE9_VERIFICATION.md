# Phase 9 — AI Diagnosis

Status: **implemented and manually verified on the feature branch on 3 October 2026**.

Phase 9 adds an advisory AI diagnosis layer on top of persisted deterministic incident analysis. AI does not calculate origin, blast radius, severity, telemetry coverage or containment.

## Architecture and safety boundary

The diagnosis path is:

```text
Persisted deterministic IncidentAnalysis
    -> StoredAnalysisDiagnosisContextMapper
    -> sanitized DiagnosisContext
    -> AiDiagnosisPort
    -> GeminiDiagnosisAdapter when enabled
    -> AiDiagnosis
```

`DiagnosisContextFactory` provides the equivalent mapping for live domain analysis. The provider receives compact incident facts rather than raw telemetry. Free-text evidence is sanitized before provider use.

The deterministic engine remains the source of truth. The AI may explain supplied evidence and recommend actions, but it must not add/remove impacted components or contradict deterministic incident facts.

Production recovery remains human-controlled. The Gemini prompt explicitly prevents direct instructions to restart, fail over, roll back or change production infrastructure; where such recovery may be required, the diagnosis must tell the operator to confirm the condition and follow the approved recovery procedure.

## Provider and fallback behavior

The optional initial provider is Gemini and is configured only through environment variables:

```text
GEMINI_ENABLED
GEMINI_API_KEY
GEMINI_MODEL
GEMINI_BASE_URL
GEMINI_TIMEOUT_SECONDS
```

No API key is committed or persisted in incident history.

The default model configuration is `gemini-3.8-flash`. A transient provider HTTP 503 receives one bounded retry. Any remaining provider failure is isolated by `AiDiagnosisService`, which returns the deterministic fallback rather than breaking deterministic incident analysis.

## API

Persisted incidents expose:

```text
POST /api/v1/blast-radius/incidents/{id}/diagnosis
```

Runtime OpenAPI verification confirmed the POST operation and documents that persisted sanitized deterministic analysis is the source of truth, AI does not calculate blast radius, and provider failure returns a deterministic fallback.

## Automated verification

Final Phase 9 Blast Radius API regression:

```text
Tests run: 186
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

Dedicated Gemini adapter regression coverage proves:
- HTTP 200 structured response mapping;
- HTTP 503 followed by success performs exactly one retry;
- two HTTP 503 responses stop after the second attempt;
- empty provider content fails safely;
- a missing API key fails before a provider request.

Additional tests cover `DiagnosisContextFactory`, persisted-analysis mapping, provider fallback, and sanitization. During Phase 9 testing a Bearer credential redaction defect was found and fixed so the complete credential is redacted before AI use.

## Manual verification — controlled PostgreSQL outage

The verified persisted incident was:

```text
incident: f9f5afe5-f15e-4780-bd06-10c963395ce1
application: document-platform
environment: local
origin: postgres
origin confidence: HIGH
severity: HIGH / 50
containment: HELD
coverage: logs, metrics, traces and health AVAILABLE
```

Observed deterministic impact:

```text
postgres          ORIGIN    distance 0
document-service  OBSERVED  distance 1
customer-service  OBSERVED  distance 2
payment-service   OBSERVED  distance 3
```

Expected impact matched observed expected impact and no unexpected impact was recorded.

## Real Gemini verification

A real Gemini request succeeded using `gemini-3.8-flash`. The diagnosis kept PostgreSQL as the deterministic origin, explained the database connection evidence observed in `document-service`, and described the resulting 5xx effects in `customer-service` and `payment-service`.

Prompt hardening was then applied so output uses simpler English, distinguishes the incident origin from services where effects are observed, addresses the origin before resilience improvements, and does not issue direct production mutation instructions.

A separate runtime request encountered Gemini HTTP 503. The API still returned HTTP 200 with the deterministic fallback, proving provider failure isolation. The adapter subsequently gained the single bounded 503 retry described above, with dedicated automated regression coverage.

## Recovery and incident lifecycle verification

After PostgreSQL was restored:
- target PostgreSQL became healthy;
- document, customer and payment services were healthy;
- the diagnostic database and Blast Radius API remained healthy;
- the traffic generator produced repeated HTTP 201 synthetic transactions.

The incident was then explicitly resolved. The same UUID was retained with:

```text
status: RESOLVED
origin: postgres
origin confidence: HIGH
severity: HIGH / 50
```

The original failure analysis snapshot was retained. The ACTIVE incident query returned an empty list, while the resolved incident remained available in history. Healthy recovery therefore did not overwrite the recorded failure state or implicitly resolve the incident.

## Phase 9 acceptance

Phase 9 acceptance is complete:
- deterministic analysis remains authoritative;
- provider input is bounded and sanitized;
- Gemini integration works against the real provider;
- AI language is evidence-bounded and operationally safer;
- provider failure cannot break deterministic analysis;
- transient 503 retry behavior has automated coverage;
- persisted incidents can be diagnosed through the documented API;
- recovery and explicit lifecycle resolution preserve the original incident;
- final regression is 186/186 passing.

# Phase 6 — Evidence Correlation and Observed Impact

Status: **stopped-database retained-topology acceptance PASS**, 2026-10-03. Recovery testing remains explicitly deferred pending approval.

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

The endpoint does not execute remediation, mutate monitored services or invoke AI. Analysis may refresh the local retained-topology knowledge store.

## Interpretation

- `ORIGIN`: suspected or explicitly supplied origin.
- `OBSERVED`: component is in the theoretical radius and has failure evidence.
- `THEORETICAL_ONLY`: component is in the theoretical radius but no failure evidence was found while telemetry coverage is complete.
- `UNKNOWN`: component is theoretical but telemetry coverage is incomplete and no failure evidence exists.
- `UNEXPECTED`: failure evidence exists for a component outside the theoretical radius.

## Investigation findings and implemented correction

The 2026-10-02 investigation proved two failure boundaries:

1. Healthy SQL spans identified `server.address=postgres`, while a technology-only INTERNAL connection-pool span became a false `postgresql` node. This made peer-less inference ambiguous.
2. Tempo's one-hour retained history expired during investigation. Incident analysis had no durable topology when identifying traces disappeared. A long trace also leaked old spans into the current evidence window.

The adapter now separates identity and technology, retains span role, and filters spans by start time in `[from,to)`. `IncidentAnalysisService` uses the provider-neutral `DependencyTopologyProvider`, never the Tempo discovery adapter directly. Automatic runtime discovery merges into an atomic, scoped, timestamped local topology store with a configurable seven-day TTL. Incident evidence is independently filtered; retained knowledge does not become failure evidence. Server errors alone and ambiguous multi-dependency cases do not infer a dependency origin.

See [ADR-024](DECISIONS.md#adr-024--retain-runtime-topology-separately-from-incident-evidence) and [topology retention](TOPOLOGY_RETENTION.md) for configuration, provenance, expiry and local limitations.

## Tests actually executed

- Identity-only `TempoTraceAdapterTest`: **12 passed**, no failures/errors/skips.
- Initial combined focused run: **38 passed**, no failures/errors/skips.
- Final focused run: **67 passed**, no failures/errors/skips:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -f blast-radius-api/pom.xml -B -ntp \
  -Dtest=TempoTraceAdapterTest,CapturedTempoRegressionTest,TraceDiscoveredTopologyProviderTest,RetainedTopologyProviderTest,IncidentAnalysisServiceTest,BlastRadiusApplicationTests,LocalTelemetryProviderTest,DeterministicGraphEngineTest,SpanEvidenceTest test
```

Coverage includes captured healthy/failed OTLP payloads, concrete database identity, technology-only attributes, loopback exclusion, retention after raw trace loss and provider restart, new discoveries, duplicates/cycles, scope isolation, stale expiry, monotonic observation timestamps, corrupt-store failure, cross-trace span-ID isolation, incident interval boundaries, historical evidence exclusion, server-only errors, ambiguous real dependencies and partial coverage.

The initial sandboxed identity-test attempt could not initialize Mockito self-attachment; the permitted rerun passed. No application fix was inferred from that environment error.

## API-only Docker validation

```bash
docker --context desktop-linux compose config --quiet
docker --context desktop-linux compose build blast-radius-api
docker --context desktop-linux compose create --no-build blast-radius-api
# Install approved offline snapshot while the new API is stopped.
docker --context desktop-linux cp /tmp/phase6-topology-bootstrap/. blast-radius-api:/app/data/topology/
docker --context desktop-linux compose up -d --no-deps --no-build blast-radius-api
docker --context desktop-linux exec --user root blast-radius-api chown -R 10001:10001 /app/data/topology
```

Compose configuration passed. The existing Dockerfile's Maven `clean verify` ran the full API regression **once: 156 tests, 0 failures, 0 errors, 0 skipped**. The image build passed. API health returned UP. All nine non-API container IDs were unchanged.

PostgreSQL remained `exited`, with its unchanged stop timestamp **2026-10-02T14:24:57.34405618Z**. No volume was deleted, no other image rebuilt, and PostgreSQL was never started.

## Approved bootstrap and live learning

The healthy trace had expired, so this specific acceptance used the approved offline helper and real captured trace `00213806d536843849e3cc53fdcd1534`. The helper is outside application source/image and is not a startup mechanism.

The database edge retained:

- firstSeen: `2026-10-02T14:20:09.502934296Z`;
- lastSeen: `2026-10-02T14:20:09.504211379Z`;
- source: `captured-tempo-bootstrap`;
- sourceRef: `trace/00213806d536843849e3cc53fdcd1534`.

After analysis, live automatic learning had refreshed the HTTP edges to `2026-10-03T03:27:17Z` with `local-tempo` provenance. The peer-less outage did **not** renew the database edge. This separates real runtime refresh from bootstrap knowledge and preserves honest freshness.

## Actual stopped-database result

A synthetic payment request with correlation ID `phase6-retained-outage-20261003` returned **HTTP 502**. Analysis used no `originHint`:

```text
applicationId = document-platform
environment   = local
from          = 2026-10-03T03:23:53.352482Z
to            = 2026-10-03T03:28:53.352482Z
```

Actual topology:

```text
NODES
customer-service  SERVICE
document-service  SERVICE
payment-service   SERVICE
postgres          DATABASE technology=POSTGRESQL

EDGES
payment-service  -> customer-service
customer-service -> document-service
document-service -> postgres
```

There is no `postgresql` node and no `localhost` node.

| Component | State | Distance | Path |
|---|---|---:|---|
| postgres | ORIGIN | 0 | postgres |
| document-service | OBSERVED | 1 | postgres → document-service |
| customer-service | OBSERVED | 2 | postgres → document-service → customer-service |
| payment-service | OBSERVED | 3 | postgres → document-service → customer-service → payment-service |

Coverage: **logs AVAILABLE, metrics AVAILABLE, traces AVAILABLE, health AVAILABLE; fullyCovered=true**.

Actual warning:

> Live health snapshots outside the incident window were excluded from correlation; coverage describes provider availability.

The timeline contains **31 signals: 24 TRACE and 7 METRIC**. Its earliest timestamp is `2026-10-03T03:24:30.747928961Z`; latest is `2026-10-03T03:28:53Z`. A nanosecond-accurate check found **zero signals outside `[from,to)`**. Captured healthy knowledge from October 2 did not enter the current origin score or timeline. Logs were available but did not supply ERROR/FATAL signals to this result; live health snapshots were excluded by the interval guard.

The initial acceptance checker used a Python ISO parser that rejected nine-digit fractions. The checker was corrected to compare integer nanoseconds against the saved response; no live experiment or application change was needed.

Machine-readable observed results: [outage acceptance](../fixtures/experiments/phase6-retained-topology-outage.json).

## Limitations and stopping point

- Origin is an inference from a current failed dependency operation and its unique retained relationship, not a fresh successful database measurement.
- Local topology is single-writer and scoped to one configured application/environment per backend. Runtime discovery is bounded/sampled; available providers do not prove complete topology.
- TTL eventually removes unrefreshed knowledge. A future architecture provider can supply durable managed relationships with a different lifecycle policy.
- Health is currently live snapshot evidence; historical health coverage is not supplied by Actuator.
- The stored database relationship was bootstrapped for this acceptance. Healthy automatic database rediscovery/recovery was not tested live because PostgreSQL must remain stopped; automatic discovery/persistence/restart behavior is regression-tested.
- No recovery, Phase 7, AI, remediation, PR, push or merge was performed. Stop here pending approval.

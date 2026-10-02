# Phase 5 Verification — Deterministic Graph Engine and Automatic Topology Discovery

**Acceptance: PASSED on 2026-10-02.**

Phase 5 establishes the deterministic, technology-neutral graph layer used to calculate theoretical blast radius. It also proves that the topology does not need to be hand-authored for every monitored application: a local adapter can derive dependency edges automatically from normalized distributed-trace evidence and feed the same provider-neutral graph engine.

Branch: `feat/deterministic-graph-engine`.

## Scope verified

Phase 5 adds:

- technology-neutral component nodes and dependency edges;
- component type separated from technology metadata;
- topology validation and duplicate-edge normalization;
- `DependencyTopologyProvider` as the acquisition boundary;
- deterministic reverse dependency traversal;
- minimum hop distance and deterministic shortest dependency path;
- DIRECT versus INDIRECT theoretical-impact classification;
- fan-out, fan-in, cycles, disconnected components and duplicate-edge handling;
- automatic local topology discovery from normalized trace spans;
- a live topology-discovery acceptance test against the running Docker/Tempo evidence.

Phase 5 does **not** infer incident origin, correlate runtime evidence into observed impact, calculate incident severity, perform AI diagnosis/remediation, persist incidents, expose UI, or execute autonomous fixes. Those remain later phases.

## Final verification results

The final clean Blast Radius API regression compiled 44 production source files and 19 test source files, then completed:

- **125 tests**
- **0 failures**
- **0 errors**
- **0 skipped**
- **BUILD SUCCESS**

The Phase 5 tests included in that normal regression are:

- `DeterministicGraphEngineTest`: 11 passed;
- `TopologyDomainTest`: 3 passed;
- `TraceDiscoveredTopologyProviderTest`: 2 passed.

The opt-in `TraceDiscoveredTopologyProviderLiveIT` was executed separately against the running local Docker observability stack and passed 1/1.

## Deterministic graph acceptance

The graph engine was verified for:

- canonical dependency chains;
- reverse traversal from a failure origin;
- direct and indirect impact;
- minimum hop distance;
- deterministic dependency paths;
- deterministic shortest-path tie breaking;
- fan-out;
- fan-in;
- cycle termination;
- disconnected components;
- duplicate dependency edges;
- origin nodes with no dependents;
- invalid/unknown origins and edge endpoints;
- duplicate component IDs;
- topology/domain immutability;
- component type and technology independence.

For an edge `A -> B`, A depends on B. Failure propagation therefore traverses in reverse from B to its dependents.

For the canonical local chain:

```text
payment-service -> customer-service -> document-service -> postgres
```

a `postgres` origin yields the theoretical path:

```text
postgres
  -> document-service   hop 1 / DIRECT
  -> customer-service   hop 2 / INDIRECT
  -> payment-service    hop 3 / INDIRECT
```

This is theoretical impact only; it does not claim those components were actually degraded.

## Automatic topology acquisition proof

Topology acquisition is deliberately separate from graph calculation. The graph engine consumes `DependencyTopologyProvider`; it does not hardcode service names or know how dependencies were discovered.

`TraceDiscoveredTopologyProvider` is the local proof adapter. It consumes normalized `SpanEvidence` through the existing `TelemetryProvider`, derives cross-service dependencies from parent/child spans and peer-service evidence, identifies database peers from normalized database attributes, normalizes duplicate observations, and returns a `DependencyTopology`.

The deterministic fixture test proved discovery of:

```text
payment-service -> customer-service
customer-service -> document-service
document-service -> postgres
```

without supplying those dependency edges to the graph engine manually.

The separate live integration test then repeated that proof using real Tempo evidence from the running synthetic application. It required the discovered topology to contain all three canonical dependencies and required the graph engine, using that discovered topology, to return the PostgreSQL theoretical blast path through document, customer and payment services.

This proves the local flow:

```text
real application traffic
  -> OpenTelemetry / Tempo
  -> normalized SpanEvidence
  -> TraceDiscoveredTopologyProvider
  -> DependencyTopology
  -> DeterministicGraphEngine
  -> theoretical blast radius
```

## Production integration boundary

The trace-discovery adapter is a proof of automatic acquisition, not a declaration that Tempo must be the only production topology source.

The provider contract allows MadlangaAI's architecture/dependency analysis to become the enterprise topology source when integrated. Runtime traces, service catalogs and approved platform/cloud metadata can enrich or provide alternate topology adapters without changing the deterministic graph engine.

This prevents manual per-system graph maintenance while keeping graph semantics independent of vendors and discovery mechanisms.

## Exit assessment

Phase 5 exit criteria are satisfied:

1. a validated technology-neutral dependency graph exists;
2. theoretical blast radius is deterministic and cycle-safe;
3. direct/indirect classification, paths and hop distance are available;
4. topology acquisition is isolated behind a provider contract;
5. automatic topology discovery has been proven against both deterministic fixtures and real local trace evidence;
6. the final regular API regression is clean.

Phase 6 can now correlate the theoretical graph with Phase 4 logs, metrics, traces and health evidence to determine **observed** impact rather than merely potential impact.

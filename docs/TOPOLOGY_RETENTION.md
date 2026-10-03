# Retained runtime topology (Phase 6)

Topology is application knowledge, not incident evidence. The deterministic graph engine remains independent of Tempo, filesystem persistence and incident telemetry.

## Acquisition and persistence

`IncidentAnalysisService -> DependencyTopologyProvider -> RetainedTopologyProvider`

The local provider consumes normalized spans through `RuntimeSpanSource`, discovers relationships, and merges them through the replaceable `TopologyStore` port. `FileTopologyStore` writes versioned JSON snapshots with atomic replacement and a forced file write. Compose retains them in the `topology-data` named volume, mounted at `/app/data/topology`. No raw logs, payloads or failure signals are stored there.

Snapshots are scoped by application ID and environment, with hashed filenames. Each component retains identity, type, technology, metadata, firstSeen, lastSeen and the latest observation's provider/source reference. Edges retain their own firstSeen/lastSeen and source. Cross-service parent relationships are matched by **trace ID plus span ID**. Duplicate edges merge; cycles remain valid graph inputs.

Normal operation polls runtime traces automatically, even if nobody requests incident analysis. Analysis/topology requests also refresh discovery. There are no runtime fixture imports and no manually configured service chain. Explicit failed client calls can still demonstrate a relationship; peer-less failures never refresh an unidentified edge. Metadata-only DB spans retain technology in normalized evidence but never invent a technology-named component.

## Configuration

| Environment variable | Default | Meaning |
|---|---|---|
| `TOPOLOGY_TTL` | `7d` | Observation lifetime, substantially longer than Tempo's one-hour retention |
| `TOPOLOGY_DISCOVERY_WINDOW` | `5m` | Runtime trace discovery window |
| `TOPOLOGY_REFRESH_INTERVAL` | `30s` | Fixed delay between background discovery runs |
| `TOPOLOGY_REFRESH_ENABLED` | `true` | Enable automatic refresh; tests disable it |
| `TOPOLOGY_DIRECTORY` | `./data/topology` | Snapshot directory; under `/app` in the container |
| `TOPOLOGY_APPLICATION_ID` | `document-platform` | Application represented by this local backend |
| `TOPOLOGY_ENVIRONMENT` | `local` | Environment represented by this local backend |

TTL uses **actual span timestamps**, not collection/read time. Re-reading old traces cannot renew a relationship. Expired edges/nodes are removed on refresh; service health or peer-less errors cannot indefinitely renew a database edge. An unavailable backend does not erase unexpired knowledge. A corrupt/unwritable store is an explicit failure, not a silently empty graph.

The local collector/backend represents one configured application/environment. Requests for another scope are rejected rather than mislabelling this lab's telemetry. Future shared backends need resource-based application scoping. The local file adapter is single-writer; replicas require another `TopologyStore` implementation with appropriate concurrency guarantees.

## Incident integrity

The Tempo adapter filters every returned trace to spans whose **start time** is in `[from, to)`, including direct trace-ID queries. Incident analysis defensively filters all evidence families to the same interval. A span beginning before `from` is excluded even if its duration overlaps the interval. Historical discovery never changes incident coverage, origin scores or the timeline.

Actuator is a live snapshot provider, not a historical health archive. Samples outside the requested interval are excluded from correlation with a warning. `health=AVAILABLE` still describes current provider availability, not historical health coverage. Do not interpret fully available providers as proof of complete topology.

A peer-less INTERNAL/CLIENT/PRODUCER error can support an **inferred** failed dependency only where the retained topology has exactly one dependency. A server error alone does not accuse a dependency; multiple real dependencies remain ambiguous. Inference is not a direct database measurement and should be read with its supporting evidence.

Inspect the effective topology:

```bash
curl -fsS 'http://localhost:8080/api/v1/blast-radius/topology?applicationId=document-platform&environment=local'
```

This endpoint exposes nodes, metadata and edges; it may refresh the local knowledge store but never changes monitored applications.

## Offline acceptance bootstrap only

The approved stopped-database experiment starts after healthy traces have expired. `scripts/BootstrapCapturedTopology.java` can replay a captured real OTLP response through the production normalization/discovery path into an **offline** snapshot directory. It is not included in the application image and is never called by application startup.

```bash
mvn -f blast-radius-api/pom.xml -B -ntp dependency:build-classpath -Dmdep.outputFile=/tmp/blast-radius-api.classpath
java -cp "blast-radius-api/target/classes:$(cat /tmp/blast-radius-api.classpath)" \
  scripts/BootstrapCapturedTopology.java \
  blast-radius-api/src/test/resources/tempo/healthy-database.json \
  /tmp/phase6-topology-bootstrap document-platform local
```

Use Java 21. `BOOTSTRAP_TOPOLOGY_TTL` accepts an ISO-8601 duration (default `P7D`) and must match the target lab policy. Original span timestamps and trace identity are preserved; already expired fixtures do not create authoritative dependencies. The source is explicitly marked `captured-tempo-bootstrap`. Never copy snapshots into an actively writing API store: stop only the API or install into a newly created, stopped API container, then start only that API. Preserve any existing snapshot before merging bootstrap observations. Never start PostgreSQL or remove its volume as part of this procedure.

Normal runtime learning replaces this one-off bootstrap when healthy traffic is available. Sampled/bounded trace discovery can miss relationships; retention preserves learned knowledge but cannot discover an identity never observed. It is replaceable by MadlangaAI architecture knowledge later.

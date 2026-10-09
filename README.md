# MadlangaAI Blast Radius Engine

The Blast Radius Engine helps an operator answer three questions: **Where did a failure likely start? Which services are affected? What evidence supports that conclusion?** It combines dependency topology with logs, metrics, traces and health checks, saves incidents, and tracks recovery. Optional AI explains the result; deterministic backend rules make the decisions.

Read the documentation in this order:

1. [README.md](README.md) — the system and its integration boundaries.
2. [SETUP.md](SETUP.md) — installation, operation and validation.

## What runs locally

Compose starts the API, React dashboard and PostgreSQL incident database. Set `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` and a stable `POSTGRES_VOLUME_NAME` in `.env`; credentials have no fallback defaults. Configure a monitored application separately through a deployment profile. Without application probes or telemetry, the engine has no application outage evidence.

Logs, metrics, traces and direct availability probes feed sanitized telemetry and retained topology. Deterministic analysis identifies origins and impact, then persists incidents for the REST API and dashboard.

## How other systems connect

A monitored application needs a stable application ID, environment and component IDs. Its topology and telemetry must use the same identities, or evidence cannot be reliably attached to a service.

- **Topology:** implement `DependencyTopologyProvider` to supply components and directed dependencies. The local implementation learns relationships from Tempo spans, retains them in PostgreSQL and expires stale observations.
- **Telemetry:** implement `TelemetryProvider` to return normalized, sanitized logs, metrics, traces, health and explicit coverage for a requested time window. The local provider combines Loki, Prometheus, Tempo and Actuator adapters.
- **Consumer:** use the incident REST endpoints to list incidents, open evidence and request an advisory diagnosis. The included React dashboard is one such consumer.

A future MadlangaAI architecture source or Datadog integration can implement these boundaries. Each deployment monitors one application/environment; application registration, multi-application scheduling, enterprise authentication, team ownership and notification delivery still require integration work. The dashboard can copy a handoff summary, but does not send messages or page a team.

## Topology and impact

A dependency edge `service-a → service-b` means service A needs service B. Failure propagation follows the reverse direction toward callers. The graph records paths and dependency distances; unrelated components are not linked merely because they appear next to each other.

**Potential impact** means a dependency path exists. **Observed impact** means current failure evidence was found for that component. **Unknown** means the available telemetry cannot establish the answer. Retained historical topology is useful knowledge, not proof of a current failure.

## Detection and recovery

`IncidentAnalysisService` correlates evidence and assesses a likely origin with confidence. `BlastRadiusGraphService` calculates potential impact; `IncidentSeverityService` calculates severity using the incident evidence. The scheduler calls `IncidentLifecycleService` to create or update one ACTIVE incident per application/environment/origin.

Recovery requires three distinct, fresh direct origin UP probes by default. Partial logs/metrics/traces alone cannot prove or prevent recovery; missing direct origin availability evidence cannot prove recovery. Newer direct DOWN evidence resets the recovery streak, and compare-and-set incident revision checks guard resolution. Incident and topology persistence use PostgreSQL with Flyway migrations. Topology snapshots retain observation timestamps and provenance, scoped by application/environment.

## The visual dashboard

The dashboard polls persisted incidents every five seconds, preserves the selected incident, and caches diagnoses by incident for the browser session. Search incidents by component, symptom or ID; search the map to highlight matching services; search evidence to narrow the explanation.

The service map draws recorded dependency paths and provides hover, keyboard-focus and click explanations:

| Color | Meaning |
|---|---|
| Red | A health signal reports the service unavailable. |
| Amber | Failure or degradation signals exist; this does not prove the process stopped. |
| Gently pulsing green | Potential impact is unconfirmed; this is **not** proof of health. |
| Gray | Insufficient evidence or unknown impact. |
| Solid green in resolved history | The incident is resolved; the saved snapshot does not establish live service health. |

An impact chart summarizes service classifications. A timeline groups recorded evidence by time and family; its counts are observations, not request volume or separate incidents. Plain-language explanations retain expandable technical observations for engineers. Reduced-motion preferences disable the pulse.

The backend does not always know whether a service was deliberately stopped, exhausted a resource or became unreachable. The UI reports what the evidence establishes and keeps uncertainty visible instead of inventing a cause.

## Optional diagnosis

`DiagnosisService` calls its configured provider with sanitized deterministic evidence. Gemini can try configured fallback models; the deterministic provider remains the application fallback. AI does not select the origin, compute blast radius, decide severity or resolve incidents.

Configure AI through `.env`; never commit real API keys. Without Gemini the deterministic engine still works.

## Start and validate

```bash
cp -n .env.example .env
# Set the required database, volume and monitoring values in .env.
docker compose config --quiet
docker compose up -d --build
```

Open the [dashboard](http://localhost:5173). Follow [SETUP.md](SETUP.md) for readiness checks and the full validation sequence.

Run the engine regression checks described in [SETUP.md](SETUP.md). Runtime acceptance must use the configured monitored application.

## Repository ownership

`incident/`, `topology/`, `telemetry/`, `diagnosis/`, `lifecycle/` and `shared/` own the backend capabilities. The current checkout uses feature-first Spring folders such as `controller`, `service`, `model`, `provider` and `repository`. Frontend presentation does not change backend decisions.

Telemetry is sanitized before storage, display and AI use. Keep coverage separate from health, provenance separate from raw provider payloads, and incident severity separate from any wider MadlangaAI health score. The engine does not autonomously repair production systems.

## Outage-only incident lifecycle (feature branch)

The detector creates an **OUTAGE / UNAVAILABLE** incident only from direct, typed availability evidence (configured Spring liveness or JDBC availability probes). HTTP 500, latency, readiness failures and CPU/resource pressure remain supporting telemetry and are not outage types. A database probe returning UNKNOWN (for example invalid credentials) does **not** prove a database outage.

Every active incident retains a stable ID, initial detection time and deduplicated timestamped evidence from successive telemetry evaluations. Origin availability is monitored independently of historical symptoms. Recovery requires fresh direct origin UP availability evidence across three distinct consecutive successful probe evaluations by default. Partial LOG/METRIC/TRACE coverage is recorded but does not reset otherwise valid direct recovery evidence; missing direct UP or newer direct DOWN evidence blocks recovery. Resolution uses a persisted revision compare-and-set guard. AI diagnosis is explicitly requested and archived with the exact incident evidence snapshot/version it used; polling does not regenerate it.

The default per-incident collection limits are **20,000 observations** and **7 days**. Once a limit is reached, previous observations remain intact, the limitation is recorded, and the latest direct recovery proof is saved separately. These limits do **not** implement total database retention or provide missed-telemetry backfill during an observability outage. Historical incidents and diagnosis records are preserved until an explicit archival/retention policy is approved; no background deletion is enabled.

Monitoring identities, topology and probes are application-profile configuration, not hard-coded engine logic. Current scope is one monitored application/environment and one PostgreSQL/JDBC target per engine deployment; arbitrary automatic enterprise discovery and a multi-application registry are not implemented.

Run backend verification with `mvn -f blast-radius-api/pom.xml clean verify`, frontend tests with `node --test frontend/src/incident-visuals.test.js`, and the frontend build with `npm --prefix frontend run build`.

### Verified isolated CI results

GitHub Actions run [37756041645](https://github.com/SLMakomazi/blast-radius-engine/actions/runs/37756041645) passed on implementation commit `fae38e0` on 2026-10-08: **223 backend tests (0 failed, 0 skipped), 14 frontend regression tests (0 failed), and the frontend production build**. The backend suite includes two PostgreSQL-backed tests against a disposable CI database: Flyway V1–V3, JSONB incident/diagnosis persistence, single-active-origin uniqueness and cross-connection transactional advisory locking. No developer data or volumes were touched. This does **not** replace controlled live-service outage/recovery testing against the configured monitored application.

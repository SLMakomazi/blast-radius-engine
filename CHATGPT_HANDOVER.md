# ChatGPT handover — outage evidence lifecycle

Updated 2026-10-08. **Work is in progress, not ready to declare complete.** The user requested this handover when usage runs low; the Codex usage tool reports the five-hour allowance at 100%. No reset credit was consumed.

## Repository and safety constraints

- Workspace: `/Users/sisekomakomazi/Projects/blast-radius-engine`.
- Current feature branch: `feat/outage-evidence-lifecycle`, created from the clean current working HEAD as requested.
- Starting branch: `fix/failure-degradation-separation`.
- Base commit before the enhancement: `67e9268` (`test: preserve independent database health evidence`). Enhancement changes and this handover are being committed as an **in-progress checkpoint** on the feature branch at the user's explicit request. Use `git log -1` for the latest checkpoint SHA; preserve all work.
- Do not reset/discard changes, modify main, merge, delete Docker volumes, reset databases, or fabricate production evidence. The user explicitly authorized pushing this feature-branch checkpoint on 2026-10-08; no merge is authorized.
- Preserve working PostgreSQL availability detection, Spring liveness checks, guarded recovery, frontend design, existing integrations and incident history.
- Preserve capability-first packages: incident, topology, telemetry, diagnosis, lifecycle, shared.
- Do not spawn subagents unless explicitly authorized. This task has used no subagents.
- Markdown previously consolidated to README.md, SETUP.md, ARCHITECTURE.md, PRESENTATION.md. This fifth handover file is explicitly requested by the latest user.

## User's requested outcome

Implement structured OUTAGE / UNAVAILABLE classification backed only by direct availability evidence; keep HTTP 500, latency, readiness DOWN and resource pressure as symptoms. Accumulate and deduplicate evidence for the full ACTIVE lifecycle, maintain UUID/start time and historical evidence, resolve automatically with positive guarded recovery, prevent stale reopening and concurrent evidence loss, and bound collection explicitly. Keep confirmed availability, observed impact, potential impact and physical root cause distinct.

Make application identities, dependencies, probe settings and telemetry configurable while retaining the demo default. Preserve frontend incident selection, map interaction, filters and diagnosis across silent background updates. AI should use accumulated evidence and persist the exact version/snapshot used for each explicitly requested diagnosis.

Final deliverables still required: branch and final commit SHA, backend/frontend/persistence/tests summary, classification/dedup/configuration explanations, honest tests and limitations, exact Docker rebuild commands, stepwise PostgreSQL outage/recovery validation. No merge. The checkpoint push is explicitly authorized, but it does not mean the enhancement is complete. Review and run all available relevant tests before declaring completion.

## Implementation currently in working tree

### Backend classification and evidence

- `incident/model/EvidenceSignal.java` now adds structured Kind (`SYMPTOM`, `POTENTIAL`, `AVAILABILITY_UNAVAILABLE`, `AVAILABILITY_AVAILABLE`), provider, sourceRef and collectedAt. Legacy five-argument constructor remains.
- `IncidentAnalysisService` prioritizes directly unavailable origins; only database availability/liveness observations produce availability failure kinds. Readiness and generic symptoms do not. Dependency-attributed span errors are marked POTENTIAL, not direct availability.
- Successful availability probes are included in the timeline for guarded recovery. They are not failure signals.
- Correlation carries provenance into normalized evidence. Log explanations use sanitized actual messages instead of demo scenario classifications.
- `telemetry/model/EvidenceIdentity.java` gives logs/metrics/spans deterministic opaque IDs. The adapters previously generated new random UUIDs on every overlapping fetch. Metric identity includes dimensions; trace identity includes trace/span IDs. Health probes remain distinct actual observations.
- Health adapters now use real occurrence timestamps and separate collection timestamps. Correlation includes current live probes collected just beyond a live query's upper bound but excludes them from historical queries.
- HTTP 500/503 alone no longer establishes a DOWN health result without a recognized health body. Transport errors are separated from malformed response errors.
- Database authentication/configuration errors return UNKNOWN; SQL connection-class errors return DOWN. Existing PostgreSQL direct probe remains.

### Lifecycle / persistence

- `lifecycle/service/IncidentEvidenceAccumulator.java` merges persisted JSON with new evidence rather than replacing it. Prior timeline and impact evidence are preserved; observed impact and peak severity persist.
- Dedup uses stable provider identity where available and deterministic occurrence/component/family/provider/source/signal fallback for legacy/random IDs. Derived metric delta changes for the same stable sample do not multiply observations.
- Snapshot has incidentType, availabilityStatus, availabilityExplanation, rootCause=UNDETERMINED, evidenceVersion, collectionStartedAt, lastCollectedAt, collectionLimited.
- Limits: `blast-radius.lifecycle.max-evidence-per-incident` (20,000 default) and `collection-duration` (7 days). Reaching either stops new evidence admission and adds an explicit persistent warning; previously stored history is not evicted. Availability monitoring and recovery continue.
- Missing collection windows add a gap warning. There is **not** catch-up collection after a long scheduler outage; review this limitation against the user's requirement.
- `IncidentLifecycleService` is transactional, uses a scope lock before creation/update/resolution, preserves ID/start time and peak severity, checks latest resolved timestamp before creating a new incident, and appends evidence even during recovery evaluation.
- `JdbcIncidentRepository` uses PostgreSQL transaction advisory locks keyed by application/environment/origin. Port methods are now required, not silent default no-ops. Existing V2 unique ACTIVE-origin index remains.
- `resolveIfUnchanged` checks the exact persisted updatedAt revision under the same lock before resolving, preventing a stale recovery decision from resolving a newly updated incident.
- Scheduler retains consecutive full-coverage/no-origin-failure guards and additionally requires explicit successful origin availability evidence. Restarts reset healthy-window counts conservatively.
- Old snapshots without retained direct confirmation are not automatically labelled OUTAGE merely because they exist.

### AI

- New `diagnosis/service/DiagnosisArchive.java` and Flyway `V3__diagnosis_evidence_snapshots.sql` append explicitly requested diagnosis results with exact snapshot and evidence version.
- POST `/api/v1/blast-radius/incidents/{id}/diagnosis` returns diagnosisId, evidenceVersion and evidenceCapturedAt along with the existing fields.
- GET `/{id}/diagnoses` retrieves saved diagnosis results. Exact source snapshot is retained in the database row.
- DiagnosisRequest evidence includes category and provenance. Gemini instructions distinguish facts, hypotheses, direct availability, physical causes, historical evidence and potential/observed impact.
- No diagnosis is automatically regenerated by polling.

### Configuration / portability

- Demo-specific names/endpoints/JDBC settings moved out of Java defaults and into `src/main/resources/application-demo.yml`.
- `application.yml` selects `demo` as the default Spring profile. Explicit other profiles avoid merging demo component maps.
- `topology/config/ConfiguredTopology.java` binds explicit components/dependencies and validates edge references using existing domain types.
- Primary topology provider chooses explicitly configured topology or the existing trace-derived retained topology. Scope is restricted to the configured application/environment.
- Local telemetry scope is similarly guarded.
- `infrastructure/monitoring/application-ledger.yml` is an example second deployment profile with its own names, topology, HTTP liveness, JDBC probe and telemetry URLs.
- One application per engine deployment, multiple HTTP services, currently one JDBC target; this is not automatic enterprise discovery or multi-application orchestration. The installed JDBC driver is PostgreSQL.
- Configurable liveness path and health timeout are wired. Review other telemetry adapters' existing timeout wiring; not all declared timeout properties have been audited/fixed.

### Frontend

- `incidentClassification()` uses backend structured fields, never symptom keywords. Legacy incidents show UNCLASSIFIED / UNKNOWN rather than invented outage labels.
- Explicit clicks determine selection; it no longer automatically selects the first/newest incident.
- Background list polling fetches the same selected incident detail. `acceptIncidentDetail()` rejects responses for a now-unselected incident and older detail responses, and preserves object identity for unchanged data.
- Map retains `key={selected.id}`; filters/map state/diagnosis aren't reset by evidence refresh or ACTIVE→RESOLVED.
- Diagnosis displays its evidence version. Collection-limit warning is shown.
- Direct availability explanations take priority over symptom ordering; origin headings no longer call a directly confirmed outage merely suspected. Existing map design and colors retained.

### Regression runners

- `scripts/run-phase11-e2e.py` retains its six scenarios and adds assertions for OUTAGE/UNAVAILABLE, direct origin evidence, stable start time and retained evidence through polling/resolution.
- `scripts/run-degradation-e2e.py` was outdated: it still required degradation-only ACTIVE outage incidents. It now calls deterministic analysis for symptoms, retains required real LOG/METRIC/TRACE family assertions and expected origins, asserts liveness stays UP and no new outage is created, and verifies successful full-chain requests after reset. Review this modification carefully; don't weaken real-telemetry assertions.
- These are the actual remaining E2E runner names; stale documentation still mentions run-stage2-e2e.py in places and needs correction.

## Validation performed

### Backend

- Initial sandbox Maven run failed because Mockito/Byte Buddy could not attach its JVM test agent. A later compile typo was fixed.
- Successful complete command outside sandbox:
  `mvn -f blast-radius-api/pom.xml clean verify`
- Latest completed full run: **212 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS. Log `/tmp/outage-verify-handover.log` (2026-10-08 09:56 SAST).
- This final run includes the additional `ConfiguredTopologyTest` readiness/liveness and binding cases, `IncidentLifecycleServiceTest` recovery revision race, and new `DiagnosisArchiveTest`. All passed.
- Test JDK is local Java 25 targeting Java 21. Docker builds use Java 21.
- SQL migration and advisory-lock behavior have NOT yet been exercised against real PostgreSQL; mock/unit tests do not substitute for that integration check.

### Frontend

- `node --test frontend/src/incident-visuals.test.js`: **12/12 passed**, no skips.
- `npm --prefix frontend run build`: passed.
- Logs: `/tmp/outage-ui-tests-final.log`, `/tmp/outage-ui-build-final.log`.
- Manual browser interaction test used built frontend and a **clearly labelled UI TEST FIXTURE**, not real outage evidence:
  - no initial automatic newest selection;
  - selected older incident;
  - generated diagnosis at evidence version 1;
  - pinned fixture-api, map search fixture-api, Log filter and evidence search 500;
  - advanced fixture to version 2: new evidence and observed impact appeared while pinned selection, searches, filter, input focus and version-1 diagnosis remained;
  - advanced to version 3: selected incident disappeared from ACTIVE list but same selected detail became RESOLVED / RECOVERED; map pin, searches, focus and diagnosis remained.
- Preview server `/tmp/outage-ui-preview.py`, port 5175, exec session 76979. It serves frontend/dist and in-memory labelled fixture API; it does not touch backend/DB. Stop it when done.
- Temporary in-app browser tab 3 at http://127.0.0.1:5175/; handle `outagePreview` in CUA REPL. It is not marked as a deliverable and should be closed/allowed to expire.
- Do not claim automated browser regression coverage beyond actual helper tests and this explicitly described browser check. Scroll/zoom were not separately measured.

### Live environment / E2E

- Initially only API, incident DB and three demo services were running; some unhealthy and telemetry services absent. Subsequently both Docker and Podman became unavailable.
- `docker compose ps`: Docker socket `/Users/sisekomakomazi/.docker/run/docker.sock` missing.
- `podman ps`: connection refused to its VM socket.
- Actual unsandboxed `curl --max-time 5 http://127.0.0.1:8080/actuator/health`: connection refused.
- Attempts to run both E2E suites inside sandbox also hit network permission errors (logs `/tmp/outage-phase11.log`, `/tmp/outage-degradation.log`). Stage 1 never passed API preflight; degradation reported 0/4 due unavailable API. These are **not application test passes**, nor proof of code regressions.
- Runners were subsequently strengthened/updated and Python syntax checked. Their updated runtime behavior is NOT yet validated with real telemetry.
- Do not report 10/10 PASS. Runtime unavailable remains an explicit limitation until services can run again.

## Remaining work, in order

1. Inspect git log/diff/status and this handover; preserve the checkpoint and any subsequent local changes.
2. The final 212-test Maven run passed. After further production changes, rerun `mvn -f blast-radius-api/pom.xml clean verify` with necessary local agent-attachment permissions. Rerun frontend tests/build if further relevant changes.
3. Review all production changes systematically. Particular concerns: SQL advisory locking and V3 migration on PostgreSQL; stale concurrent update ordering; recovery timestamps and revision guard; dedup of repeated telemetry; legacy snapshots; actual Spring demo/ledger profile loading.
4. Reconcile bounds/retention requirement explicitly: per-incident evidence count and collection duration are implemented, but old incident records/diagnosis archives are retained indefinitely and total storage across incidents is not globally bounded. No automatic deletion was introduced because existing history/data must be preserved. Decide a focused configurable archival/retention approach if needed, or clearly disclose remaining limitation; don't pretend collection duration is a complete database retention policy.
5. Review scheduler restart gaps. History is retained and gaps are reported, but missed telemetry is not fetched across the entire downtime. Avoid claims of continuous capture through outages of the collector itself.
6. Review frontend list polling race handling (detail races are guarded; old status-list requests can still arrive late), and unnecessary rerenders. Do not regress demonstrated map/filter/diagnosis stability.
7. Improve precise observed/potential explanations if needed. Existing readiness/metric degradation can be observed symptoms; distinguish these from proof of actual failed downstream requests.
8. Update README.md, SETUP.md, ARCHITECTURE.md, PRESENTATION.md. They have NOT been updated yet for this enhancement. Add configuration guide, accumulation/limits/provenance/diagnosis archive behavior, current filenames, new file inventory entries and truthful validation status. The manager presentation should stay concise. Do not create unrelated docs beyond this requested handover.
9. If Docker/Podman available, rebuild and run real acceptance sequentially, not concurrently. Preserve data. Do not use test fixtures as live validation evidence. If unavailable, record exact blockers and unexecuted checks.
10. Run final `git diff --check` (passed at this handover; rerun after edits), inspect diff, commit logically on feature branch. The enhancement has an in-progress checkpoint commit; add focused commits for further work. Do not merge. The user authorized this checkpoint push only.
11. Update this handover with final test counts/commit SHA/status before stopping. Final user report must be honest if live validation remains blocked.

## Commands for continuation

```bash
cd /Users/sisekomakomazi/Projects/blast-radius-engine
git status --short
git branch --show-current
mvn -f blast-radius-api/pom.xml clean verify
node --test frontend/src/incident-visuals.test.js
npm --prefix frontend run build
python3 -m py_compile scripts/run-phase11-e2e.py scripts/run-degradation-e2e.py
git diff --check
```

When container runtime is available (verify before running):

```bash
docker compose config --quiet
docker compose build blast-radius-api frontend
docker compose up -d
curl --fail http://127.0.0.1:8080/actuator/health
python3 scripts/run-phase11-e2e.py
# Wait for healthy telemetry and fresh traces, then:
python3 scripts/run-degradation-e2e.py --scenario all
```

Never use `docker compose down -v` or reset data. The current migration only creates the diagnosis archive table/index.

## Intended PostgreSQL demonstration procedure to document and verify

1. Start all lab services; confirm API, collector, Loki, Prometheus, Tempo and demo liveness, and fresh requests/traces. Keep incident DB running.
2. Record current incident IDs; stop only `postgres` with `docker compose stop postgres`.
3. Wait for exactly one new PostgreSQL OUTAGE/UNAVAILABLE incident with a direct availability event. Select it explicitly.
4. Record its ID/start/evidence count; leave it down for several polling intervals. Verify same ID/start, growing unique evidence, observed downstream failures only where evidence exists, other dependants potential/unknown.
5. Generate diagnosis; record returned evidenceVersion. Let evidence grow and verify diagnosis remains unchanged until explicitly rerun.
6. Start only monitored PostgreSQL with `docker compose start postgres`.
7. Wait for successful direct probe plus guarded consecutive healthy windows (default three). Verify the same incident resolves with resolvedAt and retained history. Verify dashboard updates without selecting another incident or resetting filters/pin/diagnosis.
8. Verify no stale telemetry reopens that incident. Always restore PostgreSQL if interrupted.

## Remote checkpoint request

The user explicitly asked to commit and push the work so another ChatGPT session can inspect it. Remote: `https://github.com/SLMakomazi/blast-radius-engine.git`; branch: `feat/outage-evidence-lifecycle`. Read this file on that branch before continuing. This is an unfinished implementation checkpoint, not a production-ready release. Push success and the exact final SHA must be verified from Git, not inferred from this note.

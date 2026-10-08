# MadlangaAI Blast Radius — validation and ChatGPT handover

Validated on 2026-10-08, Africa/Johannesburg. **Validation-only session; implementation remains unfinished. Live outage/recovery validation is NOT complete.**

## 1. Verified repository state and authorization

- Repository: `SLMakomazi/blast-radius-engine`.
- Workspace: `/Users/sisekomakomazi/Projects/blast-radius-engine`.
- Branch: `feat/outage-evidence-lifecycle`.
- Verified local HEAD and origin tracking ref: `85ce53f560644dc2caf1d13a36535cba2d297689`.
- Initial working tree was clean. Executed status, branch check, `git fetch origin`, `git switch feat/outage-evidence-lifecycle`, `git pull --ff-only origin feat/outage-evidence-lifecycle`, and `git log -8 --oneline`. Fast-forwarded from `f9e3902` by exactly two commits.
- `b54039336e92a207695d23fd02a53fc4f756d18b`: changes the recovery veto from any origin evidence to only `EvidenceSignal::confirmsUnavailable`. Lingering HTTP errors, latency and other symptoms no longer veto recovery by themselves.
- `85ce53f560644dc2caf1d13a36535cba2d297689`: adds `lingeringHttpErrorsDoNotPreventRecoveryAfterDirectAvailabilityReturns`, asserting resolution after three direct-UP evaluations with retained metric symptoms.
- No application/test/configuration code was edited. No fixes, commits, pushes, merges or PRs were performed. This handover is the only intentional repository file edit. Maven/Vite generated ignored build output; investigation probes and logs are under `/tmp`.
- No containers were started, stopped, rebuilt or removed. No faults were injected, no database was reset, and no volumes were deleted. Previous handover instructions authorizing a checkpoint push do **not** authorize a push in this validation session.

## 2. Test execution and build results

| Command | Result | Passed | Failed | Errors | Skipped |
|---|---|---:|---:|---:|---:|
| `mvn -f blast-radius-api/pom.xml clean verify` | Compilation, test compilation, all standard tests, JAR packaging and Boot repackage PASS | 213 | 0 | 0 | 0 |
| `node --test frontend/src/incident-visuals.test.js` | PASS | 12 | 0 | 0 | 0 |
| `npm --prefix frontend run build` | PASS; Vite 7.1.9, 1,668 modules | — | — | — | — |
| `mvn -f blast-radius-api/pom.xml -Dtest=TraceTopologyServiceLiveIT test` | FAIL: live telemetry unavailable | 0 | 1 | 0 | 0 |
| `mvn -f blast-radius-api/pom.xml -Dtest=LocalTelemetryProviderLiveIT test` | FAIL: live telemetry unavailable | 0 | 1 | 0 | 0 |
| `docker compose config --quiet` | PASS: configuration parses; does not establish runtime readiness | — | — | — | — |

Standard backend compilation targets Java 21; the local test runtime is Java 25. The two `*LiveIT` classes are opt-in and not discovered by the default Surefire naming convention. Therefore “213 passed, zero skipped” does not mean live integrations passed. Across the separately executed backend tests: 215 cases executed, 213 passed and 2 assertion failures. Both live failures are environment-dependent, not demonstrated implementation regressions.

Full logs, local to this machine:

- `/tmp/blast-validation-backend.log`
- `/tmp/blast-validation-frontend.log`
- `/tmp/blast-validation-build.log`
- `/tmp/blast-validation-live-topology.log`
- `/tmp/blast-validation-live-telemetry.log`
- `/tmp/blast-validation-compose.log`
- `/tmp/blast-validation-test-inventory.tsv`, `/tmp/blast-validation-focus-cases.txt`
- `/tmp/blast-validation-probes.log`, `/tmp/blast-validation-frontend-probes.log`

Surefire XML/text reports are in `blast-radius-api/target/surefire-reports/`. Temporary logs/probes are not committed and will not automatically be available to remote ChatGPT; essential findings and exact outputs are included below.

### Exact failing cases and investigation

1. `com.madlanga.blastradius.topology.service.TraceTopologyServiceLiveIT.discoversCanonicalDependencyChainFromRealTempoEvidence` — `java.lang.AssertionError`: actual edges `[]` did not contain `payment-service->customer-service`, `customer-service->document-service`, `document-service->postgres`. Providers logged `ResourceAccessException` and unreachable telemetry. Expected live dependencies were not available to discover.
2. `com.madlanga.blastradius.telemetry.provider.LocalTelemetryProviderLiveIT.retrievesNormalizedEvidenceFromRunningLocalLab` — assertion at test line 58: logs coverage `expected: <AVAILABLE> but was: <UNAVAILABLE>`. Warnings report Loki/Tempo unreachable, Prometheus metrics unavailable and health endpoints unreachable.

Independent runtime evidence: Docker socket `/Users/sisekomakomazi/.docker/run/docker.sock` does not exist; unsandboxed `curl --max-time 5 http://127.0.0.1:8080/actuator/health` returned exit 7, connection refused. These checks and IT runs did not attempt to start the environment. No failing standard test methods exist in this run. Expected mock-provider failure logs inside passing unit tests are not build failures.

### Standard backend class inventory

All entries below passed with zero failures, errors and skips.

| Test class | Executed cases |
|---|---:|
| `com.madlanga.blastradius.ArchitectureBoundaryTest` | 3 |
| `com.madlanga.blastradius.BlastRadiusApplicationTests` | 1 |
| `com.madlanga.blastradius.diagnosis.mapper.DiagnosisRequestMapperStoredJsonTest` | 2 |
| `com.madlanga.blastradius.diagnosis.mapper.DiagnosisRequestMapperTest` | 1 |
| `com.madlanga.blastradius.diagnosis.provider.GeminiDiagnosisProviderTest` | 7 |
| `com.madlanga.blastradius.diagnosis.service.DiagnosisArchiveTest` | 1 |
| `com.madlanga.blastradius.diagnosis.service.DiagnosisServiceTest` | 2 |
| `com.madlanga.blastradius.incident.controller.IncidentControllerTest` | 6 |
| `com.madlanga.blastradius.incident.service.IncidentAnalysisServiceTest` | 17 |
| `com.madlanga.blastradius.incident.service.IncidentSeverityServiceTest` | 6 |
| `com.madlanga.blastradius.lifecycle.scheduler.IncidentLifecycleSchedulerTest` | 2 |
| `com.madlanga.blastradius.lifecycle.service.IncidentEvidenceAccumulatorTest` | 4 |
| `com.madlanga.blastradius.lifecycle.service.IncidentLifecycleServiceTest` | 14 |
| `com.madlanga.blastradius.shared.sanitization.TelemetrySanitizerTest` | 29 |
| `com.madlanga.blastradius.telemetry.model.EvidenceProvenanceTest` | 5 |
| `com.madlanga.blastradius.telemetry.model.HealthEvidenceTest` | 8 |
| `com.madlanga.blastradius.telemetry.model.LogEvidenceTest` | 7 |
| `com.madlanga.blastradius.telemetry.model.MetricEvidenceTest` | 6 |
| `com.madlanga.blastradius.telemetry.model.SpanEvidenceTest` | 9 |
| `com.madlanga.blastradius.telemetry.model.TelemetryBundleTest` | 9 |
| `com.madlanga.blastradius.telemetry.model.TelemetryQueryTest` | 5 |
| `com.madlanga.blastradius.telemetry.provider.LocalTelemetryProviderTest` | 6 |
| `com.madlanga.blastradius.telemetry.provider.health.ActuatorHealthAdapterTest` | 6 |
| `com.madlanga.blastradius.telemetry.provider.health.DatabaseHealthAdapterTest` | 1 |
| `com.madlanga.blastradius.telemetry.provider.loki.LokiLogAdapterTest` | 6 |
| `com.madlanga.blastradius.telemetry.provider.prometheus.PrometheusMetricsAdapterTest` | 5 |
| `com.madlanga.blastradius.telemetry.provider.tempo.CapturedTempoRegressionTest` | 5 |
| `com.madlanga.blastradius.telemetry.provider.tempo.TempoTraceAdapterTest` | 13 |
| `com.madlanga.blastradius.topology.config.ConfiguredTopologyTest` | 4 |
| `com.madlanga.blastradius.topology.model.TopologyModelTest` | 3 |
| `com.madlanga.blastradius.topology.service.BlastRadiusGraphServiceTest` | 11 |
| `com.madlanga.blastradius.topology.service.TopologyServiceTest` | 7 |
| `com.madlanga.blastradius.topology.service.TraceTopologyServiceTest` | 2 |

### Focused passed cases

```text
com.madlanga.blastradius.diagnosis.service.DiagnosisArchiveTest
  storesExactEvidenceRevisionWithoutUpdatingIncidentOrPriorDiagnosis
com.madlanga.blastradius.incident.service.IncidentAnalysisServiceTest
  ignoresHttpFiveXxLatencySumEvenWhenItIncreases
  marksUnobservedTheoreticalNodeUnknownWhenCoverageIsPartial
  acceptsSustainedCpuPressureAsDegradationEvidence
  usesPreIncidentTraceHistoryToRecoverDependencyMissingFromOutageWindow
  timeoutCounterAloneIsSupportingNotSufficientFailureEvidence
  infersPeerlessFailedDependencyWhenTopologyHasSingleCandidate
  findsDeepestFailingDependencyAndClassifiesObservedRadius
  acceptsHighMeanHttpLatencyAsDegradationEvidence
  reportsFailureEvidenceOutsideTheoreticalRadiusAsUnexpected
  acceptsSlowSpanAsTraceLatencyEvidence
  excludesHistoricalFailuresFromEveryEvidenceFamilyAndKeepsCoverageIndependent
  rejectsOriginHintThatIsNotInDiscoveredTopology
  serverFailureAloneDoesNotAccuseRetainedDependency
  counterMetricMustIncreaseInsideWindowBeforeItCountsAsFailureEvidence
  acceptsSustainedDatabasePoolContentionAsDegradationEvidence
  fullCoverageDistinguishesTheoreticalOnlyFromObserved
  acceptsIncreasingFiveXxCountAsFailureEvidence
com.madlanga.blastradius.lifecycle.scheduler.IncidentLifecycleSchedulerTest
  resolvesOnlyAfterConsecutivePositiveAvailabilityChecksAndCollectsRecoveryEvidence
  lingeringHttpErrorsDoNotPreventRecoveryAfterDirectAvailabilityReturns
com.madlanga.blastradius.lifecycle.service.IncidentEvidenceAccumulatorTest
  emptyRecentWindowRetainsEarlierEvidence
  limitsNeverEvictStoredHistoryAndExposeCollectionGap
  overlappingWindowsAccumulateAndDeduplicateUnstableProviderIds
  overlappingMetricDeltasReferToOneProviderObservation
com.madlanga.blastradius.lifecycle.service.IncidentLifecycleServiceTest
  explicitResolutionPreservesPeakSeverityAndIncidentHistory
  resolvedOriginCannotReopenFromStaleEvidence
  degradationEvidenceDoesNotCreateOutageIncident
  createsActiveIncidentWhenFailureEvidenceExists
  theoreticalAnalysisDoesNotCreateFalseIncident
  symptomTextAloneCannotCreateOutage(String)[1]
  symptomTextAloneCannotCreateOutage(String)[2]
  symptomTextAloneCannotCreateOutage(String)[3]
  symptomTextAloneCannotCreateOutage(String)[4]
  symptomTextAloneCannotCreateOutage(String)[5]
  symptomTextAloneCannotCreateOutage(String)[6]
  repeatedPollsKeepIdentityAndOriginalStart
  recoveryCannotResolveARevisionUpdatedByAnotherCollector
  recoveryCollectionPreservesActiveIncidentUntilGuardedResolution
com.madlanga.blastradius.telemetry.provider.LocalTelemetryProviderTest
  missingEvidenceIsNotInterpretedAsHealthy
  assemblesFullBundleWhenAllProvidersSucceed
  neverReturnsNullWhenAllEvidenceFamiliesAreUnavailable
  logsUnavailableDoesNotSuppressOtherFamilies
  unhandledExceptionFromLokiIsIsolated
  allProvidersUnavailableProducesAllUnavailableWithWarnings
com.madlanga.blastradius.telemetry.provider.health.DatabaseHealthAdapterTest
  directDatabaseProbeDistinguishesUnavailableAvailableAndConfigurationError
com.madlanga.blastradius.topology.config.ConfiguredTopologyTest
  readinessDownWhileLivenessUpIsNotAnAvailabilityFailure
  profilePropertiesBindComponentsAndDependencies
  invalidConfiguredEdgesAreRejected
  secondApplicationUsesSameAnalysisWithoutInventingDownstreamFailures
```

Frontend passed cases: missing evidence stays unknown; only hard liveness failure is red for the existing fixtures; resolved views remain historical; graph branches do not invent edges; dependency explanation names the immediate dependency; search includes impacts/evidence; time buckets preserve counts; explanations do not invent shutdown/OOM; stored JSON and duplicate evidence work; origin labels differ from downstream labels; structured outage headlines override latency/500; helper detail polling preserves selection and rejects the tested stale responses. These tests do not cover all React effects or all timestamp formats (C3–C5).

## 3. Confirmed issues

**10 confirmed product issues/gaps:** six groups reproduced by executing the current production methods/functions in temporary probes (C1–C6), and four established by direct source inspection (C7–C10). Reproduction probes used isolated test inputs, not production evidence or live database writes. They were not added to the repository or included in the 213-test count. The stale-update probe used an in-memory repository and proves domain ordering behavior, not PostgreSQL lock implementation.

Java probe: `/tmp/BlastValidationProbe.java`, compiled against the current Maven test classpath copied from Surefire XML into `/tmp/blast-validation-classpath`. Frontend probes imported actual helpers and executed the actual `loadIncidents` function text with controlled promise completion. The output was:

```text
UNKNOWN database confirmsUnavailable=true
capped recovery: timeline=1 containsUP=false limited=true
timeline first=2026-10-08T08:00:00.100Z expected=2026-10-08T08:00:00Z
newer DOWN then older UP snapshot: status=RESOLVED retainsNewerDown=true
Newer fractional timestamp accepted: false
List after RESOLVED response then stale ACTIVE response: ACTIVE
LOG SYMPTOM containing liveness words: tone=failed label=Unavailable
```

### C1 — UNKNOWN database health becomes a confirmed outage

- **Severity:** High.
- **File/line:** `blast-radius-api/src/main/java/com/madlanga/blastradius/incident/service/IncidentAnalysisService.java:139-144; blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/health/DatabaseHealthAdapter.java:38-55`.
- **Current behavior:** The adapter returns UNKNOWN for authentication/configuration errors with httpStatus=null. Correlation applies the UNKNOWN/null HTTP-status rule to database/availability as well as liveness and emits AVAILABILITY_UNAVAILABLE.
- **Expected behavior:** An inconclusive or misconfigured database probe must remain UNKNOWN; only a reliable failed availability observation may create an outage.
- **Proof:** Temporary execution of the actual analysis service with database UNKNOWN printed `UNKNOWN database confirmsUnavailable=true`. DatabaseHealthAdapterTest passes because it checks the adapter result, not its subsequent correlation.
- **Recommended correction:** Separate database UNKNOWN from HTTP transport-unreachable observations. Preserve a typed probe outcome/reason rather than infer JDBC reachability from an absent HTTP status.
- **Regression test:** Add adapter → correlation → lifecycle tests for SQLSTATE 28P01, runtime/configuration failure, genuine connection refusal and successful connection.

### C2 — Older analysis can overwrite current assessment and be used for recovery

- **Severity:** High.
- **File/line:** `blast-radius-api/src/main/java/com/madlanga/blastradius/lifecycle/service/IncidentLifecycleService.java:54-70,108-115; blast-radius-api/src/main/java/com/madlanga/blastradius/lifecycle/service/IncidentEvidenceAccumulator.java:16-18,35-41,56-57; blast-radius-api/src/main/java/com/madlanga/blastradius/lifecycle/scheduler/IncidentLifecycleScheduler.java:124-149`.
- **Current behavior:** A late older analysis is accepted, assigned a fresh updatedAt, and returned as the current revision. The scheduler checks direct availability in that older raw analysis; resolveIfUnchanged only compares the newly saved revision, not the observation chronology. Max analysisTo preserves a timestamp but does not reject stale recovery evidence.
- **Expected behavior:** An older healthy observation must not resolve an incident whose newer direct observation confirms unavailability. Stale batches may append historical evidence without replacing current health/coverage or qualifying recovery.
- **Proof:** Temporary sequential service reproduction saved DOWN at 08:00:29 (window ending :30), then older UP at :10, then invoked the same guarded resolution API with that returned revision. It produced `status=RESOLVED retainsNewerDown=true`. An in-memory repository isolated the domain ordering flaw; real concurrent PostgreSQL scheduling was NOT tested.
- **Recommended correction:** Track monotonic current availability/evaluation watermarks and reject stale recovery candidates under the scope lock. Validate the latest direct origin observation and distinct healthy-check sequence at resolution.
- **Regression test:** Add reverse-completion-order collector tests and a PostgreSQL concurrency test; verify a delayed healthy batch cannot overwrite or resolve a newer DOWN revision.

### C3 — Timestamp strings are compared instead of instants

- **Severity:** Medium.
- **File/line:** `blast-radius-api/src/main/java/com/madlanga/blastradius/lifecycle/service/IncidentEvidenceAccumulator.java:31,56-57; frontend/src/incident-visuals.js:102-105`.
- **Current behavior:** Timeline sorting, snapshot from/to merging and frontend stale-detail rejection use lexical string comparisons. ISO timestamps with different fractional precision do not sort chronologically this way.
- **Expected behavior:** Compare parsed instants or monotonic versions, retaining full precision where required.
- **Proof:** Production accumulator sorted 08:00:00.100Z before 08:00:00Z. Actual acceptIncidentDetail rejected the newer .100Z response after a whole-second Z response (`Newer fractional timestamp accepted: false`).
- **Recommended correction:** Use Instant parsing in Java. In the UI prefer evidenceVersion/update revision; otherwise parse timestamps with appropriate precision and tie-breaking.
- **Regression test:** Add whole-second versus fractional-second, varying precision and stale/newer response ordering cases to accumulator and frontend tests.

### C4 — Old incident-list responses can overwrite the selected status view

- **Severity:** Medium.
- **File/line:** `frontend/src/main.jsx:44-59,112-118`.
- **Current behavior:** Every completed list request updates incidents unconditionally. Clearing the interval does not cancel in-flight requests when ACTIVE/RESOLVED changes; overlapping polls have no request generation or scope guard.
- **Expected behavior:** Only the newest response for the current list scope should update the list. Detail selection and diagnoses should remain independent.
- **Proof:** A temporary harness executed the actual loadIncidents function extracted from main.jsx with deferred fetch responses. Completing RESOLVED first and stale ACTIVE second left the list at ACTIVE. No application source was changed.
- **Recommended correction:** Scope list requests by status and generation, cancel or ignore superseded responses, and consider preventing overlapping polling work.
- **Regression test:** Add a component/controller test with reversed ACTIVE/RESOLVED fetch completion and out-of-order repeated polls.

### C5 — Map still treats log wording as proof of unavailability

- **Severity:** High.
- **File/line:** `frontend/src/incident-visuals.js:13-16,28-40,50-51`.
- **Current behavior:** The headline classification uses backend fields, but nodeView still ORs structured availability with regex matches over every signal, regardless of family or Kind. A LOG/SYMPTOM mentioning liveness health DOWN renders red and Unavailable.
- **Expected behavior:** A symptom or quoted dependency failure must not override structured evidence and claim the emitting component is unavailable.
- **Proof:** Calling actual nodeView with a LOG/SYMPTOM signal `ERROR log: dependency reported liveness health DOWN` produced `tone=failed label=Unavailable`.
- **Recommended correction:** Prefer typed direct HEALTH availability evidence. If legacy compatibility is required, explicitly validate provenance/family and do not override known SYMPTOM/POTENTIAL categories.
- **Regression test:** Add map tests for logs quoting health failures, negative/quoted phrases, dependency errors, and genuine direct availability observations.

### C6 — Collection limits discard the evidence that justified recovery

- **Severity:** Medium.
- **File/line:** `blast-radius-api/src/main/java/com/madlanga/blastradius/lifecycle/service/IncidentEvidenceAccumulator.java:21-28,64,72-75; blast-radius-api/src/main/java/com/madlanga/blastradius/lifecycle/scheduler/IncidentLifecycleScheduler.java:127-149`.
- **Current behavior:** At the count or duration cap all new observations, including successful direct recovery checks, are excluded. Raw analysis can still resolve the incident, so retained history and subsequent diagnosis may contain only DOWN observations. The cap warning is explicit; this is not silent eviction of old history.
- **Expected behavior:** Bound collection while retaining auditable lifecycle transitions and the recovery evidence used to resolve the incident.
- **Proof:** Actual accumulator with cap=1 retained the initial DOWN, dropped a subsequent UP, and printed `timeline=1 containsUP=false limited=true`. Scheduler uses uncapped analysis for its direct-UP check.
- **Recommended correction:** Reserve bounded space for availability transitions/recovery proof or store transition evidence separately. Clearly expose collection gaps and preserve all already retained evidence.
- **Regression test:** Add cap and collection-duration tests that resolve the incident and verify the exact positive checks remain available in history and AI input.

### C7 — JDBC health is omitted from health coverage calculation

- **Severity:** Medium.
- **File/line:** `blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/LocalTelemetryProvider.java:101-110; blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/health/ActuatorHealthAdapter.java:118-127; blast-radius-api/src/main/java/com/madlanga/blastradius/lifecycle/scheduler/IncidentLifecycleScheduler.java:129-133`.
- **Current behavior:** Database evidence is appended, but aggregate health coverage is copied only from the Actuator result. With a JDBC-only profile and no HTTP endpoints, Actuator reports UNAVAILABLE even when the configured database check succeeds.
- **Expected behavior:** Coverage should describe the configured probes, including database-only deployments; absent unconfigured HTTP probes should not block guarded recovery.
- **Proof:** Direct source proof: zero Actuator probeCount returns UNAVAILABLE; LocalTelemetryProvider always uses healthResult.getCoverage() and ignores databaseHealth in coverage. Scheduler then refuses recovery. This branch was not exercised against a live database.
- **Recommended correction:** Aggregate coverage over configured probe types and distinguish unavailable configured probes from probe types not configured.
- **Regression test:** Add LocalTelemetryProvider and scheduler tests for JDBC-only profiles, successful/unknown/down database checks and mixed HTTP/JDBC coverage.

### C8 — Telemetry source timeout properties are not applied

- **Severity:** Medium.
- **File/line:** `blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/config/TelemetryConfig.java:33-38; blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/loki/LokiLogAdapter.java:56-65; blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/prometheus/PrometheusMetricsAdapter.java:54-62; blast-radius-api/src/main/java/com/madlanga/blastradius/telemetry/provider/tempo/TempoTraceAdapter.java:67-75; infrastructure/monitoring/application-ledger.yml:36-47`.
- **Current behavior:** Loki, Prometheus and Tempo bind timeout settings, but their constructors only set baseUrl/build on the shared RestClient builder; none reads its timeout. The default factory is fixed at 5-second connect / 10-second read and the singleton builder can also be mutated by health construction.
- **Expected behavior:** Each monitoring profile timeout should govern its own adapter without construction-order coupling.
- **Proof:** Repository-wide getTimeout search plus constructor inspection confirms these properties are unused by these three adapters. The example declares 5-second timeouts but those values are not wired.
- **Recommended correction:** Construct isolated clients/request factories per adapter using their bound timeout settings; avoid mutating one shared builder instance.
- **Regression test:** Use local delayed-response tests to assert independent timeout behavior for every adapter and profile.

### C9 — Scheduler logs successful resolution even when revision guard rejects it

- **Severity:** Low.
- **File/line:** `blast-radius-api/src/main/java/com/madlanga/blastradius/lifecycle/scheduler/IncidentLifecycleScheduler.java:162-166; blast-radius-api/src/main/java/com/madlanga/blastradius/lifecycle/service/IncidentLifecycleService.java:108-115`.
- **Current behavior:** resolveIfUnchanged returns Optional.empty on a concurrent revision change, but the scheduler ignores the result, clears its counter and logs automatically resolved.
- **Expected behavior:** A successful resolution log must reflect a confirmed RESOLVED result; rejected attempts should be reported accurately.
- **Proof:** Direct source proof: line 163 ignores the returned Optional; line 165 emits success unconditionally. The existing service test demonstrates that the guard can return empty.
- **Recommended correction:** Check the returned incident/status before logging resolution; log a guarded retry otherwise.
- **Regression test:** Add a scheduler test with resolveIfUnchanged returning empty and verify no successful-resolution event/log is emitted.

### C10 — Collection bounds are not a complete storage-retention policy

- **Severity:** Medium.
- **File/line:** `blast-radius-api/src/main/java/com/madlanga/blastradius/lifecycle/service/IncidentEvidenceAccumulator.java:21-28; blast-radius-api/src/main/java/com/madlanga/blastradius/diagnosis/service/DiagnosisArchive.java:17-30; blast-radius-api/src/main/resources/db/migration/V3__diagnosis_evidence_snapshots.sql:2-10`.
- **Current behavior:** Evidence per incident is capped, but resolved incidents are retained indefinitely and every requested diagnosis inserts another full evidence snapshot. There is no configured archive/retention policy bounding total history storage.
- **Expected behavior:** Meet the requested retention requirement with explicit, configurable archival/retention semantics that do not silently destroy existing history.
- **Proof:** Archive.save always INSERTs full snapshots; migrations and repository code provide no retention/archive mechanism. The previous handover already identified this unfinished requirement.
- **Recommended correction:** Agree and implement a bounded archival policy with explicit disclosure and preserved auditability. Do not delete existing records or volumes as a shortcut.
- **Regression test:** Add policy tests for active versus resolved incidents, repeated diagnoses, archival retrieval and explicit retention notices.

## 4. Unverified risks — not confirmed production defects

**Six unverified risk areas**, distinct from the ten confirmed issues:

1. **Real PostgreSQL transactions/migrations/restarts.** V2 unique ACTIVE-origin index, V3 diagnosis table, advisory-lock ownership, JSONB persistence, rollback and cross-process contention were not exercised against PostgreSQL. Spring tests use H2 with Flyway disabled; lifecycle/archive tests mock persistence. No proof of database-level loss or duplicates was obtained.
2. **Concurrent/replayed recovery checks.** `healthyWindows` is process-local and counts invocations, not distinct observation IDs. Restart resets it conservatively, but repeated cached observations, overlapping evaluator calls and multi-instance failure/healthy interleavings need tests. Default single-process fixed-delay scheduling reduces this risk but is not distributed coordination.
3. **Completeness under lag, source caps and slow collection.** Fixed delay is 10 seconds **after evaluation completes**, not a guaranteed 10-second cadence. Lookback is only 20 seconds; adapters run serially. Long calls/restarts or late logs/traces can leave gaps. `withinWindow` stops including beyond-window live health when collection runs over its 60-second live-query allowance. Source query limits and sampling timestamps may further affect completeness. No load/lag experiment was performed.
4. **Multiple simultaneous origins / actual deployment portability.** Core selection returns one origin per proactive evaluation; recovery loops only over existing incidents. Independent simultaneous outages may leave another unavailable origin without its own incident. Tests demonstrate a second topology and property binding, not live onboarding, profile-isolation behavior or simultaneous-origin fairness. No automatic multi-application registry exists.
5. **Growth/performance and AI context size.** Incident list polling returns full snapshots without pagination, then fetches selected detail again. Every poll can increment evidenceVersion and lastCollectedAt even with no new retained evidence. Large timelines, all histories, full AI prompts and repeated diagnosis snapshots may burden the 96-MB API heap and frontend rerenders. No memory/load/Gemini end-to-end run was performed.
6. **Full current-browser interaction behavior.** Source review supports stable keys and independent state, and helper tests pass. This session did not rerun mounted React/browser tests for hover/focus/scroll/pinning, active AI requests or slow-network races. The previous handover's labelled browser-fixture test is historical evidence only, not a fresh live-stack pass.

## 5. Implementation status against the previous handover

| Requirement | Status and limits |
|---|---|
| Preserve capability-first structure and existing history | Implemented structurally; no runtime data was modified during this review. Actual restart/database behavior remains untested. |
| OUTAGE / UNAVAILABLE distinct from symptoms/root cause | Implemented for normal typed observations and history headline; broken for database UNKNOWN (C1) and map regex fallback (C5). Root cause remains UNDETERMINED unless advisory reasoning identifies supported hypotheses. |
| HTTP 500, latency, readiness or pressure alone do not create outage | Lifecycle gate requires typed HEALTH unavailability; existing rejection/readiness tests pass. The analysis API still intentionally reports degradation evidence; analysis is not equivalent to persisting an outage. |
| Origin and impact from telemetry/topology | Core service/dependency names are not hard-coded. Readiness/CPU/latency can classify OBSERVED symptoms, which is broader than confirmed downstream request failures. Dependency-attributed errors are POTENTIAL. Missing signals remain potential/unknown, not healthy. |
| Continuous accumulated history | Implemented with merge/dedup and stable UUID/start time until explicit limits; no automatic downtime catch-up. C2/C3/C6 remain. |
| Dedup and provenance | Stable log/metric/span IDs plus fallback keys; occurrence and collection timestamps retained; overlapping-window tests pass. Full backend source-cap and high-volume behavior not tested. |
| Transactional updates / no stale reopening | Scope locks and resolved-time barrier implemented; stale-reopening and revision-rejection unit tests pass. C2 shows the revision check is not sufficient for out-of-order observations. Real PostgreSQL contention untested. |
| Guarded recovery with residual symptoms | Latest two commits implement this correction; both scheduler tests pass. Full coverage + no direct DOWN + direct UP + configured consecutive count are required. C1/C2/C6/C7/C9 limit correctness/auditability. |
| Retention/storage policy | Partial: 20,000 observations and seven-day collection duration by default, with explicit warning and no eviction of already stored history. No total retention/archive policy (C10). |
| Application configuration | Demo defaults isolated in application-demo.yml; ledger example, validated configured topology, scope guard, configurable HTTP liveness/JDBC target. One application/environment and one JDBC target per deployment; PostgreSQL driver present. C7/C8 and live profile testing remain. |
| Frontend live refresh | Silent five-second list/detail refresh, explicit selection, stable map key, independent filters and diagnosis implemented; C3/C4/C5 remain. Full component/browser test coverage incomplete. |
| AI accumulated evidence + immutable diagnosis snapshot | Implemented: mapper reads stored accumulated JSON; POST generates explicitly; archive stores exact snapshot/version and result in one INSERT. Existing diagnosis is not replaced by polling. Actual V3 migration, Gemini request and persisted retrieval not tested live. C6/C10 affect investigation history. |
| Documentation | Previous handover read completely. README/SETUP/ARCHITECTURE/PRESENTATION still need enhancement updates. Only this handover was changed as instructed. |

Documentation housekeeping: `PRESENTATION.md:683` still links nonexistent `scripts/run-stage2-e2e.py`; `ARCHITECTURE.md:694` describes same-incident degradation recovery, whereas the updated degradation runner asserts no new outage and request recovery. These stale descriptions were not edited. They are additional documentation work, not included in the ten product-issue count.

## 6. Evidence lifecycle findings

- Every successful `persistLifecycle` call for an existing ACTIVE origin merges a new analysis with persisted history even if the new raw analysis contains only recovery/symptom evidence. Identity, startedAt and createdAt remain stable; severity keeps its peak.
- Stable source IDs distinguish observations; derived metric numeric deltas are normalized in the dedup category. Previously collected entries retain their first stored metadata. New direct probes represent new observations, not duplicate polls of one source record.
- Evidence is duplicated into timeline and per-component evidence for the snapshot. OBSERVED/UNEXPECTED historical states remain sticky. This is cumulative incident impact, not a live health map. Latest coverage is a snapshot rather than a full coverage history.
- Collection stops admitting new events at the count or duration cap. Warning/collectionLimited is persistent; updatedAt/evidenceVersion/lastCollectedAt still move. Thus updatedAt alone cannot demonstrate new evidence. Recovery can still occur without persisted recovery evidence (C6).
- Scheduler outage or an evaluation that takes longer than the lookback can skip observations permanently. A later merge records a gap warning but does not fetch the missing interval. Topology missing/expired, unavailable providers, serialization/persistence exceptions or health exclusion after slow collection can also prevent evidence updates.
- History is stored in the incident JSONB snapshot and retained on resolution. Persistence is intended to survive process restarts; no process restart was executed in this session.
- SQL advisory locks serialize scope updates and the existing partial unique index prevents two ACTIVE rows per scope. These guard writes, not observation chronology (C2). Out-of-order incoming snapshots can still replace non-history metadata.
- AI input comes from the stored accumulated snapshot, including impacts/paths, logs/metrics/traces/health and provenance. Each explicit diagnosis archives the exact input JSON and evidenceVersion; polling never calls diagnosis. The UI caches diagnoses only for its current browser session and does not automatically load the archive after reload.

## 7. Recovery findings

- The default threshold is three; the constructor clamps configured values to at least two. The existing positive-check test verifies no resolution before the required count and resolution once reached.
- Current raw-analysis symptoms no longer veto recovery; the new regression specifically includes an HTTP 500 metric. Persisted historical DOWN evidence does not itself veto recovery because the scheduler evaluates newly fetched raw analysis, not the accumulated snapshot.
- Every accepted healthy window requires full coverage and an AVAILABILITY_AVAILABLE event for the origin. Direct unavailable events, missing UP, partial coverage, missing persisted result and exceptions reset the process-local count.
- PostgreSQL is checked using JDBC connection plus isValid; it is not inferred from application readiness. C1 corrupts UNKNOWN interpretation downstream, and C7 can block database-only recovery.
- A repeated old direct DOWN still present in the current query window can delay recovery; after it ages out, fresh direct UP can qualify. Raw checks lack a distinct-probe watermark (risk 2), and stale-write ordering can permit unsafe resolution (C2).
- `resolveIfUnchanged` rejects a changed updatedAt revision under the scope lock; successful resolution preserves snapshot/start and sets resolvedAt. Its protective unit test passes. It does not guarantee the latest admitted raw analysis is temporally current.
- No duplicate incident was observed in real runtime because the live stack was inaccessible. Unique-index and locking inspection is not a real concurrency pass.

## 8. Frontend polling and state findings

- New ACTIVE entries should appear on the next successful five-second list refresh; no initial/newest auto-selection is performed. A click controls selection.
- Background refresh does not set loading or clear errors and fetches the same selected ID even when that ID leaves the ACTIVE list. This supports in-place resolution.
- Map key is selected.id; ServiceMap keeps hover, pinned component and search in local state. Evidence filters reset only on selected ID change. Updating detail does not intentionally remount the map.
- Diagnoses are keyed by selected incident ID in separate state; runDiagnosis is explicit and an in-flight result is associated with the captured selected ID. Polling does not reset or restart it.
- Detail response guards check selected ID and updatedAt; the timestamp guard is faulty for varying precision (C3). List responses have no scope/generation guard (C4).
- Background fetch failures are silently ignored, while the header may still say “Live incident monitoring.” Treat that as an operator-awareness limitation; no live failure UX experiment was performed.
- React still rerenders when lastUpdated or changed snapshot metadata is set; stable keys preserve state but are not a guarantee of minimal render cost. Full snapshots are compared using JSON.stringify. There is no separate metadata-only list endpoint or component-level polling test suite.

## 9. Runtime tests not executed — approval boundaries

**NOT EXECUTED — REQUIRES APPROVAL:** `python3 scripts/run-phase11-e2e.py` stops/starts PostgreSQL and services, including Tempo for partial observability. `python3 scripts/run-degradation-e2e.py --scenario all` injects lab faults and generates traffic. Neither was run in this session. Likewise no rebuild, stack startup, controlled outage, restart, migration against the incident DB, diagnosis POST, forced concurrency write or data modification was performed.

Read-only integration checks were attempted and failed as reported above. The Docker socket and API are unavailable. Do not claim a Stage 1 result of 6/6, degradation result of 4/4, or total 10/10. Current runtime outage/recovery behavior, migration safety and persistence across restart remain untested.

### Exact PostgreSQL outage/recovery procedure for a later approved run

These commands are documentation, **not authorization to execute them**. Obtain explicit approval to start/rebuild the stack if needed and separate approval covering the controlled outage/integration run. Never run `down -v`, delete volumes or reset databases.

1. Establish a running build of this exact branch. If approved to rebuild: `docker compose build blast-radius-api frontend`, then `docker compose up -d`. Confirm `docker compose ps`, API `/actuator/health`, Loki `/ready`, Prometheus `/-/ready`, Tempo `/ready`, collector health and fresh real traffic/traces. Keep `blast-radius-db` running.
2. Capture baseline IDs via GET `http://127.0.0.1:8080/api/v1/blast-radius/incidents?status=ACTIVE`. Require a healthy monitored PostgreSQL baseline so an existing outage does not invalidate the test.
3. With outage approval: `docker compose stop postgres`. Poll the incident list until one new PostgreSQL incident appears. Verify OUTAGE / UNAVAILABLE, typed direct availability evidence and origin=postgres. HTTP 500/latency must remain symptoms, not the headline.
4. GET `/api/v1/blast-radius/incidents/{id}` repeatedly over at least three completed scheduler evaluations. Record UUID, startedAt, evidenceVersion, unique evidence IDs/counts, timestamps/provenance and impact paths. Verify original evidence remains and count grows below the cap. No unsupported downstream service may become unavailable solely through dependency errors.
5. Optionally, only if approved to invoke AI/write a diagnosis: POST `/incidents/{id}/diagnosis`; record its evidenceVersion. New observations must not replace that diagnosis until another explicit request.
6. Restore with `docker compose start postgres` even if an assertion fails. Wait for verified JDBC UP and three qualifying covered evaluations. Confirm same incident ID/start, RESOLVED/resolvedAt, retained failure history and retained recovery proof. Confirm no stale reopening.
7. Observe the selected frontend detail change without jumping to another incident, losing map pin/hover/focus/filter/scroll or replacing the diagnosis. Test delayed network responses separately in an isolated harness.
8. After complete restoration and fresh telemetry, run the six-case outage suite and four-case degradation suite sequentially if that broader fault-injection scope was approved. Do not modify assertions to turn infrastructure failures into passes.

## 10. Recommended priorities and ChatGPT continuation

1. **P0/P1 correctness:** fix C1 database UNKNOWN promotion, C2 stale recovery chronology and C5 frontend false-unavailable interpretation. Add regression tests before accepting the fixes.
2. **P1/P2 history and refresh:** fix C3 temporal ordering, C4 response scope races and C6 retained recovery proof. Keep the latest residual-symptom recovery behavior and both scheduler tests.
3. **P2 portability/operations:** fix C7 configured probe coverage and C8 timeout isolation; then address C9 truthful lifecycle logs and C10 explicit archive/retention policy.
4. Validate real PostgreSQL advisory locking, migrations, stale writes, duplicate creation, restart continuity and concurrent recovery against an approved isolated environment. Preserve current history and volumes.
5. Add failure/slow-provider and mounted frontend state tests; validate explicit second-profile deployment. Document collection gaps, source limits, remaining single-application/one-JDBC-target scope and AI limits honestly.
6. Update the four main documentation files when implementation work is authorized. Keep current names (`run-phase11-e2e.py`, `run-degradation-e2e.py`) and avoid obsolete validation-stage instructions.
7. Rerun `mvn -f blast-radius-api/pom.xml clean verify`, frontend node tests/build and applicable approved integrations. Record exact failures and do not claim live acceptance from unit tests.

### Instructions to the next ChatGPT session

Read this handover first. Check `git status` and branch/HEAD and preserve any local changes. The application code validated here is commit `85ce53f560644dc2caf1d13a36535cba2d297689`. After validation, the user explicitly requested committing and pushing this handover alone to `feat/outage-evidence-lifecycle` so ChatGPT can review the findings. This documentation publication does not authorize a merge, deployment or outage. Obtain the user's implementation authorization, then address the priority list with focused changes. Never reset data, discard work, weaken tests or replace real evidence with fixtures. Keep deterministic outage decisions separate from AI.

The validator made no fixes. All issue recommendations are for the subsequent implementation session.

## 11. Final local verification

`git diff --check` passed. `git diff --name-only` and `git status --short` showed only `CHATGPT_HANDOVER.md` modified; no untracked repository files were created. Docker Compose configuration validation returned exit 0. No commit or push was made. The final read-only remote-ref check is recorded in the validation session output.

## 12. Handover publication follow-up

After the validation-only session, the user explicitly authorized committing and pushing only `CHATGPT_HANDOVER.md` to the existing feature branch. Statements above that no commit or push occurred describe the validation session. No application code or test results were changed for publication. Temporary logs referenced here remain local; the results and issue evidence are recorded in this document for remote review.

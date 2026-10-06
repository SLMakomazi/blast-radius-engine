# Stage 1–2 Local Validation

Stage 1–2 runners are the acceptance layer for the local Blast Radius Engine. They prove that the deterministic engine behaves correctly under different failure shapes before enterprise MadlangaAI integration.

## Principle

> Topology calculates potential impact; runtime telemetry proves observed impact; AI explains sanitized evidence.

Gemini is not required for this suite.

## Scenarios

| Scenario | Expected origin | Maximum topology propagation | Key proof |
| --- | --- | ---: | --- |
| Healthy baseline | none | 0 | no false ACTIVE incident |
| PostgreSQL outage | postgres | 3 | deepest dependency failure can propagate upstream |
| Document service outage | document-service | 2 | PostgreSQL is not falsely blamed |
| Customer service outage | customer-service | 1 | only upstream callers can be affected |
| Payment service outage | payment-service | 0 | no false downstream propagation |
| Partial observability | customer-service | 1 | missing telemetry cannot prove recovery |

The runner also checks that repeated lifecycle evaluations retain one ACTIVE incident UUID and that automatic recovery resolves that same UUID.

## Run

Start the normal local stack first:

~~~bash
cp -n .env.example .env
docker compose up -d --build
~~~

Then run all scenarios:

~~~bash
python3 scripts/run-phase11-e2e.py
~~~

Run one scenario:

~~~bash
python3 scripts/run-phase11-e2e.py --scenario postgres
python3 scripts/run-phase11-e2e.py --scenario document-service
python3 scripts/run-phase11-e2e.py --scenario customer-service
python3 scripts/run-phase11-e2e.py --scenario payment-service
python3 scripts/run-phase11-e2e.py --scenario partial-observability
~~~

For another compatible Compose runtime:

~~~bash
CONTAINER_RUNTIME=podman python3 scripts/run-phase11-e2e.py
~~~

## Safety

The suite stops only local Compose services. It does not remove volumes. Every destructive availability action is paired with restoration in a `finally` block.

The monitored `postgres` service may be stopped. The diagnostic `blast-radius-db` is never intentionally stopped because incident persistence must remain available.

## Pass criteria

The suite passes only when:

- a healthy system creates no new incident;
- each outage is assigned to the expected origin;
- observed propagation does not exceed the dependency graph;
- a payment-service failure does not invent downstream impact;
- severity is present and deterministic;
- repeated detection keeps one active UUID;
- recovery resolves the same UUID;
- partial telemetry is represented as incomplete;
- partial telemetry cannot automatically resolve an incident;
- recovery resumes after telemetry is restored.

The E2E suite validates deterministic behavior. AI diagnosis remains an optional advisory smoke test and is deliberately excluded from acceptance.

## Full Stage 1–2 regression order

Required result: Stage 1: 6/6; Stage 2: 4/4; TOTAL: 10/10 PASS. Record actual branch results separately; a historical pass does not validate new changes.

| Stage | Runner | Scenarios |
|---|---|---|
| 1 | `run-phase11-e2e.py` | Six cases above, including healthy baseline |
| 2 | `run-stage2-e2e.py --scenario all` | HTTP 500, intermittent 500, latency, database connectivity |

Stage 1 validates Hard Failure / Blast Radius Detection, including guarded recovery with partial observability. Stage 2 validates Degraded-But-Running Detection. Both require real telemetry; keep their assertions and evidence requirements unchanged.

Run each command only after the previous suite passes. Stop and investigate any failure; do not weaken assertions.

```bash
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
python3 scripts/run-phase11-e2e.py
sleep 10
```

Stage 1 deliberately interrupts Tempo. Before Stage 2, confirm Tempo `/ready`, Collector health on port 13133 and fresh traces through Tempo `/api/search`. Check recent Collector logs for new `failed`, `dropping`, `no more retries` or `no such host` errors. If export has not recovered, restart only `tempo` and `otel-collector`, then wait for fresh trace evidence and a quiet Collector log interval. A running container alone does not prove trace export recovered.

```bash
# Only if trace export needs recovery:
docker compose restart tempo otel-collector
# Confirm fresh traces and stable export before continuing.
python3 scripts/run-stage2-e2e.py --scenario all
docker compose ps
curl -s 'http://localhost:8080/api/v1/blast-radius/incidents?status=ACTIVE'
docker compose logs --since 1m otel-collector
```

After recovery, no unexpected incident should remain ACTIVE and expected services should be running/healthy where checks exist. Errors caused by deliberate observability interruption may exist historically; inspect fresh errors after recovery. Gemini is optional and excluded from deterministic acceptance.

## Two-stage cleanup verification

The cleanup keeps the two runners above byte-for-byte unchanged. Only the retired
runners, their seven exclusive synthetic change/distributed fault modes and unit
tests, their lab-specific signal mappings, and obsolete dashboard stage entries
were removed. Repository usage searches found those modes only in the removed
runners, the mock fault implementation/tests and the dedicated signal mappings.
Generic error-log processing remains in place.

IncidentLifecycleService, IncidentLifecycleMonitor, incident history, recovery,
partial-observability handling, telemetry coverage, confidence and optional
containment assessment remain intact. No package was moved or deleted; the
capability-first tree remains in [PROJECT_STRUCTURE.md](../PROJECT_STRUCTURE.md).
No runtime directory or configuration was removed, and no empty directory or
obsolete placeholder resulted from the deletions.

The rebuilt API passed 195 tests and document-service passed 12 tests through
Maven `clean verify`. The frontend build and Compose configuration check passed.
Stage 1 passed 6/6 on the rebuilt stack. The first complete Stage 2 run passed
3/4: database connectivity was detected, but its snapshot had no trace signals
at the assertion time. Its later persisted snapshot contained 28 trace signals
and full telemetry coverage; Collector logs showed no export failures. This is
evidence of an intermittent enrichment-timing issue, not a relaxed pass. The complete
unchanged Stage 2 rerun passed 4/4, including 42 trace signals in the database
connectivity case. Both suites retain all assertions. No code, runtime settings,
thresholds or runner timings were changed between attempts.

Final result on 6 October 2026: **Stage 1: 6/6; Stage 2: 4/4; TOTAL: 10/10 PASS**.
All 12 Compose services are running and all configured health checks are healthy.
Tempo and Collector readiness return HTTP 200, fresh traces are available, fault
mode is NONE and no ACTIVE incidents remain. The final minute of Collector logs
contains no export errors. `git diff --check` passed, and the repository-wide
reference search found no retired-stage references or obsolete total.

Local execution logs: `/tmp/blast-two-stage-build.log`,
`/tmp/blast-two-stage-stage1.log`, `/tmp/blast-two-stage-stage2.log` (first attempt),
and `/tmp/blast-two-stage-stage2-retry.log` (complete passing rerun). These are
local session artifacts, not runtime inputs.

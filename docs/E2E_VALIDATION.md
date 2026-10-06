# Stage 1–4 Local Validation

Stage 1–4 runners are the acceptance layer for the local Blast Radius Engine. It proves that the deterministic engine behaves correctly under different failure shapes before enterprise MadlangaAI integration.

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

## Full Stage 1–4 regression order

The completed baseline result is Stage 1: 6 / 6, Stage 2: 4 / 4, Stage 3: 4 / 4 and Stage 4: 4 / 4; total 18 / 18. Record new branch results separately.

| Stage | Runner | Scenarios |
|---|---|---|
| 1 | `run-phase11-e2e.py` | Six cases above, including healthy baseline |
| 2 | `run-stage2-e2e.py --scenario all` | HTTP 500, intermittent 500, latency, database connectivity |
| 3 | `run-stage3-e2e.py --scenario all` | Deployment regression, configuration error, API contract break, feature-flag regression |
| 4 | `run-stage4-e2e.py --scenario all` | Distributed cascade, compound dependency failure, flapping dependency, partial observability |

Stage 3 uses reversible synthetic change faults and stored change markers. Stage 4 proves upstream observed impact and blocks recovery while traces are missing. These local scenarios do not implement real deployment rollback, general multi-origin analysis or network partition control.

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
sleep 10
python3 scripts/run-stage3-e2e.py --scenario all
sleep 10
python3 scripts/run-stage4-e2e.py --scenario all
docker compose ps
curl -s 'http://localhost:8080/api/v1/blast-radius/incidents?status=ACTIVE'
docker compose logs --since 1m otel-collector
```

After recovery, no unexpected incident should remain ACTIVE and expected services should be running/healthy where checks exist. Errors caused by deliberate observability interruption may exist historically; inspect fresh errors after recovery. Gemini is optional and excluded from deterministic acceptance.

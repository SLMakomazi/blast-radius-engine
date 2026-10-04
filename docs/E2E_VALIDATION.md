# Phase 11 End-to-End Validation

Phase 11 is the acceptance layer for the local Blast Radius Engine. It proves that the deterministic engine behaves correctly under different failure shapes before enterprise MadlangaAI integration.

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

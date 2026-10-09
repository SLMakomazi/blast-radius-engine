# WIL Evaluation Reminder System → MadlangaAI (local verification)

## Current state

The existing MadlangaAI Collector receives OTLP/HTTP traces at `otel-collector:4318/v1/traces` **inside Docker**, applies privacy filtering, and exports to Tempo at `tempo:4318`. The Spring backend reads Tempo at `http://tempo:3200`. This branch additionally publishes the Collector receiver to the **host loopback only** at `http://127.0.0.1:4318/v1/traces`. It does not make a public ingress or connect Render.

## Validate locally

From the blast-radius-engine repository root:

```bash
docker compose config --quiet
docker compose up -d otel-collector tempo
docker compose ps otel-collector tempo
curl -fsS http://127.0.0.1:13133/
python3 scripts/smoke-external-otlp.py
```

A PASS proves that a **synthetic** WIL-named trace can travel through the Collector and be queried from Tempo by trace ID. It does **not** prove WIL's Render backend is sending data, or that MadlangaAI's incident detection supports multi-application onboarding.

To inspect the Collector/Tempo if the smoke test fails:

```bash
docker compose logs --tail=100 otel-collector tempo
curl -i http://127.0.0.1:3200/ready
```

## Connecting the WIL backend

The WIL Express backend has `backend/tracing.js` and optional OpenTelemetry auto-instrumentation. When running WIL **locally on the same Mac** (outside Docker), its backend can export to `http://127.0.0.1:4318/v1/traces` with `OTEL_ENABLED=true` after installing backend dependencies and verifying its lockfile. The local loopback URL is allowed by WIL's tracing URL validation. Use only synthetic data for this development test.

For WIL deployed on **Render**, **do not** set the loopback URL: `localhost` inside Render refers to Render, not the Mac. Leave `OTEL_ENABLED=false` until there is a secured public HTTPS OTLP gateway with backend authentication, per-application identity, rate limits, traffic limits and appropriate retention. A bare Cloudflare Tunnel to port 4318 is not an acceptable production ingress.

Do not put OTLP credentials in React environment variables. Avoid real student/mentor data in traces; verify privacy filtering and storage before using live data.

## What remains before blast-radius analysis

1. Verify the local synthetic trace in Tempo.
2. Confirm WIL backend lockfile, startup and actual spans for Express/Mongoose in a controlled environment.
3. Add authenticated remote ingestion for Render; never publish the raw Collector.
4. Add application registration, topology identity mapping and health/metrics adapters for WIL. The existing engine is a single-application synthetic lab, so receiving WIL spans alone will **not** make WIL appear as an independent monitored application.
5. Validate MongoDB dependency traces, safe health probes and incident detection with controlled failures.

# Blast Radius Frontend

Phase 10 local incident dashboard for the MadlangaAI Blast Radius Engine.

## Run locally

```bash
cd frontend
cp -n .env.example .env
npm install
npm run dev
```

Open the Vite URL printed in the terminal. The default API base URL is `http://localhost:8080`.

## Current vertical slice

- ACTIVE / RESOLVED incident history;
- persisted incident detail;
- origin, severity, observed impact and propagation depth KPIs;
- deterministic impact path;
- telemetry coverage;
- failure experiment containment;
- on-demand advisory AI diagnosis through the Phase 9 endpoint.

The frontend consumes sanitized persisted analysis only. It does not calculate blast radius, invent evidence, or mutate production infrastructure.

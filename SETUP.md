# Setup

Copy `.env.example` to `.env` and fill in the required database, volume and monitoring settings. Keep `.env` private.

```bash
docker compose config --quiet
docker compose up -d --build
```

Compose runs PostgreSQL, the Spring Boot API and the React/Vite frontend. The backend builds with Java 21 and the frontend with Node 22 to match the project requirements.

The frontend defaults to localhost:5173 and the API to localhost:517380. Configure their bind addresses and ports in `.env` for hosting. PostgreSQL is accessible inside the Compose network. Use your hosting ingress for HTTPS.

An empty `VITE_API_BASE_URL` uses the frontend's same-origin `/api/` proxy. A supplied value is embedded at build time and must be reachable from the user's browser; rebuild the frontend when changing it.

Spring Boot receives standard `SPRING_DATASOURCE_*` variables. Flyway applies all migrations at API startup, including V4 for topology. Incidents and topology persist in the PostgreSQL volume. Keep `POSTGRES_VOLUME_NAME` stable across deployments and back up the database. Previously saved topology JSON files are not imported automatically.

Topology refresh is disabled until your new telemetry integration is ready.

```bash
mvn -f blast-radius-api/pom.xml clean verify
node --test frontend/src/incident-visuals.test.js
npm --prefix frontend run build
docker compose logs -f backend-api
docker compose down
```

`docker compose down` retains the database volume. `docker compose down -v` removes it. PostgreSQL integration tests use a disposable database through `CI_PG_URL`, `CI_PG_USER` and `CI_PG_PASSWORD`.

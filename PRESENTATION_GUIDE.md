# Manager Presentation Guide

Use this as a short speaking guide while opening the repository. The goal is to explain the system from connection -> topology -> telemetry -> analysis -> incident -> dashboard -> validation without reading code line by line.

## 1. Start with the whole system

**Open:** `README.md`

**Say:** “This is the MadlangaAI Blast Radius Engine. It combines dependency topology with real runtime telemetry to identify where a failure likely started, what could be affected, what was actually affected and how severe the incident is. AI is optional and explains the deterministic result; it does not calculate the blast radius.”

## 2. Show how the lab is wired

**Open:** `docker-compose.yml`

**Say:** “Compose gives us a complete local enterprise-style lab: payment calls customer, customer calls document, document calls PostgreSQL. Alongside that we run the Blast Radius API, its own incident database, the dashboard and the observability stack.”

**Point out:** `payment-service`, `customer-service`, `document-service`, `postgres`, `blast-radius-db`, `otel-collector`, `loki`, `prometheus`, `tempo`, `blast-radius-api`, `frontend`.

## 3. Show where traffic starts

**Open:** `traffic-generator/traffic.py`

**Say:** “This continuously sends requests through the whole synthetic chain. That means when I break or degrade something, the services naturally generate logs, metrics and traces rather than me inserting fake evidence directly into Blast Radius.”

## 4. Walk the application dependency chain

**Open in order:**
- `mock-services/payment-service/.../PaymentService.java`
- `mock-services/payment-service/.../client/CustomerClient.java`
- `mock-services/customer-service/.../CustomerService.java`
- `mock-services/customer-service/.../client/DocumentClient.java`
- `mock-services/document-service/.../DocumentService.java`
- `mock-services/document-service/.../persistence/DocumentRepository.java`

**Say:** “These files create the runtime path payment -> customer -> document -> PostgreSQL. The HTTP calls and database call are what later appear as dependency relationships in distributed traces.”

## 5. Show observability collection

**Open:** `infrastructure/observability/otel/collector.yml`

**Say:** “The OpenTelemetry Collector is the telemetry routing point. The services produce telemetry and the collector sends the relevant signals to our observability backends.”

Then briefly open:
- `infrastructure/observability/logging/loki.yml` — “Loki stores/query logs.”
- `infrastructure/observability/prometheus/prometheus.yml` — “Prometheus gives us metrics.”
- `infrastructure/observability/tracing/tempo.yml` — “Tempo gives us distributed traces.”

## 6. Explain the provider boundary

**Open:** `blast-radius-api/.../telemetry/application/port/TelemetryProvider.java`

**Say:** “The domain does not know about Loki, Tempo or Prometheus. It asks this provider-neutral port for evidence. That is important because an enterprise deployment can replace the local adapters without rewriting blast-radius logic.”

Then open `telemetry/infrastructure/LocalTelemetryProvider.java`.

**Say:** “This local implementation combines logs, metrics, traces and health into one normalized telemetry bundle.”

## 7. Show each telemetry adapter

Open briefly:
- `telemetry/infrastructure/loki/LokiLogAdapter.java`
- `telemetry/infrastructure/prometheus/PrometheusMetricsAdapter.java`
- `telemetry/infrastructure/tempo/TempoTraceAdapter.java`
- `telemetry/infrastructure/health/ActuatorHealthAdapter.java`

**Say:** “Each adapter speaks to one technical source and converts vendor-specific responses into our own domain evidence. After this boundary, the rest of the engine works with normalized evidence rather than vendor payloads.”

## 8. Explain topology

**Open:** `topology/application/port/DependencyTopologyProvider.java`

**Say:** “Topology tells us what could be affected if a component fails. This port keeps the deterministic engine independent of where that topology came from.”

Open `topology/infrastructure/TraceDiscoveredTopologyProvider.java`.

**Say:** “Locally we can discover dependencies from runtime spans. Calls observed in Tempo become directed component relationships.”

Open `topology/domain/DeterministicGraphEngine.java`.

**Say:** “This is the deterministic graph traversal. Starting from an origin, it walks the dependency graph and calculates theoretical blast radius and propagation depth. No AI is used here.”

## 9. Show the central analysis

**Open:** `incident/service/IncidentAnalysisService.java`

**Say:** “This is the main orchestration point. It brings topology and telemetry together, assesses the likely origin, compares theoretical impact with observed evidence, calculates propagation and builds the final incident analysis.”

Open `incident/model/IncidentSeverityService.java`.

**Say:** “Severity is also deterministic. We calculate it from the incident evidence and impact instead of asking an LLM to guess a severity.”

## 10. Explain theoretical vs observed impact

**Open:** `incident/model/ComponentImpact.java` and `incident/model/ComponentImpact.State.java`.

**Say:** “A component can be inside the theoretical blast radius without actually showing failure evidence. This separation prevents us from claiming every reachable dependency definitely failed.”

## 11. Show proactive monitoring

**Open:** `lifecycle/infrastructure/IncidentLifecycleMonitor.java`

**Say:** “This scheduler is what makes the engine proactive. It evaluates the configured application/environment repeatedly rather than waiting for somebody to manually report an incident.”

Open `lifecycle/application/IncidentLifecycleService.java`.

**Say:** “This service keeps one incident active while the same problem continues and only resolves it after guarded healthy windows. It avoids creating a new incident every monitoring cycle.”

## 12. Show persistence

**Open:** `incident/repository/IncidentRepository.java`, then `incident/repository/JdbcIncidentRepository.java`.

**Say:** “Application code depends on the repository contract. JDBC is the local implementation that stores the incident in the separate Blast Radius database.”

Open `blast-radius-api/src/main/resources/db/migration/V1__create_incidents.sql` and `V2__enforce_single_active_incident.sql`.

**Say:** “Flyway owns the diagnostic schema and the second migration protects our single-active-incident rule at database level.”

## 13. Show the API and dashboard

**Open:** `incident/controller/IncidentHistoryController.java`.

**Say:** “The frontend reads persisted incident state through this API. The UI does not calculate blast radius; it presents the engine's result.”

Open `frontend/src/main.jsx`.

**Say:** “This is the local dashboard used to inspect incidents and request an optional AI explanation.”

## 14. Explain AI's boundary

**Open:** `diagnosis/service/DiagnosisService.java` and `diagnosis/provider/DiagnosisProvider.java`.

**Say:** “AI starts only after deterministic analysis. It receives a bounded diagnosis context and returns advice.”

Open `diagnosis/provider/GeminiDiagnosisProvider.java`.

**Say:** “Gemini is one infrastructure adapter. If the provider changes, the core blast-radius model does not need to change.”

Open `shared/sanitization/TelemetrySanitizer.java`.

**Say:** “Before evidence is exposed to diagnosis, sensitive telemetry is sanitized using shared redaction rules.”

## 15. Demonstrate Stage 1

**Open:** `scripts/run-phase11-e2e.py`

**Say:** “Stage 1 proves hard-failure detection. We actually stop PostgreSQL or one of the services and then verify that the engine detects the origin, respects dependency direction, keeps a stable incident and recovers correctly.”

**Expected:** 6/6.

## 16. Demonstrate Stage 2

**Open:** `scripts/run-stage2-e2e.py`

**Say:** “Stage 2 is harder because the service stays running. We inject HTTP errors, intermittent errors, latency or database-connectivity degradation into document-service and require the engine to detect it from runtime evidence.”

Then open `mock-services/document-service/.../fault/FaultInjectionService.java`.

**Say:** “The fault is synthetic, but the resulting service behavior and telemetry are real. We are not inserting fake logs or traces into Blast Radius.”

**Expected:** 4/4.

## 17. Explain how a real Momentum/MadlangaAI application connects

Return to:
- `topology/application/port/DependencyTopologyProvider.java`
- `telemetry/application/port/TelemetryProvider.java`

**Say:** “To connect another system, we need its canonical dependency topology and its telemetry mapped into these two boundaries. The local Loki/Prometheus/Tempo setup proves the engine; enterprise adapters can supply the same contracts from the approved Momentum/MadlangaAI sources.”

## 18. Close

**Open:** `ARCHITECTURE.md`

**Say:** “The repository is capability-first: incident, topology, telemetry, diagnosis, lifecycle and shared. Each capability uses only as much internal layering as its complexity needs. The important design rule is that infrastructure can change while deterministic domain rules remain stable.”

**Final line:** “The proof is 10 scenarios: six hard-failure scenarios and four degraded-but-running scenarios. Topology tells us what could be affected, runtime telemetry tells us what was actually affected, and AI only explains the sanitized deterministic result.”

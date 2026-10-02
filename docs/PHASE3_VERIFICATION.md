# Phase 3 Verification — Full Observability

**Original Podman acceptance: PASSED on 2026-10-02.** Real logs, metrics, traces and health were retrieved for healthy, PostgreSQL outage and recovery requests. Collector loss preserved business functionality; fresh evidence was retrieved after export recovered. No analysis/normalization/AI implementation was added.

Branch: `feat/full-observability-stack`. The Phase 3 implementation was committed and pushed before the final documentation review. The original Podman acceptance and the final Docker recovery verification are both recorded below.

## Architecture and exact versions

```text
traffic-generator -> payment:8081 -> customer:8082 -> document:8083 -> postgres:5432
                       | Java agent OTLP logs/traces (all three services)
                       v
                 otel-collector:4318 -> tempo:4318 (traces)
                                    -> loki:3100/otlp (logs)
API + three services /actuator/prometheus -> Prometheus (5-second DNS scrape)
API + three services /actuator/health/* -> direct verification probes
```

| Component | Exact image / instrumentation version | Host exposure |
| --- | --- | --- |
| Collector | `docker.io/otel/opentelemetry-collector-contrib:0.157.0` | 127.0.0.1:13133 health |
| Prometheus | `docker.io/prom/prometheus:v3.14.0` | 127.0.0.1:9090 API |
| Tempo | `docker.io/grafana/tempo:2.10.7` | 127.0.0.1:3200 API |
| Loki | `docker.io/grafana/loki:3.7.8` | 127.0.0.1:3100 API |
| PostgreSQL | `docker.io/library/postgres:17.6` | 127.0.0.1:5432 |
| Java agent | `2.31.1`, SHA-256 verified in three Dockerfiles | No host ingestion port |

All five infrastructure images were inspected and report `arm64`; no amd64 override. Collector 0.162.0 returned manifest-not-found in the queried official registries, so the runnable 0.157.0 pin was used and its configuration validated at startup. Tempo 2.x remains a small monolithic local-storage backend.

Application images/container names remain `blast-radius-api:latest` / `blast-radius-api`, `payment-service:latest` / `payment-service`, `customer-service:latest` / `customer-service`, `document-service:latest` / `document-service`, `traffic-generator:latest` / `traffic-generator`. Infrastructure container names match Compose service names. Canonical DNS comes from service names, not container names. API/mock host ports remain loopback 8080/8081/8082/8083; traffic has none. OTLP 4318 is internal only.

## Commands and test results

Executed from the repository root with JDK 21 and `/opt/podman/bin` available:

```bash
mvn -f blast-radius-api/pom.xml clean verify
mvn -f mock-services/payment-service/pom.xml clean verify
mvn -f mock-services/customer-service/pom.xml clean verify
mvn -f mock-services/document-service/pom.xml clean verify
python3 -m unittest discover -s traffic-generator -v
podman compose config --quiet
podman compose build
podman compose up -d
podman compose ps
./scripts/verify-observability.sh --output /tmp/blast-radius-phase3
python3 scripts/verify-phase2.py
podman stats --no-stream
podman machine ssh 'free -m; uptime; df -h /'
git diff --check
```

| Suite | Tests | Failures / errors / skipped | Result |
| --- | ---: | --- | --- |
| Blast Radius API | 1 | 0 / 0 / 0 | BUILD SUCCESS |
| Payment | 17 | 0 / 0 / 0 | BUILD SUCCESS |
| Customer | 12 | 0 / 0 / 0 | BUILD SUCCESS |
| Document (context + database failure) | 6 | 0 / 0 / 0 | BUILD SUCCESS |
| Python traffic | 4 | 0 / 0 / 0 | OK |

Total: **36 Java + 4 Python tests passed**. Maven tests also passed inside all four OCI image builds. Mock-service tests exercise input validation, real RestClient request construction against mocked HTTP transport, correlation and error propagation, H2/Flyway persistence/failure, plus Prometheus exposure and sensitive-endpoint exclusion. The real container experiment separately verifies Java agent/JDBC/PostgreSQL compatibility.

Compose config and build passed; startup brought up all ten services. Business dependencies remain health ordered. Collector/Tempo/Loki use externally queried health/readiness APIs because their upstream minimal images do not contain a health-check shell. Prometheus/application/database health is checked as well. No command required Docker Desktop, a Docker socket mount, changing VM resources, or forcing a different architecture.

## Controlled transactions

IDs below are actual outputs of the final successful Phase 3 script. HTTP success persisted exactly one row; the failed request persisted zero after recovery. Counter deltas include concurrent synthetic traffic and independently timed scrapes; they are not attributed exclusively to the controlled transaction.

| Phase / correlation ID | Trace ID | Spans / DB spans / error spans | Payment HTTP / rows |
| --- | --- | --- | --- |
| `phase3-healthy-0f4b3544fe` | `61e1c07146fcb6829b35fca26be213c3` | 8 / 3 / 0 | 201 / 1 |
| `phase3-postgres-failure-0f4b3544fe` | `731882770abb404e23370995a8d92c50` | 21 / 16 / 21 | 502 / 0 |
| `phase3-recovery-0f4b3544fe` | `4e74e1069201ae632a87c7ac7ce41b3c` | 8 / 3 / 0 | 201 / 1 |
| `phase3-observability-restored-0f4b3544fe` | `4bc8b2513f8b8324956edb6e76a903c4` | 8 / 3 / 0 | 201 / receipt |

The recovery requests used the same running applications; no image build or application restart occurred between healthy, outage, recovery or Collector restoration.

## Retrieved centralized log evidence

For each ID, Loki `/loki/api/v1/query_range` was queried using `{service_name=~"payment-service|customer-service|document-service"} | correlation_id="<ID>"`. All three service names were required. Records included nanosecond timestamps, `severity_text`, `service_name`, `deployment_environment_name=local`, `correlation_id`, `trace_id` and `span_id`.

| Transaction | Service | Actual log message |
| --- | --- | --- |
| Healthy | payment-service | `event=request_succeeded dependency=customer-service` |
| Healthy | customer-service | `event=request_succeeded dependency=document-service` |
| Healthy | document-service | `event=document_inserted dependency=postgres documentId=fa9f0655-a201-4923-aadb-a9654871f7ce` |
| Failure | payment-service | `event=dependency_failed dependency=customer-service downstreamStatus=502` |
| Failure | customer-service | `event=dependency_failed dependency=document-service downstreamStatus=503` |
| Failure | document-service | `event=dependency_failed dependency=postgres code=DATABASE_UNAVAILABLE` |
| Recovery | payment-service | `event=request_succeeded dependency=customer-service` |
| Recovery | customer-service | `event=request_succeeded dependency=document-service` |
| Recovery | document-service | `event=document_inserted dependency=postgres documentId=23f58bc1-9f4d-4f71-a309-29716b69eed7` |

Loki `/loki/api/v1/labels` returned exactly `deployment_environment_name` and `service_name`. Correlation/trace/span IDs were structured metadata, not index labels.

## Retrieved distributed trace evidence

Trace IDs were extracted from the correlated Loki records and retrieved as JSON from Tempo `/api/traces/<traceId>`. The verifier follows parent IDs and checks customer has a payment ancestor and document has a customer ancestor. Healthy trace spans:

| Service | Span | Span ID | Parent span ID |
| --- | --- | --- | --- |
| payment-service | `POST` | `cf700669a96110ce` | `4a627d450e97f6bf` |
| payment-service | `POST /api/payments` | `4a627d450e97f6bf` | `(root)` |
| customer-service | `POST /api/customers/validate` | `58aab8c1d21212c6` | `cf700669a96110ce` |
| customer-service | `POST` | `41c503f305ad0e47` | `58aab8c1d21212c6` |
| document-service | `HikariDataSource.getConnection` | `3820d268b6e6121f` | `a08f368e31e4992c` |
| document-service | `INSERT synthetic_lab.synthetic_documents` | `069c7fb20b8ab97f` | `a08f368e31e4992c` |
| document-service | `SELECT synthetic_lab.synthetic_documents` | `3ee9ee48cdd01fda` | `a08f368e31e4992c` |
| document-service | `POST /api/documents` | `a08f368e31e4992c` | `41c503f305ad0e47` |

The failed trace contained **21 ERROR spans**, including all three HTTP servers, both HTTP clients and **16 JDBC/pool connection spans**. The pool made multiple internal connection attempts while PostgreSQL was unavailable; this is Hikari connection acquisition/replenishment, not a newly added business retry policy. Exception types were retained; messages/stack traces and SQL parameters/text were removed at the Collector. Recovery returned eight spans with INSERT/SELECT and no error status.

Correlation ID is a preserved business value; the independent 32-hex trace ID comes from standard W3C instrumentation. The same trace ID crosses the two HTTP hops automatically. No correlation ID is used as a trace ID or metric label.

## Retrieved Prometheus evidence

All four `up{job="applications"}` targets were 1 before experiments. Each controlled request advanced `http_server_requests_seconds_count` for the matching route/status in all three services.

| Phase | Payment status / before → after | Customer status / before → after | Document status / before → after |
| --- | --- | --- | --- |
| Healthy | 201 / 167 → 170 | 200 / 168 → 171 | 201 / 169 → 172 |
| Failure | 502 / 14 → 16 | 502 / 14 → 16 | 503 / 14 → 16 |
| Recovery | 201 / 173 → 175 | 200 / 173 → 175 | 201 / 173 → 177 |

Also retrieved nonempty HTTP latency sums/histograms, downstream `http_client_requests_seconds_count`, and document `hikaricp_connections_active`. JVM/process metrics were exposed. Examples from the failure snapshot:

```text
http_server_requests_seconds_sum{"environment": "local", "service": "document-service", "status": "503", "uri": "/api/documents"} = 113.999168415
http_server_requests_seconds_sum{"environment": "local", "service": "customer-service", "status": "502", "uri": "/api/customers/validate"} = 108.56131253
http_server_requests_seconds_sum{"environment": "local", "service": "payment-service", "status": "502", "uri": "/api/payments"} = 115.095449312
http_client_requests_seconds_count{"client_name": "document-service", "environment": "local", "service": "customer-service", "status": "503", "uri": "/api/documents"} = 16
http_client_requests_seconds_count{"client_name": "customer-service", "environment": "local", "service": "payment-service", "status": "502", "uri": "/api/customers/validate"} = 15
hikaricp_connections_active{"environment": "local", "pool": "HikariPool-1", "service": "document-service"} = 0
```

No Blast Radius PromQL or zero-traffic classification was implemented. Counters/timestamps remain available for later traffic/error-rate/latency/zero-traffic evaluation. `up` is scrape availability, not proof of database or whole-chain readiness.

## Health evidence

| Observation | Healthy | PostgreSQL unavailable | Recovery |
| --- | --- | --- | --- |
| API `/actuator/health` | 200 UP | 200 UP | 200 UP |
| Document readiness | 200 UP | 503 DOWN | 200 UP |
| Document liveness | 200 UP | 200 UP | 200 UP |
| Payment/customer readiness and liveness | 200 UP | 200 UP | 200 UP |

Direct endpoint observations are saved with the evidence capture timestamp/environment. Health endpoints were not coupled to the Collector. `/actuator/env`, `/actuator/configprops` and `/actuator/heapdump` returned 404 for all four Spring applications.

## Partial observability and restoration

Stopped `otel-collector`; transaction `phase3-partial-0f4b3544fe` returned **201** and persisted **one row**. Prometheus request counters advanced and all health probes remained UP. Loki returned zero matching logs and Tempo returned **404** for the caller-supplied independent W3C trace ID `2f389d2b145f459f81522a72239dd9a6`. Collector health was unreachable.

Restarted the Collector without rebuilding or restarting applications. The script sends fresh, uniquely identified synthetic probes until all three agent log exports are observed, then checks a new transaction across all four evidence families. Final run used these actual probes:

```json
[
  {
    "correlationId": "phase3-export-probe-0f4b3544fe-0",
    "httpStatus": 201,
    "logServices": [
      "customer-service",
      "document-service",
      "payment-service"
    ]
  }
]
```

The final restoration transaction `phase3-observability-restored-0f4b3544fe` had logs from all three services and trace `4bc8b2513f8b8324956edb6e76a903c4` with eight spans, three DB spans and no errors. Metrics advanced and health stayed UP.

Earlier verification exposed a real reconnection gap: Collector health recovered before all agent exporters reconnected, and a request immediately after restart had only document logs. That earlier run correctly failed instead of inventing complete evidence. Agent queues/export are bounded and asynchronous, so telemetry during or immediately after an outage may be delayed or lost. No durable replay guarantee is claimed. The final script waits for observed exporter recovery, not just backend process readiness.

## Resource observations

Existing Podman Machine: 3 CPUs, 3,608 MiB RAM, no swap, 43 GiB disk. No settings changed. A post-experiment snapshot reported:

```text
total        used        free      shared  buff/cache   available
Mem:            3608        1972         127          15        1697        1636
Swap:              0           0           0
 05:04:19 up  6:46,  4 users,  load average: 4.10, 8.18, 12.17
Filesystem      Size  Used Avail Use% Mounted on
/dev/vda4        43G  6.7G   36G  16% /
```

| Container | Measured memory (Podman decimal MB) | Configured limit (MiB) |
| --- | ---: | ---: |
| prometheus | 51.02MB | 256 |
| loki | 82.52MB | 256 |
| tempo | 122MB | 256 |
| otel-collector | 39.24MB | 192 |
| blast-radius-api | 166MB | 256 |
| postgres | 27.83MB | 192 |
| document-service | 284.9MB | 448 |
| customer-service | 270.4MB | 448 |
| payment-service | 266.8MB | 448 |
| traffic-generator | 16.79MB | 64 |

Container total in this snapshot: **1,327.5 MB (~1.24 GiB)**. Configured limits total **2,816 MiB**. VM available memory was **1,636 MiB**; disk use **6.7 GiB / 43 GiB**. Inspected container states had `OOMKilled=false` and no unexpected restarts. Memory stayed within the available machine allocation during the demonstrated low-throughput experiment. CPU load was transiently elevated (earlier 1-minute load 8.05, later 4.10); this is not a load/soak-test capacity claim.

## Privacy, limitations and verification corrections

- All requests/data were synthetic. Actual configured DB password and synthetic authorization/password-header canaries were checked in retrieved evidence and were absent. Exception stack traces, credential-bearing connection-string fields and request-body fields were absent.
- Collector retains only selected service/environment/span attributes and controlled application logger scopes. SQL text/parameters, headers and request bodies are not exported. This is a local data-minimization boundary, not a general-purpose PII detector.
- Framework diagnostics remain local container logs. Loki is an application-log evidence store here, not a socket-based archive of all container output. PostgreSQL server metrics/logs and Python tracing are not separately collected; document JDBC spans/pool metrics/readiness supply DB-boundary evidence.
- Backend APIs bind to host loopback and are unauthenticated local-lab APIs. Internal communication uses Compose DNS.
- Prometheus block retention is 2 hours/128 MB, Tempo 1 hour, Loki 24 hours plus compaction/deletion delay. Active/WAL data can exceed retention targets. Telemetry storage is disposable on container recreation; PostgreSQL keeps its named volume.
- An initial health probe timed out under load; Python 3.9 socket timeout and restart-time connection reset handling were corrected in bounded health polls. No business timeout/retry behavior was changed.
- First plain `py_compile` encountered the macOS cache sandbox; rerunning with `PYTHONPYCACHEPREFIX=/tmp/p3-pycache` passed. No remaining command is blocked.
- The final acceptance run exited 0. Raw backend JSON and `summary.json` are available locally under `/tmp/blast-radius-phase3`; this report preserves IDs and selected observations after short-retention backends expire.

## Bounded telemetry availability probe

The continuation replaces the fixed log-only exporter wait with a verification-only
four-family probe in `scripts/verify-observability.py`. Timeout and poll interval are
configurable (`--availability-timeout`, `--poll-interval`). One monotonic deadline
bounds network calls and sleeps. `--availability-only` exits nonzero on timeout and
writes the missing evidence families; `--availability-cases` limits execution to
Collector timeout/recovery. Container control operations have a separate 30-second
limit and restore the Collector in `finally`.

Each candidate uses fresh UUID-based correlation and W3C trace IDs. Logs must cover
all three services with the matching trace ID; trace parentage must connect the
chain and include JDBC evidence. Metrics must advance beyond the candidate's
baseline and have scrape timestamps after it was sent. Health is queried live.
Historical telemetry cannot satisfy a candidate. If reconnect loses a candidate's
telemetry, a later candidate is a new synthetic transaction, not a business retry.
A DOWN health observation or error span is still available telemetry; business
success is asserted separately by the focused experiment. No Java availability API,
TelemetryBundle or Phase 4 classification was implemented.

During the initial continuation, the old Podman VM and all lab endpoints became
unresponsive. A standalone 8-second probe terminated after **8.056 seconds**, with
`telemetryAvailable=false`, all four families missing and no claimed business
success. This was an environment-wide failure, not proof of Collector-only business
continuity. Stalled stop commands were cancelled and a 30-second restore attempt
timed out. The user authorized recovery, then explicitly requested switching to
Docker. Podman Desktop was closed and its VM stopped; its disk, images and volumes
were preserved. No VM resource settings were changed.

Docker Desktop 29.7.2 was started with the `desktop-linux` context. Its separate
image store had none of the five application images, requiring builds from the
existing Dockerfiles and downloads of the unchanged pinned infrastructure images.
The probe accepts `--runtime docker`; the Compose/business configuration is unchanged.
The resource measurements above belong to the earlier successful Podman run.

## Final Docker telemetry-availability verification

After the runtime switch, all ten services were brought up unchanged under Docker
Compose. A baseline payment using correlation ID
`manual-before-collector-test-001` returned HTTP **201** and persisted the document.

The Collector was then stopped while the business services, PostgreSQL, Prometheus
and direct health endpoints remained running. Payment
`manual-collector-down-001` still returned HTTP **201** and persisted successfully,
proving that business traffic does not wait on observability.

A bounded availability-only probe was run with an 8-second timeout and 1-second poll
interval. It returned:

```json
{
  "telemetryAvailable": false,
  "timedOut": true,
  "families": {
    "logs": false,
    "traces": false,
    "metrics": true,
    "health": true
  },
  "missingEvidenceFamilies": ["logs", "traces"],
  "elapsedSeconds": 8.0
}
```

This is the intended partial-observability result: missing logs/traces are reported
as missing and are never interpreted as proof that dependencies are healthy.

The Collector was restarted and the availability probe was invoked immediately,
without an artificial settle delay. A fresh synthetic transaction returned HTTP
**201** and fresh evidence was observed from all four families:

```json
{
  "telemetryAvailable": true,
  "timedOut": false,
  "families": {
    "logs": true,
    "traces": true,
    "metrics": true,
    "health": true
  },
  "missingEvidenceFamilies": [],
  "elapsedSeconds": 2.455
}
```

This closes the Phase 3 exporter-reconnection gap. Collector/container readiness is
not used as a substitute for end-to-end telemetry availability. Business traffic
never waits for telemetry; only verification and future analysis-side consumers may
perform a bounded wait for fresh required evidence.

## Phase 2 regression retained during continuation

The previously launched `python3 scripts/verify-phase2.py` completed successfully;
its existing `/tmp/p3-phase2-regression.log` was reused, not rerun.

- `phase2-healthy-db56186635a5`: HTTP 201, one persisted document.
- `phase2-failure-db56186635a5`: document database failure 503; customer observed 503
  and returned 502; payment observed 502 and returned 502. Document readiness DOWN,
  liveness UP, independent API UP. Correlated logs identified each dependency.
- `phase2-recovery-db56186635a5`: HTTP 201, one persisted document; failed request
  rows zero and original healthy row retained.
- Final script result: `Phase 2 healthy -> PostgreSQL failure -> recovery verification PASSED`.

## Deliberately deferred to Phase 4 and later

Provider-neutral TelemetryBundle ingestion/models, coverage classification, provider adapters and normalized evidence persistence remain deferred. Graph traversal/impact/root-cause/severity algorithms, Datadog/MadlangaAI integration, AI/remediation, chaos orchestration and UI/Grafana are not implemented. Original source-derived planning documents and Phase 2 historical evidence are preserved.

## Exact file inventory

### Created

```text
docs/PHASE3_VERIFICATION.md
infrastructure/observability/logging/loki.yml
infrastructure/observability/otel/collector.yml
infrastructure/observability/otel/javaagent.properties
infrastructure/observability/prometheus/prometheus.yml
infrastructure/observability/tracing/tempo.yml
scripts/verify-observability.py
scripts/verify-observability.sh
```

### Modified

```text
CODEX.md
README.md
blast-radius-api/pom.xml
blast-radius-api/src/main/resources/application.yml
docker-compose.yml
docs/DECISIONS.md
docs/IMPLEMENTATION_PLAN.md
docs/LOCAL_LAB.md
docs/TEST_STRATEGY.md
mock-services/customer-service/Dockerfile
mock-services/customer-service/pom.xml
mock-services/customer-service/src/main/java/com/madlanga/lab/customer/config/HttpClientConfig.java
mock-services/customer-service/src/main/resources/application.yml
mock-services/customer-service/src/test/java/com/madlanga/lab/customer/CustomerApplicationTests.java
mock-services/document-service/Dockerfile
mock-services/document-service/pom.xml
mock-services/document-service/src/main/resources/application.yml
mock-services/document-service/src/test/java/com/madlanga/lab/document/DocumentApplicationTests.java
mock-services/payment-service/Dockerfile
mock-services/payment-service/pom.xml
mock-services/payment-service/src/main/java/com/madlanga/lab/payment/config/HttpClientConfig.java
mock-services/payment-service/src/main/resources/application.yml
mock-services/payment-service/src/test/java/com/madlanga/lab/payment/PaymentApplicationTests.java
scripts/verify-phase2.py
```

### Removed redundant empty-directory markers

```text
infrastructure/observability/logging/.gitkeep
infrastructure/observability/otel/.gitkeep
infrastructure/observability/prometheus/.gitkeep
infrastructure/observability/tracing/.gitkeep
```

## Observability component tree

```text
infrastructure/observability/
├── otel/
│   ├── collector.yml
│   └── javaagent.properties
├── prometheus/prometheus.yml
├── tracing/tempo.yml
└── logging/loki.yml
scripts/
├── verify-phase2.py
├── verify-observability.py
└── verify-observability.sh
docs/PHASE3_VERIFICATION.md
```

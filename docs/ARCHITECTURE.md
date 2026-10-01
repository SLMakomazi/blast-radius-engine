# Architecture

## Design principle
**Topology calculates potential impact; full telemetry proves observed impact; AI explains sanitized evidence.**

## Technology-neutral topology
The Blast Radius graph is a generic component graph, not a microservice-only graph.

At minimum a node can represent:
`SERVICE`, `API`, `DATABASE`, `MESSAGE_BROKER`, `QUEUE`, `TOPIC`, `SERVERLESS_FUNCTION`, `WORKFLOW`, `FRONTEND`, `EXTERNAL_SYSTEM`, or `UNKNOWN`.

Technology metadata is separate from node type, e.g. `POSTGRESQL`, `MONGODB`, `ORACLE`, `DB2`, `SQL_SERVER`, `IBM_MQ`, `AWS_SQS`, `AWS_SNS`, `ACTIVEMQ`, `AWS_LAMBDA`, `AWS_STEP_FUNCTIONS`, `SPRING_BOOT`, `NODE_JS`, `ANGULAR`.

The model must allow new types/technologies without changing graph traversal.

## Logical architecture
```text
Dependency Topology                Telemetry Providers
(any supported technology)        logs metrics traces health
          |                                  |
          |                         Sanitization
          +---------------+------------------+
                          |
                 Incident Correlation
                          |
                 Origin Assessment
                          |
                 Blast Radius Engine
                  /              \
        Theoretical Impact    Observed Impact
                  \              /
                    Evidence
                       |
             Propagation Timeline
                       |
             Severity / Confidence
                       |
              Diagnosis Context
                       |
              AI Diagnosis (optional)
                       |
              REST/UI/MadlangaAI
```

## Local Docker lab
The first executable vertical slice intentionally uses:
`traffic-generator -> payment-service -> customer-service -> document-service -> postgres`.

This validates the engine; it does not define the domain boundary. PostgreSQL can later be replaced/augmented by MongoDB or another supported dependency through topology/telemetry adapters without rewriting core graph logic.

All services emit logs, metrics, OTLP traces and health signals. A failure driver injects controlled failures.

## Package boundaries
```text
com.madlanga.blastradius
├── api
├── application
│   ├── BlastRadiusAnalysisService
│   ├── IncidentCorrelationService
│   ├── PropagationTimelineService
│   └── DiagnosisContextService
├── domain
│   ├── model
│   ├── graph
│   ├── correlation
│   ├── severity
│   └── evidence
├── ports
│   ├── TelemetryProvider
│   ├── DependencyTopologyProvider
│   ├── FailureExperimentProvider
│   └── AiDiagnosisProvider
└── adapters
    ├── local
    ├── datadog
    ├── madlanga
    └── ai
```

## Responsibilities
- **TelemetryProvider** — normalized full/partial telemetry and coverage metadata.
- **Sanitization** — redacts before evidence enters persistence/logs/exports/AI.
- **DependencyTopologyProvider** — technology-neutral directed nodes/edges. For `A -> B`, A depends on B.
- **IncidentCorrelationService** — correlates logs, metrics, traces and health.
- **OriginAssessment** — ranks suspected origins from evidence/chronology.
- **BlastRadiusEngine** — pure graph logic; paths, distance and theoretical impact.
- **ObservedImpactEvaluator** — runtime-evidence validation; reachability alone is insufficient.
- **PropagationTimelineService** — evidence-based incident chronology.
- **SeverityCalculator** — deterministic/configurable; separate from MadlangaAI health score.
- **FailureExperimentProvider** — optional controlled-failure metadata.
- **AiDiagnosisProvider** — sanitized structured context only.

## MadlangaAI integration
Reuse MadlangaAI topology, Datadog/MCP and AI capabilities where they satisfy Blast Radius contracts. Add adapters/providers for missing evidence. Never couple the core to one database, language, messaging platform, AWS component or telemetry vendor.

## Resilience
Partial telemetry produces warnings rather than fabricated evidence; missing traces/logs do not prevent analysis when other evidence is sufficient; AI failure does not fail core analysis; cycles terminate; unknown components are surfaced; provider timeouts are isolated.

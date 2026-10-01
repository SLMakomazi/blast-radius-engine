# Architecture

## Design principle
**Topology calculates potential impact; full telemetry proves observed impact; AI explains sanitized evidence.**

## Logical architecture
```text
 Sources
 ┌──────────────────────┐      ┌─────────────────────────┐
 │ Dependency Topology  │      │ Telemetry Providers     │
 │ mock / MadlangaAI    │      │ logs metrics traces     │
 └──────────┬───────────┘      │ endpoint health         │
            │                  └───────────┬─────────────┘
            │                              │
            │                    ┌─────────▼─────────┐
            │                    │ Sanitization     │
            │                    │ PII/secrets      │
            │                    └─────────┬─────────┘
            └──────────────────────┬───────┘
                                   ▼
                         Incident Correlation
                                   │
                         Origin Assessment
                                   │
                         Blast Radius Engine
                         /                 \
               Theoretical Impact     Observed Impact
                         \                 /
                           Evidence Model
                                   │
                         Propagation Timeline
                                   │
                         Severity / Confidence
                                   │
                         Diagnosis Context
                                   │
                         AI Diagnosis (optional)
                                   │
                         REST/UI/MadlangaAI
```

## Local Docker lab
```text
traffic-generator
      |
payment-service
      |
customer-service
      |
document-service
      |
postgres

All services emit:
  logs ----------> local log backend
  metrics -------> Prometheus-compatible backend
  OTLP traces ---> OpenTelemetry Collector ---> trace backend
  health --------> health collector/probes

failure-driver ---> postgres/services
                         |
                         v
                 blast-radius-engine
                 reads normalized evidence
```

The exact local observability products may be selected during implementation, but adapters must hide product-specific schemas. Prefer lightweight, Docker-friendly components and OpenTelemetry-compatible instrumentation.

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
### TelemetryProvider
Returns normalized full/partial telemetry plus coverage metadata.

### Sanitization
Runs before evidence enters domain persistence, logs, exports or AI context.

### DependencyTopologyProvider
Returns nodes and directed edges. For `A -> B`, A depends on B. Failure propagation analysis traverses reverse dependents from B.

### IncidentCorrelationService
Correlates logs, metrics, traces and health observations into an evidence set.

### OriginAssessment
Ranks suspected origins using explicit evidence and chronology. Known local failure events can provide ground truth for test validation without replacing normal inference tests.

### BlastRadiusEngine
Pure graph/domain logic. Calculates paths, hop distance and theoretical impact.

### ObservedImpactEvaluator
Uses actual runtime evidence. Reachability alone never proves observed impact.

### PropagationTimelineService
Orders origin/degradation evidence to explain how failure moved across components.

### SeverityCalculator
Deterministic/configurable and independent from MadlangaAI Overall Health Score.

### FailureExperimentProvider
Supplies optional controlled-failure metadata so expected vs observed vs unexpected impact and containment can be evaluated.

### AiDiagnosisProvider
Receives only sanitized structured context. AI unavailability must not break deterministic results.

## MadlangaAI integration
Reuse MadlangaAI topology, Datadog/MCP and AI capabilities where they satisfy the Blast Radius contracts. Add adapters/providers where MadlangaAI lacks required logs/traces or other evidence.

## Resilience
- Partial telemetry produces warnings/coverage, not fabricated evidence.
- Missing traces do not prevent logs/metrics/health analysis.
- Missing logs do not prevent trace/metric/health analysis.
- AI failure does not fail core analysis.
- Cycles terminate safely.
- Unknown components are surfaced.
- Provider timeouts are isolated.

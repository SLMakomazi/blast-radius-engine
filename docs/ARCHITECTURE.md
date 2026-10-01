# Architecture

## Design principle
**Dependency topology calculates potential blast radius; telemetry validates observed blast radius; AI explains the evidence.**

## Logical architecture

```text
                +--------------------------+
                | MadlangaAI / Future Host |
                +------------+-------------+
                             |
        +--------------------+--------------------+
        |                                         |
 DependencyTopologyProvider                 TelemetryProvider
        |                                         |
        |                          +--------------+--------------+
        |                          |              |              |
        |                        Logs           Metrics         Traces
        |                          |              |              |
        +--------------------------+--------------+--------------+
                                   |
                          IncidentCorrelation
                                   |
                          OriginAssessment
                                   |
                         BlastRadiusEngine
                         /               \
              Theoretical Impact     Observed Impact
                         \               /
                          Evidence Model
                               |
                       DiagnosisContext
                               |
                    AiDiagnosisProvider
                               |
                        API / MadlangaAI
```

## Proposed Spring Boot modules/packages

```text
com.madlanga.blastradius
├── api
│   ├── BlastRadiusController
│   └── dto
├── application
│   ├── BlastRadiusAnalysisService
│   ├── IncidentCorrelationService
│   └── DiagnosisContextService
├── domain
│   ├── model
│   ├── graph
│   ├── severity
│   └── evidence
├── ports
│   ├── TelemetryProvider
│   ├── DependencyTopologyProvider
│   └── AiDiagnosisProvider
└── adapters
    ├── mock
    ├── datadog       # later
    ├── madlanga      # later
    └── ai            # later
```

This is ports-and-adapters/hexagonal in spirit: business rules do not import Datadog-specific classes.

## Core components

### TelemetryProvider
Returns normalized telemetry for an application/environment/time window.

### DependencyTopologyProvider
Returns nodes and directed dependency edges. For edge `A -> B`, A depends on B. If B fails, traversal follows reverse dependencies to discover potential consumers affected by B.

### IncidentCorrelationService
Groups relevant telemetry and creates an incident evidence set.

### OriginAssessment
Selects a suspected origin using explicit rules/evidence. The first PoC may use known/injected origin hints plus timestamp/error evidence; later implementations can become more sophisticated.

### BlastRadiusEngine
Pure domain logic. Traverses reverse dependencies and assigns hop distance. It must be testable with no Spring context.

### ObservedImpactEvaluator
For theoretically impacted nodes, checks telemetry for actual error/degradation evidence. A component is not "observed affected" simply because it is reachable in the graph.

### SeverityCalculator
Deterministic/configurable. AI must not be the source of the severity classification.

### AiDiagnosisProvider
Optional boundary. Receives sanitized structured diagnosis context and returns explanatory text/recommendations. Core blast-radius analysis remains functional if AI is unavailable.

## Integration path into MadlangaAI
PoC:
`MockTelemetryProvider + MockDependencyTopologyProvider`

MadlangaAI:
`Datadog/MCP Telemetry Adapter + MadlangaAI Architecture/Dependency Adapter`

The domain/application services remain unchanged.

## Resilience
- Provider failures should return explicit partial-data warnings.
- AI failure must not fail deterministic blast-radius calculation.
- Missing traces must not prevent log/metric analysis.
- Cycles in the dependency graph must not cause infinite traversal.
- Unknown components should be reported rather than silently discarded.

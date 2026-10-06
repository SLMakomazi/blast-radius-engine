# About the MadlangaAI Blast Radius Engine

## What is it?

The **Blast Radius Engine** is a proactive incident-analysis capability being developed for MadlangaAI Phase 4.

It is intended to answer:
- What is failing?
- Where did the failure most likely start?
- What components could be affected because they depend on it?
- Which components are actually showing failure evidence?
- How far has the failure propagated?
- How severe is the incident?
- What evidence supports the conclusion?
- What should an engineer investigate next?

The engine detects failures from telemetry rather than waiting for a user to report that an application is broken.

## Core principle

> **Dependency topology calculates potential blast radius; runtime telemetry proves observed blast radius; AI explains sanitized evidence.**

Topology tells the engine what **could** be affected. Logs, metrics, distributed traces and health tell it what is **actually** affected. AI is advisory after deterministic analysis.

## How it works

~~~text
Monitored application
        |
        +---- logs ------> telemetry adapter
        +---- metrics ---> telemetry adapter
        +---- traces ----> telemetry adapter
        +---- health ----> telemetry adapter
        |
        v
sanitized normalized evidence
        |
        +------ dependency topology
        |              |
        +------+-------+
               v
     deterministic analysis
               |
     origin / theoretical impact
     observed impact / propagation
     severity / confidence
               |
       incident lifecycle
               |
       diagnostic database
               |
          REST + dashboard
               |
       optional AI diagnosis
~~~

The local proof uses Loki, Prometheus, Tempo and Actuator as telemetry sources.

## Proactive lifecycle

The engine continuously evaluates a recent telemetry window. A user does not need to press Analyze first.

~~~text
healthy
   |
failure evidence appears
   |
automatic telemetry evaluation
   |
origin + impact calculated
   |
ACTIVE incident created/updated
   |
dashboard displays incident
   |
service recovers
   |
multiple healthy windows confirmed
   |
same incident becomes RESOLVED
~~~

Multiple healthy windows are required so one successful request cannot immediately close an incident.

## Analysis concepts

**Suspected origin** is the component with the strongest correlated failure evidence, with confidence and supporting evidence.

**Theoretical impact** is calculated from dependency topology. It describes components that could be affected.

**Observed impact** requires runtime evidence. Reachability alone is not enough.

**Unexpected impact** is observed failure evidence outside the expected topology and can indicate an incomplete graph or unexpected propagation.

**Propagation** records paths, hop distance and evidence chronology.

**Severity** is deterministic and separate from the MadlangaAI Overall Health Score.

**Telemetry coverage** records whether logs, metrics, traces and health were available. Missing telemetry is a data gap, never proof of health.

## AI boundary

AI is optional and on-demand. It receives sanitized deterministic diagnosis context and can explain probable cause and remediation.

AI does not determine topology, invent affected services, create blast-radius facts, autonomously modify production systems, or replace proactive deterministic detection.

## Technology-neutral design

The local proof uses Spring Boot and PostgreSQL, but the core is not PostgreSQL-specific or microservice-only.

Nodes can represent services, APIs, databases, brokers, queues, topics, serverless functions, workflows, frontends and external systems. Technology remains metadata, allowing new platforms without rewriting graph traversal.

## How another system plugs in

A monitored application integrates through two primary contracts.

### DependencyTopologyProvider

Blast Radius needs stable components and directed dependencies. For A -> B, A depends on B.

A provider supplies:
- application ID;
- environment;
- canonical component IDs;
- component types;
- technology metadata;
- dependency edges.

MadlangaAI can eventually provide this from architecture/dependency analysis. Runtime trace discovery, service catalogs or approved platform metadata can supplement it.

### TelemetryProvider

Blast Radius needs runtime evidence mapped to the same canonical component IDs:
- logs;
- metrics;
- distributed traces;
- health/availability;
- timestamps;
- provenance;
- coverage.

The local provider reads Loki, Prometheus, Tempo and Actuator. An enterprise adapter could instead read Datadog/MCP and other approved sources while producing the same normalized evidence contract.

The deterministic engine should not care which telemetry vendor supplied the evidence.

## How MadlangaAI could onboard applications

A practical target flow is:

~~~text
                 MadlangaAI
                     |
            register application
                     |
        +------------+-------------+
        |                          |
 topology/architecture       telemetry sources
        |                          |
        v                          v
DependencyTopologyProvider   TelemetryProvider
        |                          |
        +-------------+------------+
                      |
               canonical IDs
                      |
              Blast Radius Engine
                      |
        proactive lifecycle monitor
                      |
            incidents + evidence
                      |
             MadlangaAI UI/API
~~~

Conceptually, each application/environment would have a monitoring profile such as:

~~~yaml
applicationId: madlanga-ai
environment: qa

topology:
  provider: madlanga-architecture

telemetry:
  provider: datadog-mcp
  logs: enabled
  metrics: enabled
  traces: enabled
  health: enabled
~~~

This YAML is an **illustrative target**, not a currently implemented MadlangaAI configuration contract.

## Scaling to multiple monitored systems

The enterprise target is not one hardcoded local application:

~~~text
MadlangaAI
   |
   +-- Application A / DEV
   +-- Application A / QA
   +-- Application A / PROD
   +-- Application B / QA
   +-- Application B / PROD
   +-- Application C / PROD
              |
              v
       monitoring profiles
              |
              v
       Blast Radius Engine
~~~

The current Phase 10 lifecycle scheduler is configured for the local document-platform/local proof. General application registration and multi-application scheduling belong to the MadlangaAI integration phase.

## Identity is critical

Topology and telemetry must refer to the same component identities.

If topology calls something customer-service, traces call it customer-api-v2, metrics call it customers and health calls it cust, the engine needs an explicit identity mapping before it can correlate those sources reliably.

Real onboarding therefore needs canonical application IDs, environment IDs, component IDs and telemetry mappings.

## Security

Telemetry is sanitized before persistence, display, export or AI use. Enterprise integration must also apply approved authentication, RBAC, audit, retention and POPIA controls.

## Implemented locally vs integration work

Implemented locally:
- logs, metrics, traces and health adapters;
- topology/runtime dependency knowledge;
- deterministic origin assessment;
- theoretical vs observed impact;
- evidence correlation and propagation;
- deterministic severity;
- incident persistence/history;
- proactive detection;
- guarded automatic recovery;
- dashboard;
- optional AI diagnosis.

Still to integrate with MadlangaAI:
- final MadlangaAI topology schema;
- final Datadog/MCP telemetry APIs;
- enterprise authentication/RBAC/audit;
- application registration and multi-application scheduling;
- production persistence/retention policy;
- enterprise severity/criticality policy;
- MadlangaAI dashboard/report integration.

## Architectural goal

The stable core is:

~~~text
topology + normalized runtime evidence
                 |
                 v
     deterministic blast-radius analysis
~~~

Environment-specific and vendor-specific systems belong behind adapters. That is how MadlangaAI can add new monitored applications without rewriting the Blast Radius engine.

## Local validation status

Stages 1–4 are implemented. The completed baseline local validation passed Stage 1: 6 / 6, Stage 2: 4 / 4, Stage 3: 4 / 4 and Stage 4: 4 / 4 (18 / 18 total). Change-related and distributed scenarios use synthetic lab faults. Real change-system integration and enterprise onboarding still require the contracts listed above. See [validation](docs/E2E_VALIDATION.md) and [package guidance](docs/DEVELOPER_GUIDE.md).

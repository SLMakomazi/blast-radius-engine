# MadlangaAI Blast Radius Engine

Full-capability Blast Radius proof-of-concept for future integration into **MadlangaAI Phase 4**.

## Core principle
> **Dependency topology calculates potential blast radius; full runtime telemetry validates observed blast radius; AI explains sanitized evidence.**

The engine supports **logs, metrics, distributed traces and endpoint/application health**. Current MadlangaAI/Datadog requirements expose only part of that set, so missing capabilities are proven locally behind replaceable provider contracts rather than removed from the design.

## Local-first proof
The target repository runs a complete synthetic environment with Docker Compose:

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
```

The services emit logs, metrics, traces and health signals. A controlled failure driver can break PostgreSQL/services while traffic continues, allowing the engine to calculate theoretical vs observed impact and propagation.

## What Blast Radius returns
- suspected origin + confidence;
- direct/indirect theoretical impact;
- observed impact;
- dependency paths;
- propagation timeline;
- telemetry coverage/data gaps;
- evidence/provenance;
- deterministic incident severity;
- optional chaos containment assessment;
- sanitized AI diagnosis/remediation context.

## Documentation
Start with [CODEX.md](CODEX.md).

- [Source Alignment](docs/SOURCE_ALIGNMENT.md)
- [Product Requirements](docs/PRODUCT_REQUIREMENTS.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Blast Radius Specification](docs/BLAST_RADIUS_SPEC.md)
- [Telemetry Contract](docs/TELEMETRY_CONTRACT.md)
- [Local Docker Lab](docs/LOCAL_LAB.md)
- [MadlangaAI Integration](docs/INTEGRATION.md)
- [Implementation Plan](docs/IMPLEMENTATION_PLAN.md)
- [Test Strategy](docs/TEST_STRATEGY.md)
- [UI/UX Concept](docs/UI_UX_CONCEPT.md)
- [Architecture Decisions](docs/DECISIONS.md)

## Scope
This repository must prove all capabilities required by Blast Radius locally, even when the current MadlangaAI MVP does not yet expose the corresponding telemetry source. It does not perform autonomous production remediation or own production chaos orchestration.

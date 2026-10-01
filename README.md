# MadlangaAI Blast Radius Engine

Design and proof-of-concept repository for the **Blast Radius Analysis** capability intended for integration into MadlangaAI.

## Purpose

Given an application incident, the engine is designed to answer:

- What failed and where did the incident likely originate?
- Which components **could** be affected based on the dependency graph?
- Which components are **actually observed** as degraded from runtime telemetry?
- What evidence supports the assessment?
- What should an engineer investigate or remediate?

The core principle is:

> **Dependency topology calculates potential blast radius; telemetry validates observed blast radius; AI explains the evidence.**

## PoC approach

The first implementation should use synthetic, provider-neutral logs, metrics, traces and dependency topology. Real Datadog/MCP and MadlangaAI integrations are adapters added later.

Proposed PoC technology: **Java + Spring Boot**, pending confirmation of the final MadlangaAI integration constraints.

## Documentation

Start with [CODEX.md](CODEX.md). It is the instruction entry point for Codex and other coding agents.

Detailed design:

- [Product Requirements](docs/PRODUCT_REQUIREMENTS.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Blast Radius Specification](docs/BLAST_RADIUS_SPEC.md)
- [Telemetry Contract](docs/TELEMETRY_CONTRACT.md)
- [MadlangaAI Integration](docs/INTEGRATION.md)
- [Implementation Plan](docs/IMPLEMENTATION_PLAN.md)
- [Test Strategy](docs/TEST_STRATEGY.md)
- [Architecture Decisions](docs/DECISIONS.md)

## Canonical demo

```text
payment-service -> customer-service -> document-service -> postgres
```

A synthetic PostgreSQL failure propagates toward dependent services. The engine calculates theoretical impact from topology and then checks telemetry to determine observed impact.

## Scope boundary

This repository is **not** a replacement for MadlangaAI, Datadog, chaos engineering or the automated bug-fixing capability. It isolates the Blast Radius domain so the concept can be validated and later integrated cleanly into MadlangaAI.

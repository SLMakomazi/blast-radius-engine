# Blast Radius UI/UX Concept

## Direction
Use the supplied MadlangaAI pilot dashboards as visual inspiration, not as a screen to copy. Preserve the executive dashboard language—clear KPI cards, status colors, strong hierarchy—while making the dependency topology and evidence the center of Blast Radius.

## Main analysis screen
### Header/filter row
- application;
- environment;
- incident/time window;
- analysis status;
- telemetry coverage indicator.

### KPI cards
- suspected origin;
- incident severity;
- theoretical components;
- observed affected components;
- maximum propagation depth;
- confidence.

### Interactive topology
The graph is the primary visual.

Node states:
- origin;
- direct theoretical;
- indirect theoretical;
- observed affected;
- theoretical-only/healthy;
- unexpected impact;
- unknown/insufficient telemetry.

Edges should expose dependency direction and highlighted propagation paths.

## Evidence drawer
Selecting a node opens:
- component identity/type/criticality;
- direct/indirect/distance;
- path from origin;
- observed state;
- first degradation timestamp;
- log evidence;
- metric evidence;
- trace evidence;
- health evidence;
- provider/source references;
- data gaps;
- AI explanation for that component.

## Propagation timeline
Show chronological incident movement, for example:
```text
T+0s   postgres          HEALTH_DOWN
T+2s   document-service  DB_CONNECTION_ERROR
T+4s   document-service  ERROR_RATE_SPIKE
T+7s   customer-service  DOWNSTREAM_TIMEOUT
T+12s  payment-service   HEALTHY / theoretical only
```

The timeline must be derived from evidence timestamps, not generated as fiction by AI.

## Theoretical vs observed view
Provide a clear legend/filter so users can answer:
- What could have been affected?
- What actually degraded?
- What stayed healthy?
- What impact was unexpected?

## Telemetry coverage panel
Show:
- Logs: Available/Partial/Unavailable/Not Supported
- Metrics: ...
- Traces: ...
- Health: ...

A missing telemetry family must be visible to the user so confidence is understandable.

## Chaos/containment panel
When experiment metadata exists, show:
- injected target/failure;
- expected containment boundary;
- theoretical impact;
- observed impact;
- unexpected impact;
- containment held/breached.

## AI diagnosis/remediation
AI content is downstream of deterministic evidence.

Display:
- evidence-backed summary;
- likely-cause explanation with confidence language;
- Immediate (0–30 days);
- Medium-term (30–90 days);
- Strategic (90+ days);
- data gaps/limitations.

## Compliance
Never display raw secrets, credentials or unmasked PII. UI/export consumes sanitized evidence only.

## Export/integration
The same result model should support:
- local interactive UI;
- standalone HTML/report representation if required;
- MadlangaAI dashboard/report integration later.

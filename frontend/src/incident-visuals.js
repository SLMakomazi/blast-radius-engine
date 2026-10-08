// View-only interpretations of backend evidence; never infer health from silence.
export function analysisOf(incident) {
  const value = incident?.analysis || incident?.analysisSnapshot || {};
  if (typeof value !== 'string') return value;
  try { return JSON.parse(value); } catch { return {}; }
}

export function evidenceFor(impact, analysis) {
  const items = [...(impact.evidence || []), ...(analysis.timeline || []).filter(e => e.component === impact.component)];
  return [...new Map(items.map(e => [e.evidenceId || `${e.timestamp}/${e.family}/${e.signal}`, e])).values()];
}

export function explainSignal(signal = '') {
  const s = signal.toLowerCase();
  if (/availability health (down|out_of_service)|liveness health (down|out_of_service)|liveness unreachable/.test(s)) return 'The liveness check indicates this service is unavailable or unreachable.';
  if (/readiness health (down|out_of_service)|aggregate health (down|out_of_service)/.test(s)) return 'The service is not ready to serve normally. A failed dependency can cause this while the service process remains alive.';
  if (s.includes('database connectivity') || s.includes('connection timeout')) return 'Requests could not establish a database connection. A database outage or connection problem needs investigation.';
  if (s.includes('connection-pool')) return 'The service is waiting for free database connections, which can delay requests.';
  if (s.includes('process cpu')) return 'Processor usage is high enough to affect the service’s ability to handle requests.';
  if (/latency|slow span|timeout/.test(s)) return 'Requests are taking too long. Services that depend on these responses may slow down or time out.';
  if (/intermittent/.test(s)) return 'Some requests fail while others succeed; the service is still running but unreliable.';
  if (s.includes('dependency error')) return 'A caller reported a failed request to this dependency. This supports a dependency problem, but does not prove its process stopped.';
  if (/500|5xx|error span/.test(s)) return 'Requests are returning errors. The service may still be running even though some operations fail.';
  if (/error|fatal/.test(s)) return 'Error events were recorded here. Review the supporting evidence to establish the underlying cause.';
  return 'This is a recorded observation. The available evidence does not identify a specific underlying cause.';
}

export function nodeView(impact, incident) {
  const analysis = analysisOf(incident);
  const evidence = evidenceFor(impact, analysis);
  const signals = evidence.map(e => String(e.signal || '').toLowerCase());
  const resolved = incident.status === 'RESOLVED';
  let tone = 'unknown', label = 'Not enough evidence';
  if (resolved) { tone = 'recovered'; label = 'Resolved snapshot'; }
  else if (impact.state === 'UNKNOWN') { tone = 'unknown'; }
  else if (impact.state === 'THEORETICAL_ONLY') { tone = 'risk'; label = 'At risk · unconfirmed'; }
  else if (evidence.length) {
    const hardUnavailable = evidence.some(e => e.kind === "AVAILABILITY_UNAVAILABLE" && e.family === "HEALTH") || evidence.some(e => !e.kind && e.family === "HEALTH" && /^(availability health (down|out_of_service)|liveness health (down|out_of_service)|liveness unreachable)$/.test(String(e.signal || "").toLowerCase()));
    if (hardUnavailable) { tone = 'failed'; label = 'Unavailable'; }
    else { tone = 'degraded'; label = 'Observed impact'; }
  }
  const origin = impact.state === 'ORIGIN';
  if (!resolved && origin && evidence.length) {
    label = tone === 'failed' ? 'Failure origin' : 'Likely origin';
  }
  const path = impact.path || [];
  const dependency = path.length > 1 ? path[path.length - 2] : null;
  const availability = evidence.find(e => e.kind === 'AVAILABILITY_UNAVAILABLE');
  const reason = availability ? 'Confirmed unavailable — direct availability check failed.' : evidence.length ? explainSignal(evidence[0].signal) : 'No direct failure evidence is included for this service. Missing evidence is not proof that it is healthy.';
  const relationship = origin
    ? tone === "failed" ? "Confirmed unavailable — direct availability check failed." : `The engine selected ${impact.component} as the likely starting point, with ${incident.originConfidence || analysis.origin?.confidence || 'unknown'} confidence. The underlying physical cause has not been established.`
    : dependency
      ? `${impact.state === 'OBSERVED' ? 'Observed impact — failure or degradation evidence recorded. ' : 'Potential impact — dependency relationship exists, but no direct impact confirmed. '}${impact.component} depends on ${dependency} along this recorded path. If ${dependency} cannot respond normally, this service may fail or slow down too.${impact.state === 'THEORETICAL_ONLY' ? ' That impact has not been observed here.' : ''}`
      : 'The snapshot does not include a dependency path for this service. Do not assume it is connected to the selected origin.';
  return { ...impact, tone, label, evidence, reason, relationship, origin, resolved };
}

export function graphFor(incident) {
  const analysis = analysisOf(incident);
  const nodes = (analysis.impacts || []).map(i => nodeView(i, incident));
  const ids = new Set(nodes.map(n => n.component));
  const edges = new Map();
  for (const n of nodes) {
    for (let j = 1; j < (n.path || []).length; j++) {
      const from = n.path[j - 1], to = n.path[j];
      if (ids.has(from) && ids.has(to) && from !== to) edges.set(`${from}\u0000${to}`, { from, to });
    }
  }
  return { nodes, edges: [...edges.values()] };
}

export function matchesIncident(incident, query) {
  const a = analysisOf(incident);
  return [incident.id, incident.originComponent, incident.applicationId, incident.environment, incident.severityLevel,
    ...(a.impacts || []).map(i => i.component), ...(a.timeline || []).map(e => e.signal)]
    .join(' ').toLowerCase().includes(query.trim().toLowerCase());
}

export function evidenceBuckets(analysis, count = 12) {
  const events = (analysis.timeline || []).map(e => ({ ...e, at: Date.parse(e.timestamp) })).filter(e => Number.isFinite(e.at));
  if (!events.length) return [];
  const start = Math.min(...events.map(e => e.at));
  const end = Math.max(...events.map(e => e.at));
  const width = Math.max(1000, (end - start + 1) / count);
  const buckets = Array.from({ length: count }, (_, i) => ({ at: start + i * width, end: Math.min(end, start + (i + 1) * width), LOG: 0, METRIC: 0, TRACE: 0, HEALTH: 0, total: 0 }));
  for (const e of events) {
    const b = buckets[Math.min(count - 1, Math.floor((e.at - start) / width))];
    if (Object.hasOwn(b, e.family)) { b[e.family]++; b.total++; }
  }
  return buckets;
}

/** Classification comes from the backend; symptoms never determine the headline. */
export function incidentClassification(incident) {
  const analysis = analysisOf(incident);
  const type = incident?.incidentType || analysis.incidentType || 'UNCLASSIFIED';
  const availability = incident?.status === 'RESOLVED' ? 'RECOVERED' : incident?.availabilityStatus || analysis.availabilityStatus || 'UNKNOWN';
  return `${type} · ${availability}`;
}

/** Compare ISO 8601 UTC instants without losing PostgreSQL microsecond precision. */
export function compareIncidentTimestamps(a, b) {
  const parse = value => {
    const match = /^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,9}))?(Z|[+-]\\d{2}:\\d{2})$/.exec(value || '');
    if (!match) return null;
    const millis = Date.parse(match[1] + match[3]);
    if (!Number.isFinite(millis)) return null;
    const fraction = BigInt((match[2] || '').padEnd(9, '0'));
    return BigInt(millis) * 1000000n + fraction;
  };
  const left = parse(a), right = parse(b);
  if (left === null || right === null) return null;
  return left < right ? -1 : left > right ? 1 : 0;
}

/** Ignore stale requests after a click, preserving database timestamp precision. */
export function acceptIncidentDetail(previous, next, selectedId) {
  if (next.id !== selectedId) return previous;
  if (previous?.id === next.id && previous.updatedAt && next.updatedAt
      && compareIncidentTimestamps(next.updatedAt, previous.updatedAt) === -1) return previous;
  return JSON.stringify(previous) === JSON.stringify(next) ? previous : next;
}

/** Include the bounded direct recovery proof even when ordinary evidence collection is capped. */
export function incidentEvidenceTimeline(analysis = {}) {
  const evidence = [...(analysis.timeline || [])];
  if (analysis.lastRecoveryEvidence) evidence.push(analysis.lastRecoveryEvidence);
  const key = e => [e.timestamp, e.component, e.family, e.kind, e.provider, e.sourceRef, e.signal].join('\\u0000');
  return [...new Map(evidence.map(e => [key(e), e])).values()]
    .sort((a, b) => compareIncidentTimestamps(a.timestamp, b.timestamp) ?? 0);
}

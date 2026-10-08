import React, { useState } from 'react';
import { AlertTriangle, ArrowUpRight, CheckCircle2, Database, Search, Server, X } from 'lucide-react';
import { analysisOf, graphFor, evidenceBuckets, explainSignal } from './incident-visuals.js';

const names = { failed: 'Unavailable', degraded: 'Failure signals', risk: 'At risk', unknown: 'Unknown', recovered: 'Resolved' };

export function ServiceMap({ incident, onEvidence }) {
  const { nodes, edges } = graphFor(incident);
  const [query, setQuery] = useState('');
  const [pinned, setPinned] = useState(null);
  const [hovered, setHovered] = useState(null);
  const [copied, setCopied] = useState('');
  const active = nodes.find(n => n.component === (hovered || pinned)) || nodes.find(n => n.origin) || nodes[0];
  const q = query.trim().toLowerCase();
  const matches = nodes.filter(n => `${n.component} ${n.label} ${n.reason}`.toLowerCase().includes(q));
  const matchIds = new Set(matches.map(n => n.component));
  const levels = [...new Set(nodes.map(n => Number.isFinite(n.distance) ? n.distance : 'unmapped'))].sort((a,b) => a === 'unmapped' ? 1 : b === 'unmapped' ? -1 : a-b);
  const positions = new Map();
  let rows = 1;
  levels.forEach((level, col) => {
    const group = nodes.filter(n => (Number.isFinite(n.distance) ? n.distance : 'unmapped') === level);
    rows = Math.max(rows, group.length);
    group.forEach((n, row) => positions.set(n.component, { x: 24 + col * 240, y: 48 + row * 142 }));
  });
  const width = Math.max(510, levels.length * 240 + 24), height = rows * 142 + 72;
  const possible = nodes.filter(n => n.state === 'THEORETICAL_ONLY' || n.state === 'UNKNOWN');
  const resolved = incident.status === 'RESOLVED';
  async function copySummary() {
    const text = `${incident.applicationId} / ${incident.environment} — ${incident.status}\nLikely origin: ${incident.originComponent} (${incident.originConfidence} confidence).\n${active.component}: ${active.reason}\n${active.relationship}\nSnapshot: ${analysisOf(incident).to || incident.updatedAt || incident.startedAt}\nIncident: ${incident.id}`;
    try { await navigator.clipboard.writeText(text); setCopied('Summary copied. Ready to share with your team.'); }
    catch { setCopied('Copy unavailable in this browser. Select the explanation below to copy it manually.'); }
  }
  return <section className="panel service-map" id="service-map">
    <div className="panel-head"><div><span className="eyebrow">FOLLOW THE IMPACT</span><h2>What happened. What could happen next.</h2><p>Hover, focus, or select a service to understand its evidence.</p></div><span className="snapshot-badge">{resolved ? 'Historical · resolved' : 'Incident snapshot'}</span></div>
    <div className="map-toolbar"><label className="visual-search"><Search size={17}/><input value={query} onChange={e => setQuery(e.target.value)} placeholder="Find a service or symptom…" aria-label="Search service map"/>{query && <button onClick={() => setQuery('')} aria-label="Clear map search"><X size={15}/></button>}</label><span aria-live="polite">{q ? `${matches.length} of ${nodes.length} services match` : `${nodes.length} services · ${edges.length} recorded connections`}</span></div>
    <div className="map-legend">{Object.entries(names).filter(([key]) => resolved ? key === 'recovered' : key !== 'recovered').map(([key,label]) => <span key={key}><i className={key}/>{label}</span>)}<small>Arrows show possible failure propagation →</small></div>
    {!nodes.length ? <div className="empty">No service map was included in this incident.</div> : <>
      {q && !matches.length && <p className="map-no-results" role="status">No matching services. Clear the search to explore the full path.</p>}
      <div className="map-scroll" tabIndex={0} aria-label="Service impact map; scroll horizontally for longer paths">
        <div className="map-canvas" style={{ width, height }}>
          <svg width={width} height={height} className="map-connections" aria-label="Recorded dependency propagation paths" role="img">
            <defs><marker id="impact-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#6688a4"/></marker></defs>
            {edges.map(e => { const a = positions.get(e.from), b = positions.get(e.to); const lit = active?.path?.includes(e.from) && active?.path?.includes(e.to); return <path key={`${e.from}-${e.to}`} className={lit ? 'connection highlighted' : 'connection'} d={`M ${a.x+196} ${a.y+49} C ${a.x+224} ${a.y+49}, ${b.x-28} ${b.y+49}, ${b.x} ${b.y+49}`} markerEnd="url(#impact-arrow)"><title>{e.to} depends on {e.from}; potential impact flows toward {e.to}</title></path>; })}
          </svg>
          {levels.map((level, i) => <span className="map-level" key={level} style={{ left: 24+i*240 }}>{level === 0 ? 'OUTAGE ORIGIN' : level === 'unmapped' ? 'NO RECORDED PATH' : `${level} DEPENDENCY HOP${level === 1 ? '' : 'S'}`}</span>)}
          {nodes.map(n => <button key={n.component} style={{ left: positions.get(n.component).x, top: positions.get(n.component).y }} className={`service-node ${n.tone} ${active?.component === n.component ? 'inspecting' : ''} ${q && !matchIds.has(n.component) ? 'dimmed' : ''}`} onMouseEnter={() => setHovered(n.component)} onMouseLeave={() => setHovered(null)} onFocus={() => setHovered(n.component)} onBlur={() => setHovered(null)} onClick={() => { setPinned(n.component); setCopied(''); }} aria-pressed={pinned === n.component} aria-describedby="service-explanation" title={`${n.label}: ${n.reason}`}>
            <span className="service-node-top">{/postgres|database|\bdb\b/i.test(n.component) ? <Database size={19}/> : <Server size={19}/>}<span className="status-dot"/>{n.origin && <small>ORIGIN</small>}</span><strong>{n.component}</strong><span className="node-status">{n.label}</span><small>{n.evidence.length} supporting observations</small>
          </button>)}
        </div>
      </div>
      {active && <div className={`service-explanation ${active.tone}`} id="service-explanation">
        <div className="explanation-heading"><div><span className="eyebrow">{active.origin ? 'ORIGIN AVAILABILITY' : 'WHY THIS SERVICE MATTERS'}</span><h3>{active.component}</h3></div><span className={`node-status ${active.tone}`}>{active.label}</span></div>
        {resolved && <p className="historical-note"><CheckCircle2 size={16}/> This incident was resolved. The explanation below describes the earlier failure, not current service health.</p>}
        <div className="explanation-columns"><div><h4>What the evidence tells us</h4><p>{active.reason}</p></div><div><h4>{active.origin ? 'Why the engine selected it' : 'The dependency connection'}</h4><p>{active.relationship}</p></div></div>
        {active.path?.length > 1 && <div className="dependency-trail">Recorded impact path: {active.path.join(' → ')}</div>}
        {active.origin && !resolved && <p className="next-risk"><AlertTriangle size={16}/> {possible.length ? `${possible.map(n => n.component).join(', ')}: possible or uncertain impact. Investigate these services; additional outages are not confirmed.` : 'No additional services are classified as possible or uncertain impact in this snapshot.'}</p>}
        <div className="explanation-actions"><button onClick={() => onEvidence(active.component)}>Inspect supporting evidence <ArrowUpRight size={15}/></button><button onClick={copySummary}>Copy team handoff</button><span role="status">{copied}</span></div>
      </div>}
    </>}
    <p className="map-footnote">Snapshot: {analysisOf(incident).to ? new Date(analysisOf(incident).to).toLocaleString() : 'time unavailable'}. Pulsing green means potential impact, not verified health. Gray means uncertainty. A resolved incident is historical; this map does not claim live uptime.</p>
  </section>;
}

export function EvidenceCharts({ incident, onFamily }) {
  const analysis = analysisOf(incident), buckets = evidenceBuckets(analysis);
  const nodes = graphFor(incident).nodes;
  const counts = Object.fromEntries(Object.keys(names).map(t => [t, nodes.filter(n => n.tone === t).length]));
  const max = Math.max(1, ...buckets.map(b => b.total));
  const families = ['LOG','METRIC','TRACE','HEALTH'];
  return <section className="panel visual-charts">
    <div className="panel-head"><div><span className="eyebrow">THE INCIDENT AT A GLANCE</span><h2>Signals behind the story</h2><p>Recorded observations in this snapshot, not request volume or an uptime score.</p></div></div>
    <div className="chart-layout"><div className="scope-chart"><h3>Service impact</h3><div className="scope-bar" role="img" aria-label={Object.entries(counts).map(([k,v]) => `${names[k]}: ${v}`).join(', ')}>{Object.entries(counts).filter(([,v]) => v).map(([k,v]) => <span key={k} className={k} style={{ flex: v }} title={`${names[k]}: ${v}`}/>)}</div><div className="scope-labels">{Object.entries(counts).filter(([,v]) => v).map(([k,v]) => <span key={k}><i className={k}/><b>{v}</b> {names[k]}</span>)}</div>{!nodes.length && <p>No impact data recorded.</p>}</div>
    <div className="signal-chart"><h3>When evidence was recorded</h3>{!buckets.length ? <p>No timestamped evidence in this snapshot.</p> : <><div className="signal-bars" role="img" aria-label={`Evidence grouped into ${buckets.length} time intervals; peak ${max} observations. Counts are observations, not distinct failures.`}>{buckets.map((b,i) => <div key={i} className="signal-bar" title={`${new Date(b.at).toLocaleTimeString()}: ${b.total} observations`} aria-label={`${new Date(b.at).toLocaleTimeString()}: ${b.total} observations`}>{families.map(f => <span key={f} className={f.toLowerCase()} style={{ height: `${b[f]/max*90}px` }}/>)}</div>)}</div><div className="chart-axis"><span>{new Date(buckets[0].at).toLocaleTimeString()}</span><span>{new Date(buckets.at(-1).end).toLocaleTimeString()}</span></div></>}
    <div className="chart-family-legend">{families.map(f => <button key={f} onClick={() => onFamily(f)}><i className={f.toLowerCase()}/>{({LOG:'Error records',METRIC:'Measurements',TRACE:'Request journeys',HEALTH:'Health checks'})[f]}</button>)}</div></div></div>
  </section>;
}

export function SignalExplanation({ signal }) { return <><span>{explainSignal(signal)}</span><details><summary>Technical observation</summary><code>{signal}</code></details></>; }

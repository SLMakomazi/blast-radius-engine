import React, { useEffect, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  Activity, AlertTriangle, Bot, CheckCircle2, Clock3,
  Database, GitBranch, Waves, Search
} from "lucide-react";
import "./styles.css";
import { ServiceMap, EvidenceCharts, SignalExplanation } from "./ServiceMap.jsx";
import { analysisOf, matchesIncident, incidentClassification, acceptIncidentDetail } from "./incident-visuals.js";

const API = import.meta.env.VITE_API_BASE_URL ?? "";

const statusClass = (value = "") => value.toLowerCase().replaceAll("_", "-");

function chooseSelectedIncidentId(data, selectedId) {
  return selectedId; // Only an explicit click changes the selected incident.
}

function getImpactScope(impacts, involved) {
  if (impacts.length > 0 && involved === impacts.length) return "FULL CHAIN";
  if (involved > 0) return "PARTIAL";
  return "NONE";
}

function evidenceRowKey(e) {
  return `${e.timestamp}-${e.component}-${e.family}-${e.signal}-${e.provider || ""}`;
}

function App() {
  const [search, setSearch] = useState("");
  const [evidenceSearch, setEvidenceSearch] = useState("");
  const [status, setStatus] = useState("ACTIVE");
  const [incidents, setIncidents] = useState([]);
  const [selected, setSelected] = useState(null);
  const [diagnoses, setDiagnoses] = useState({});
  const selectedIdRef = useRef(null);
  const statusRef = useRef(status);
  const listGenerationRef = useRef(0);
  const latestListRequestRef = useRef(0);
  const [loading, setLoading] = useState(true);
  const [diagnosing, setDiagnosing] = useState(false);
  const [error, setError] = useState("");
  const [lastUpdated, setLastUpdated] = useState(null);
  const [evidenceComponent, setEvidenceComponent] = useState(null);
  const [evidenceFamily, setEvidenceFamily] = useState("ALL");

  async function loadIncidents(nextStatus = status, { background = false } = {}) {
    const generation = listGenerationRef.current;
    const request = ++latestListRequestRef.current;
    if (!background) setLoading(true);
    if (!background) setError("");
    try {
      const res = await fetch(`${API}/api/v1/blast-radius/incidents?status=${nextStatus}`);
      if (!res.ok) throw new Error(`Incident API returned HTTP ${res.status}`);
      const data = await res.json();
      if (generation !== listGenerationRef.current || request !== latestListRequestRef.current || nextStatus !== statusRef.current) return;
      setIncidents(previous => JSON.stringify(previous) === JSON.stringify(data) ? previous : data);
      setLastUpdated(new Date());

      const currentSelectedId = selectedIdRef.current;

      // Poll the same selected incident without remounting the map or resetting local state. It must not replace the
      // user's selection, reset map hover/focus, or touch an existing diagnosis.
      if (background) {
        if (currentSelectedId) await loadIncident(currentSelectedId, { background: true });
        return;
      }

      const nextSelectedId = chooseSelectedIncidentId(data, currentSelectedId);
      if (!nextSelectedId) {
        selectedIdRef.current = null;
        setSelected(null);
        return;
      }

      selectedIdRef.current = nextSelectedId;
      await loadIncident(nextSelectedId);
    } catch (e) {
      if (!background) setError(e.message);
    } finally {
      if (!background) setLoading(false);
    }
  }

  async function loadIncident(id, { background = false } = {}) {
    if (!background) setError("");
    try {
      const res = await fetch(`${API}/api/v1/blast-radius/incidents/${id}`);
      if (!res.ok) throw new Error(`Incident detail returned HTTP ${res.status}`);
      const next = await res.json();
      setSelected(prev => acceptIncidentDetail(prev, next, selectedIdRef.current));
    } catch (e) {
      if (!background) setError(e.message);
    }
  }

  async function runDiagnosis() {
    if (!selected) return;
    setDiagnosing(true);
    setError("");
    try {
      const res = await fetch(`${API}/api/v1/blast-radius/incidents/${selected.id}/diagnosis`, { method: "POST" });
      if (!res.ok) throw new Error(`Diagnosis API returned HTTP ${res.status}`);
      const result = await res.json();
      setDiagnoses(prev => ({ ...prev, [selected.id]: result }));
    } catch (e) {
      setError(e.message);
    } finally {
      setDiagnosing(false);
    }
  }

  useEffect(() => {
    selectedIdRef.current = selected?.id ?? null;
  }, [selected?.id]);


  useEffect(() => {
    statusRef.current = status;
    listGenerationRef.current += 1;
    void loadIncidents(status);
    const timer = window.setInterval(() => {
      void loadIncidents(status, { background: true });
    }, 5000);
    return () => window.clearInterval(timer);
  }, [status]);

  const analysis = analysisOf(selected);
  const diagnosis = selected ? diagnoses[selected.id] ?? null : null;
  const impacts = analysis.impacts || [];
  const coverage = analysis.coverage || {};
  const observed = impacts.filter(i => i.state === "OBSERVED").length;
  const maxDepth = impacts.reduce((m, i) => Math.max(m, i.distance ?? 0), 0);
  useEffect(() => { setEvidenceComponent(null); setEvidenceFamily("ALL"); setEvidenceSearch(""); }, [selected?.id]);
  const originCount = impacts.filter(i => i.state === "ORIGIN").length;
  const involved = originCount + observed;
  const impactPercent = impacts.length ? Math.round((involved / impacts.length) * 100) : 0;
  const impactScope = getImpactScope(impacts, involved);
  const timeline = analysis.timeline || [];
  const evidenceRows = timeline.filter(e =>
    (!evidenceComponent || e.component === evidenceComponent)
    && (evidenceFamily === "ALL" || e.family === evidenceFamily)
    && `${e.component} ${e.signal} ${e.family}`.toLowerCase().includes(evidenceSearch.trim().toLowerCase())
  );
  const evidenceCounts = ["LOG","METRIC","TRACE","HEALTH"].reduce((a,f) => ({...a,[f]: timeline.filter(e => e.family === f).length}), {});
  const visibleIncidents = incidents.filter(i => matchesIncident(i, search));
  const inspectEvidence = (component = null, family = "ALL") => {
    setEvidenceComponent(component); setEvidenceFamily(family); setEvidenceSearch("");
    document.getElementById("evidence-explorer")?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  return <div className="app">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><Waves size={22}/></div><div><strong>MadlangaAI</strong><span>Blast Radius</span></div></div>
      <nav>
        <div className="nav-label nav-label-first">ENGINE</div>
        <button onClick={() => document.getElementById("service-map")?.scrollIntoView({ behavior: "smooth" })} disabled={!selected}><GitBranch size={18}/> Service map</button>
        <button onClick={() => document.getElementById("ai-diagnosis")?.scrollIntoView({ behavior: "smooth" })} disabled={!selected}><Bot size={18}/> AI diagnosis</button>
      </nav>
      <div className="principle"><span>CORE PRINCIPLE</span><p>Topology calculates potential impact. Telemetry proves observed impact. AI explains sanitized evidence.</p></div>
    </aside>

    <main>
      <header>
        <div><span className="eyebrow">MADLANGAAI</span><h1>Blast Radius Analysis</h1><p>Understand the likely origin, observed impact, potential impact and supporting telemetry.</p></div>
        <div className="live-status"><span className={error ? "live-dot stale" : "live-dot"}></span><span>{error ? "Updates interrupted" : "Live incident monitoring"}</span></div>
      </header>

      {error && <div className="error"><AlertTriangle size={18}/>{error}</div>}

      <section className="workspace">
        <div className="incident-column panel">
          <div className="panel-head"><div><span className="eyebrow">INCIDENTS</span><h2>History</h2></div><div className="segmented">
            {["ACTIVE","RESOLVED"].map(s => <button key={s} className={status===s?"selected":""} onClick={()=>{ statusRef.current=s; listGenerationRef.current+=1; setStatus(s); }}>{s}</button>)}
          </div></div>
          <label className="visual-search incident-search"><Search size={16}/><input aria-label="Search incidents" placeholder="Search service, symptom, ID…" value={search} onChange={e => setSearch(e.target.value)}/></label>
          <div className="incident-list">
            {loading && !incidents.length && <div className="empty">Loading incidents…</div>}
            {!loading && !visibleIncidents.length && <div className="empty">{search ? "No incidents match your search." : `No ${status.toLowerCase()} incidents.`}</div>}
            {visibleIncidents.map(i => <button key={i.id} className={"incident-item "+(selected?.id===i.id?"current":"")} onClick={()=>{selectedIdRef.current=i.id;void loadIncident(i.id)}}>
              <div className="incident-top"><span className={"severity "+statusClass(i.severityLevel)}>{i.severityLevel}</span><span className={"state "+statusClass(i.status)}>{i.status}</span></div>
              <strong>{i.originComponent}</strong>
              {incidentClassification(i) && <span className="scenario-label">{incidentClassification(i)}</span>}
              <span>{i.applicationId} · {i.environment}</span>
              <small><Clock3 size={12}/>{new Date(i.startedAt).toLocaleString()}</small>
            </button>)}
          </div>
        </div>

        <div className="detail-column">
          {!selected ? <div className="panel empty large">Select an incident to inspect its deterministic evidence.</div> : <>
            <section className="incident-title panel">
              <div><span className="eyebrow">INCIDENT {selected.id.slice(0,8)}</span><h2>{selected.originComponent} <span>origin</span></h2><p>{incidentClassification(selected) || "Incident"} · {selected.applicationId} / {selected.environment}</p></div>
              <div className="incident-badges"><span className={"severity "+statusClass(selected.severityLevel)}>{selected.severityLevel} · {selected.severityScore}</span><span className={"state "+statusClass(selected.status)}>{selected.status}</span></div>
            </section>

            <section className="kpis">
              <Kpi icon={<Database/>} label="Origin" value={selected.originComponent} meta={selected.originConfidence+" confidence"}/>
              <Kpi icon={<Activity/>} label="Observed impact" value={observed} meta={impacts.length+" total components"}/>
              <Kpi icon={<GitBranch/>} label="Max depth" value={maxDepth} meta="dependency hops"/>
              <Kpi icon={<Waves/>} label="Impact scope" value={impactScope} meta={`${involved} / ${impacts.length} components involved`}/>
            </section>

            <ServiceMap key={selected.id} incident={selected} onEvidence={component => inspectEvidence(component)}/>
            <EvidenceCharts incident={selected} onFamily={family => inspectEvidence(null, family)}/>

            <div className="two-col">
              <section className="panel">
                <div className="panel-head"><div><span className="eyebrow">OBSERVABILITY</span><h2>Telemetry evidence</h2><p>Coverage plus the evidence captured for this incident window.</p></div>{coverage.fullyCovered && <CheckCircle2 className="ok"/>}</div>
                <div className="coverage-grid">{[["logs","LOG"],["metrics","METRIC"],["traces","TRACE"],["health","HEALTH"]].map(([k,f])=><button key={k} className="coverage-card" onClick={()=>{setEvidenceComponent(null);setEvidenceFamily(f)}}><span>{k}</span><strong className={statusClass(coverage[k])}>{coverage[k] || "UNKNOWN"}</strong><small>{evidenceCounts[f] || 0} evidence events</small></button>)}</div>
              </section>
              <section className="panel">
                <div className="panel-head"><div><span className="eyebrow">IMPACT ANALYSIS</span><h2>Blast radius scope</h2><p>Deterministic scope calculated from topology and correlated telemetry.</p></div></div>
                <div className="impact-summary">
                  <div><b>{impactScope}</b><small>scope</small></div>
                  <div><b>{observed}</b><small>observed dependants</small></div>
                  <div><b>{maxDepth}</b><small>dependency hops</small></div>
                  <div><b>{impactPercent}%</b><small>topology involved</small></div>
                  <div><b>{coverage.fullyCovered ? "FULL" : "PARTIAL"}</b><small>telemetry coverage</small></div>
                  <div><b>{selected.originConfidence || "UNKNOWN"}</b><small>origin confidence</small></div>
                </div>
              </section>
            </div>

            <section className="panel evidence-explorer" id="evidence-explorer">
              <div className="panel-head"><div><span className="eyebrow">EVIDENCE EXPLORER</span><h2>{evidenceComponent || "All components"}</h2><p>Sanitized incident evidence captured from logs, metrics, traces and health telemetry.</p></div><span className="evidence-total">{evidenceRows.length} events</span></div>
              <label className="visual-search evidence-search"><Search size={16}/><input aria-label="Search evidence" placeholder="Search evidence by service or symptom…" value={evidenceSearch} onChange={e => setEvidenceSearch(e.target.value)}/></label>
              <div className="evidence-tabs">{["ALL","LOG","METRIC","TRACE","HEALTH"].map(f=><button key={f} className={evidenceFamily===f?"selected":""} onClick={()=>setEvidenceFamily(f)}>{f==="ALL"?"All":f[0]+f.slice(1).toLowerCase()}</button>)}{evidenceComponent && <button onClick={()=>setEvidenceComponent(null)}>All components</button>}</div>
              {analysis.collectionLimited && <div className="error">Evidence collection limit reached. Earlier history is preserved; later observations may be absent.</div>}
              <div className="evidence-list">
                {!evidenceRows.length && <div className="empty">No evidence matches these filters in this incident snapshot.</div>}
                {evidenceRows.slice(0,100).map(e=><div className="evidence-row" key={evidenceRowKey(e)}><time>{new Date(e.timestamp).toLocaleTimeString()}</time><span className={"evidence-family "+statusClass(e.family)}>{e.family}</span><strong>{e.component}</strong><div className="plain-evidence"><SignalExplanation signal={e.signal}/></div><code>{e.evidenceId}</code></div>)}
              </div>
              {evidenceRows.length>100 && <div className="evidence-more">Showing first 100 of {evidenceRows.length} events.</div>}
            </section>

            <section className="panel ai-panel" id="ai-diagnosis">
              <div className="panel-head"><div><span className="eyebrow">ADVISORY LAYER</span><h2>AI diagnosis</h2><p>AI explains sanitized deterministic evidence. It does not calculate blast radius.</p></div>
                <button className="primary" onClick={runDiagnosis} disabled={diagnosing}>{diagnosing?"Diagnosing…":diagnosis?"Run again":"Generate diagnosis"} <Bot size={16}/></button>
              </div>
              {diagnosis ? <Diagnosis data={diagnosis}/> : <div className="ai-placeholder"><Bot size={28}/><span>Generate an evidence-bounded explanation and recommended next steps.</span></div>}
            </section>
          </>}
        </div>
      </section>
    </main>
  </div>;
}

function Kpi({icon,label,value,meta}) { return <div className="kpi panel"><div className="kpi-icon">{icon}</div><div><span>{label}</span><strong>{value}</strong><small>{meta}</small></div></div> }

function Diagnosis({data}) {
  const groups=[["Immediate actions",data.immediateActions],["Medium-term",data.mediumTermActions],["Strategic",data.strategicActions]];
  return <div className="diagnosis">
    <div className="diagnosis-meta"><span>{data.provider}</span><span>{data.model}</span>{data.evidenceVersion != null && <span>Evidence version {data.evidenceVersion}</span>}</div>
    <div className="diagnosis-copy"><h3>Summary</h3><p>{data.summary}</p><h3>Probable cause</h3><p>{data.probableCause}</p></div>
    <div className="actions">{groups.map(([name,items])=><div key={name}><h3>{name}</h3><ol>{(items||[]).map(x=><li key={`${name}-${x}`}>{x}</li>)}</ol></div>)}</div>
    {!!data.limitations?.length && <div className="limitations"><h3>Limitations</h3>{data.limitations.map(x=><p key={`limitation-${x}`}>• {x}</p>)}</div>}
  </div>
}

createRoot(document.getElementById("root")).render(<React.StrictMode><App/></React.StrictMode>);

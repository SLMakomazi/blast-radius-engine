import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  Activity, AlertTriangle, Bot, CheckCircle2, ChevronRight, Clock3,
  Database, GitBranch, Server, Waves
} from "lucide-react";
import "./styles.css";

const API = import.meta.env.VITE_API_BASE_URL ?? "";

const statusClass = (value = "") => value.toLowerCase().replaceAll("_", "-");

function chooseSelectedIncidentId(data, selectedId) {
  if (!data.length) return null;
  if (selectedId && data.some(i => i.id === selectedId)) return selectedId;
  return data[0].id;
}

function getImpactScope(impacts, involved) {
  if (impacts.length > 0 && involved === impacts.length) return "FULL CHAIN";
  if (involved > 0) return "PARTIAL";
  return "NONE";
}

function evidenceRowKey(e) {
  return e.evidenceId || `${e.timestamp}-${e.component}-${e.family}-${e.signal}`;
}

function incidentSignals(incident) {
  const analysis = incident?.analysis || incident?.analysisSnapshot || {};
  return (analysis.timeline || []).map(e => String(e.signal || "").toLowerCase());
}

function incidentScenario(incident) {
  const signals = incidentSignals(incident);
  if (signals.some(s => s.includes("database connectivity"))) return "DB Connectivity";
  if (signals.some(s => s.includes("latency") || s.includes("slow span") || s.includes("timeout"))) return "Latency";
  if (signals.some(s => s.includes("intermittent http 500"))) return "Intermittent 500";
  if (signals.some(s => s.includes("application error http 500") || s.includes("5xx counter"))) return "HTTP 500";
  if (signals.some(s => s.includes("process cpu"))) return "CPU Pressure";
  if (signals.some(s => s.includes("connection-pool"))) return "Pool Pressure";
  return null;
}

function incidentStage(incident) {
  const signals = incidentSignals(incident);

  // Explicit Stage 2 fault evidence wins for degraded-but-running scenarios,
  // including synthetic DB connectivity failures that can also produce error spans.
  if (signals.some(signal => signal.includes("stage2"))) return "STAGE_2";

  // Hard outages can produce secondary timeout/5xx metrics. Those symptoms must
  // not reclassify an availability failure as degradation.
  const hardFailure = signals.some(signal =>
    signal.includes("health down")
    || signal.includes("health out_of_service")
    || signal.includes("dependency error observed by")
  );
  if (hardFailure) return "STAGE_1";

  return signals.some(signal =>
    signal.includes("http mean latency")
    || signal.includes("5xx counter")
    || signal.includes("timeout counter")
    || signal.includes("process cpu")
    || signal.includes("connection-pool contention")
  ) ? "STAGE_2" : "STAGE_1";
}

function App() {
  const [status, setStatus] = useState("ACTIVE");
  const [stage, setStage] = useState("STAGE_1");
  const [incidents, setIncidents] = useState([]);
  const [selected, setSelected] = useState(null);
  const [diagnosis, setDiagnosis] = useState(null);
  const [diagnosisIncidentId, setDiagnosisIncidentId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [diagnosing, setDiagnosing] = useState(false);
  const [error, setError] = useState("");
  const [lastUpdated, setLastUpdated] = useState(null);
  const [evidenceComponent, setEvidenceComponent] = useState(null);
  const [evidenceFamily, setEvidenceFamily] = useState("ALL");

  async function loadIncidents(nextStatus = status) {
    setLoading(true);
    setError("");
    try {
      const res = await fetch(`${API}/api/v1/blast-radius/incidents?status=${nextStatus}`);
      if (!res.ok) throw new Error(`Incident API returned HTTP ${res.status}`);
      const data = await res.json();
      setIncidents(data);
      setLastUpdated(new Date());
      const stageData = data.filter(i => incidentStage(i) === stage);
      const nextSelectedId = chooseSelectedIncidentId(stageData, selected?.id);
      if (!nextSelectedId) {
        setSelected(null);
        setDiagnosis(null);
        setDiagnosisIncidentId(null);
        return;
      }

      if (diagnosisIncidentId && diagnosisIncidentId !== nextSelectedId) {
        setDiagnosis(null);
        setDiagnosisIncidentId(null);
      }
      await loadIncident(nextSelectedId);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  async function loadIncident(id) {
    setError("");
    try {
      const res = await fetch(`${API}/api/v1/blast-radius/incidents/${id}`);
      if (!res.ok) throw new Error(`Incident detail returned HTTP ${res.status}`);
      const next = await res.json();
      setSelected(prev => prev?.id === next.id && JSON.stringify(prev) === JSON.stringify(next) ? prev : next);
    } catch (e) {
      setError(e.message);
    }
  }

  async function runDiagnosis() {
    if (!selected) return;
    setDiagnosing(true);
    setError("");
    try {
      const res = await fetch(`${API}/api/v1/blast-radius/incidents/${selected.id}/diagnosis`, { method: "POST" });
      if (!res.ok) throw new Error(`Diagnosis API returned HTTP ${res.status}`);
      setDiagnosis(await res.json());
      setDiagnosisIncidentId(selected.id);
    } catch (e) {
      setError(e.message);
    } finally {
      setDiagnosing(false);
    }
  }

  useEffect(() => {
    void loadIncidents(status);
    const timer = window.setInterval(() => {
      void loadIncidents(status);
    }, 5000);
    return () => window.clearInterval(timer);
  }, [status, stage]);

  const analysis = selected?.analysis || selected?.analysisSnapshot || {};
  const impacts = analysis.impacts || [];
  const coverage = analysis.coverage || {};
  const observed = impacts.filter(i => i.state === "OBSERVED").length;
  const maxDepth = impacts.reduce((m, i) => Math.max(m, i.distance ?? 0), 0);
  const originCount = impacts.filter(i => i.state === "ORIGIN").length;
  const involved = originCount + observed;
  const impactPercent = impacts.length ? Math.round((involved / impacts.length) * 100) : 0;
  const impactScope = getImpactScope(impacts, involved);
  const timeline = analysis.timeline || [];
  const evidenceRows = timeline.filter(e =>
    (!evidenceComponent || e.component === evidenceComponent)
    && (evidenceFamily === "ALL" || e.family === evidenceFamily)
  );
  const evidenceCounts = ["LOG","METRIC","TRACE","HEALTH"].reduce((a,f) => ({...a,[f]: timeline.filter(e => e.family === f).length}), {});
  const stageMeta = stage === "STAGE_1"
    ? { label: "Stage 1", title: "Failure Analysis", description: "Hard failures and outages: stopped services, database outages and dependency propagation." }
    : { label: "Stage 2", title: "Degradation Analysis", description: "Degraded-but-running services: HTTP 500s, intermittent errors, latency, connectivity and resource pressure." };
  const visibleIncidents = incidents.filter(i => incidentStage(i) === stage);

  return <div className="app">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><Waves size={22}/></div><div><strong>MadlangaAI</strong><span>Blast Radius</span></div></div>
      <nav>
        <div className="nav-label nav-label-first">FAILURE STAGES</div>
        <button className={stage==="STAGE_1"?"nav-active":""} onClick={()=>setStage("STAGE_1")}><AlertTriangle size={18}/><span><strong>Stage 1</strong><small>Hard failures</small></span></button>
        <button className={stage==="STAGE_2"?"nav-active":""} onClick={()=>setStage("STAGE_2")}><Activity size={18}/><span><strong>Stage 2</strong><small>Degradation</small></span></button>
        <div className="nav-label">ENGINE</div>
        <button><GitBranch size={18}/> Topology</button>
        <button><Bot size={18}/> AI diagnosis</button>
      </nav>
      <div className="principle"><span>CORE PRINCIPLE</span><p>Topology calculates potential impact. Telemetry proves observed impact. AI explains sanitized evidence.</p></div>
    </aside>

    <main>
      <header>
        <div><span className="eyebrow">MADLANGAAI / {stageMeta.label.toUpperCase()}</span><h1>{stageMeta.title}</h1><p>{stageMeta.description}</p></div>
        <div className="live-status"><span className="live-dot"></span><span>Live · auto-updates every 5s{lastUpdated ? ` · ${lastUpdated.toLocaleTimeString()}` : ""}</span></div>
      </header>

      {error && <div className="error"><AlertTriangle size={18}/>{error}</div>}

      <section className="stage-banner panel">
        <div><span className="stage-number">{stageMeta.label}</span><strong>{stageMeta.title}</strong><p>{stageMeta.description}</p></div>
        {stage === "STAGE_2" && <div className="scenario-chips"><span>HTTP 500</span><span>Intermittent 500</span><span>Latency</span><span>DB connectivity</span><span>CPU / pool pressure</span></div>}
        {stage === "STAGE_1" && <div className="scenario-chips"><span>PostgreSQL outage</span><span>Document outage</span><span>Customer outage</span><span>Payment outage</span></div>}
      </section>

      <section className="workspace">
        <div className="incident-column panel">
          <div className="panel-head"><div><span className="eyebrow">INCIDENTS</span><h2>History</h2></div><div className="segmented">
            {["ACTIVE","RESOLVED"].map(s => <button key={s} className={status===s?"selected":""} onClick={()=>setStatus(s)}>{s}</button>)}
          </div></div>
          <div className="incident-list">
            {loading && <div className="empty">Loading incidents…</div>}
            {!loading && !visibleIncidents.length && <div className="empty">No {status.toLowerCase()} {stageMeta.label.toLowerCase()} incidents.</div>}
            {visibleIncidents.map(i => <button key={i.id} className={"incident-item "+(selected?.id===i.id?"current":"")} onClick={()=>loadIncident(i.id)}>
              <div className="incident-top"><span className={"severity "+statusClass(i.severityLevel)}>{i.severityLevel}</span><span className={"state "+statusClass(i.status)}>{i.status}</span></div>
              <strong>{i.originComponent}</strong>
              {incidentScenario(i) && <span className="scenario-label">{incidentScenario(i)}</span>}
              <span>{i.applicationId} · {i.environment}</span>
              <small><Clock3 size={12}/>{new Date(i.startedAt).toLocaleString()}</small>
            </button>)}
          </div>
        </div>

        <div className="detail-column">
          {!selected ? <div className="panel empty large">Select an incident to inspect its deterministic evidence.</div> : <>
            <section className="incident-title panel">
              <div><span className="eyebrow">INCIDENT {selected.id.slice(0,8)} · {incidentStage(selected).replace("_"," ")}</span><h2>{selected.originComponent} <span>origin</span></h2><p>{incidentScenario(selected) || "Availability failure"} · {selected.applicationId} / {selected.environment}</p></div>
              <div className="incident-badges"><span className={"severity "+statusClass(selected.severityLevel)}>{selected.severityLevel} · {selected.severityScore}</span><span className={"state "+statusClass(selected.status)}>{selected.status}</span></div>
            </section>

            <section className="kpis">
              <Kpi icon={<Database/>} label="Origin" value={selected.originComponent} meta={selected.originConfidence+" confidence"}/>
              <Kpi icon={<Activity/>} label="Observed impact" value={observed} meta={impacts.length+" total components"}/>
              <Kpi icon={<GitBranch/>} label="Max depth" value={maxDepth} meta="dependency hops"/>
              <Kpi icon={<Waves/>} label="Impact scope" value={impactScope} meta={`${involved} / ${impacts.length} components involved`}/>
            </section>

            <section className="panel topology">
              <div className="panel-head"><div><span className="eyebrow">PROPAGATION</span><h2>Impact path</h2></div><span className="legend"><i></i> observed evidence</span></div>
              <div className="topology-flow">
                {impacts.slice().sort((a,b)=>(a.distance??0)-(b.distance??0)).map((impact, idx) => <React.Fragment key={impact.component}>
                  {idx>0 && <ChevronRight className="arrow"/>}
                  <button className={"node "+statusClass(impact.state)} onClick={()=>{setEvidenceComponent(impact.component);setEvidenceFamily("ALL");}} title={`Inspect ${impact.component} evidence`}>
                    {impact.component==="postgres"?<Database/>:<Server/>}
                    <strong>{impact.component}</strong>
                    <span>{impact.state}</span>
                    <small>{impact.distance === 0 ? "Origin" : `Distance ${impact.distance}`}</small>
                  </button>
                </React.Fragment>)}
              </div>
            </section>

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

            <section className="panel evidence-explorer">
              <div className="panel-head"><div><span className="eyebrow">EVIDENCE EXPLORER</span><h2>{evidenceComponent || "All components"}</h2><p>Sanitized incident evidence captured from logs, metrics, traces and health telemetry.</p></div><span className="evidence-total">{evidenceRows.length} events</span></div>
              <div className="evidence-tabs">{["ALL","LOG","METRIC","TRACE","HEALTH"].map(f=><button key={f} className={evidenceFamily===f?"selected":""} onClick={()=>setEvidenceFamily(f)}>{f==="ALL"?"All":f[0]+f.slice(1).toLowerCase()}</button>)}{evidenceComponent && <button onClick={()=>setEvidenceComponent(null)}>All components</button>}</div>
              <div className="evidence-list">
                {!evidenceRows.length && <div className="empty">No {evidenceFamily==="ALL"?"":evidenceFamily.toLowerCase()+" "}evidence events captured in this incident snapshot.</div>}
                {evidenceRows.slice(0,100).map(e=><div className="evidence-row" key={evidenceRowKey(e)}><time>{new Date(e.timestamp).toLocaleTimeString()}</time><span className={"evidence-family "+statusClass(e.family)}>{e.family}</span><strong>{e.component}</strong><p>{e.signal}</p><code>{e.evidenceId}</code></div>)}
              </div>
              {evidenceRows.length>100 && <div className="evidence-more">Showing first 100 of {evidenceRows.length} events.</div>}
            </section>

            <section className="panel ai-panel">
              <div className="panel-head"><div><span className="eyebrow">ADVISORY LAYER</span><h2>AI diagnosis</h2><p>AI explains sanitized deterministic evidence. It does not calculate blast radius.</p></div>
                <button className="primary" onClick={runDiagnosis} disabled={diagnosing}>{diagnosing?"Diagnosing…":diagnosis?"Run again":"Generate diagnosis"} <Bot size={16}/></button>
              </div>
              {diagnosis && diagnosisIncidentId === selected.id ? <Diagnosis data={diagnosis}/> : <div className="ai-placeholder"><Bot size={28}/><span>Generate an evidence-bounded explanation and recommended next steps.</span></div>}
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
    <div className="diagnosis-meta"><span>{data.provider}</span><span>{data.model}</span></div>
    <div className="diagnosis-copy"><h3>Summary</h3><p>{data.summary}</p><h3>Probable cause</h3><p>{data.probableCause}</p></div>
    <div className="actions">{groups.map(([name,items])=><div key={name}><h3>{name}</h3><ol>{(items||[]).map(x=><li key={`${name}-${x}`}>{x}</li>)}</ol></div>)}</div>
    {!!data.limitations?.length && <div className="limitations"><h3>Limitations</h3>{data.limitations.map(x=><p key={`limitation-${x}`}>• {x}</p>)}</div>}
  </div>
}

createRoot(document.getElementById("root")).render(<React.StrictMode><App/></React.StrictMode>);

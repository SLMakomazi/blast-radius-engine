import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  Activity, AlertTriangle, Bot, CheckCircle2, ChevronRight, Clock3,
  Database, GitBranch, Server, Waves
} from "lucide-react";
import "./styles.css";

const API = import.meta.env.VITE_API_BASE_URL ?? "";

const statusClass = (value = "") => value.toLowerCase().replaceAll("_", "-");

function App() {
  const [status, setStatus] = useState("ACTIVE");
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
      if (!data.length) {
        setSelected(null);
        setDiagnosis(null);
        setDiagnosisIncidentId(null);
      } else {
        if (selected?.id && data.some(i => i.id === selected.id)) {
          await loadIncident(selected.id);
        } else {
          if (diagnosisIncidentId && diagnosisIncidentId !== data[0].id) {
            setDiagnosis(null);
            setDiagnosisIncidentId(null);
          }
          await loadIncident(data[0].id);
        }
      }
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
    loadIncidents(status);
    const timer = window.setInterval(() => loadIncidents(status), 5000);
    return () => window.clearInterval(timer);
  }, [status]);

  const analysis = selected?.analysis || selected?.analysisSnapshot || {};
  const impacts = analysis.impacts || [];
  const coverage = analysis.coverage || {};
  const observed = impacts.filter(i => i.state === "OBSERVED").length;
  const maxDepth = impacts.reduce((m, i) => Math.max(m, i.distance ?? 0), 0);
  const originCount = impacts.filter(i => i.state === "ORIGIN").length;
  const involved = originCount + observed;
  const impactPercent = impacts.length ? Math.round((involved / impacts.length) * 100) : 0;
  const impactScope = impacts.length && involved === impacts.length ? "FULL CHAIN" : involved ? "PARTIAL" : "NONE";
  const timeline = analysis.timeline || [];
  const evidenceRows = timeline.filter(e => (!evidenceComponent || e.component === evidenceComponent) && (evidenceFamily === "ALL" || e.family === evidenceFamily));
  const evidenceCounts = ["LOG","METRIC","TRACE","HEALTH"].reduce((a,f) => ({...a,[f]: timeline.filter(e => e.family === f).length}), {});

  return <div className="app">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><Waves size={22}/></div><div><strong>MadlangaAI</strong><span>Blast Radius</span></div></div>
      <nav>
        <button className="nav-active"><Activity size={18}/> Incident analysis</button>
        <div className="nav-label">ENGINE</div>
        <button><GitBranch size={18}/> Topology</button>
        <button><Bot size={18}/> AI diagnosis</button>
      </nav>
      <div className="principle"><span>CORE PRINCIPLE</span><p>Topology calculates potential impact. Telemetry proves observed impact. AI explains sanitized evidence.</p></div>
    </aside>

    <main>
      <header>
        <div><span className="eyebrow">MADLANGAAI / PHASE 10</span><h1>Blast Radius Command Center</h1><p>Evidence-backed incident impact, propagation and diagnosis.</p></div>
        <div className="live-status"><span className="live-dot"></span><span>Live · auto-updates every 5s{lastUpdated ? ` · ${lastUpdated.toLocaleTimeString()}` : ""}</span></div>
      </header>

      {error && <div className="error"><AlertTriangle size={18}/>{error}</div>}

      <section className="workspace">
        <div className="incident-column panel">
          <div className="panel-head"><div><span className="eyebrow">INCIDENTS</span><h2>History</h2></div><div className="segmented">
            {["ACTIVE","RESOLVED"].map(s => <button key={s} className={status===s?"selected":""} onClick={()=>setStatus(s)}>{s}</button>)}
          </div></div>
          <div className="incident-list">
            {loading && <div className="empty">Loading incidents…</div>}
            {!loading && !incidents.length && <div className="empty">No {status.toLowerCase()} incidents.</div>}
            {incidents.map(i => <button key={i.id} className={"incident-item "+(selected?.id===i.id?"current":"")} onClick={()=>loadIncident(i.id)}>
              <div className="incident-top"><span className={"severity "+statusClass(i.severityLevel)}>{i.severityLevel}</span><span className={"state "+statusClass(i.status)}>{i.status}</span></div>
              <strong>{i.originComponent}</strong>
              <span>{i.applicationId} · {i.environment}</span>
              <small><Clock3 size={12}/>{new Date(i.startedAt).toLocaleString()}</small>
            </button>)}
          </div>
        </div>

        <div className="detail-column">
          {!selected ? <div className="panel empty large">Select an incident to inspect its deterministic evidence.</div> : <>
            <section className="incident-title panel">
              <div><span className="eyebrow">INCIDENT {selected.id.slice(0,8)}</span><h2>{selected.originComponent} <span>origin</span></h2><p>{selected.applicationId} / {selected.environment}</p></div>
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
                    <small>{impact.distance===0?"Origin":`Distance ${impact.distance}`}</small>
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
                {evidenceRows.slice(0,100).map((e,i)=><div className="evidence-row" key={e.evidenceId || i}><time>{new Date(e.timestamp).toLocaleTimeString()}</time><span className={"evidence-family "+statusClass(e.family)}>{e.family}</span><strong>{e.component}</strong><p>{e.signal}</p><code>{e.evidenceId}</code></div>)}
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
    <div className="actions">{groups.map(([name,items])=><div key={name}><h3>{name}</h3><ol>{(items||[]).map((x,i)=><li key={i}>{x}</li>)}</ol></div>)}</div>
    {!!data.limitations?.length && <div className="limitations"><h3>Limitations</h3>{data.limitations.map((x,i)=><p key={i}>• {x}</p>)}</div>}
  </div>
}

createRoot(document.getElementById("root")).render(<React.StrictMode><App/></React.StrictMode>);

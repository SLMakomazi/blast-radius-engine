#!/usr/bin/env python3
"""Retrieve real evidence. Restores PostgreSQL/Collector even after failed assertions."""
import argparse
import base64
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
from http.client import HTTPException
import time
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen
import uuid

spec = importlib.util.spec_from_file_location("phase2", Path(__file__).with_name("verify-phase2.py"))
p2 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p2)
SECRET_VALUES = []
JSON_CONTENT_TYPE = "application/json"
TEMPO_TRACE_URL = "http://localhost:3200/api/traces/"
COLLECTOR_HEALTH_URL = "http://localhost:13133/"
SERVICES = {"payment-service": (8081, "/api/payments"), "customer-service": (8082, "/api/customers/validate"), "document-service": (8083, "/api/documents")}


def fetch(url):
    with urlopen(Request(url, headers={"Accept": JSON_CONTENT_TYPE}), timeout=8) as response:
        raw = response.read().decode()
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return raw


def eventually(check, label, timeout=90):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            result = check()
            if result:
                return result
        except (OSError, HTTPException, AssertionError) as ex:
            last = str(ex)
        time.sleep(2)
    raise AssertionError(f"Timed out waiting for {label}: {last}")


def prom(query):
    data = fetch("http://localhost:9090/api/v1/query?" + urlencode({"query": query}))
    assert data["status"] == "success", data
    return data["data"]["result"]


def logs(correlation):
    query = '{service_name=~"payment-service|customer-service|document-service"} | correlation_id="' + correlation + '"'
    data = fetch("http://localhost:3100/loki/api/v1/query_range?" + urlencode({
        "query": query, "start": str(time.time_ns()-600_000_000_000), "end": str(time.time_ns()), "limit": 200}))
    assert data["status"] == "success", data
    return data["data"]["result"]


def hex_id(value):
    if re.fullmatch(r"[0-9a-f]{16}|[0-9a-f]{32}", value or ""):
        return value
    return base64.b64decode(value).hex() if value else ""


def trace_spans(trace):
    result = []
    for batch in trace.get("batches", trace.get("resourceSpans", [])):
        attrs = {x["key"]: x["value"] for x in batch["resource"]["attributes"]}
        service = attrs["service.name"]["stringValue"]
        for scope in batch.get("scopeSpans", batch.get("instrumentationLibrarySpans", [])):
            for span in scope.get("spans", []):
                result.append({"service": service, "name": span["name"], "id": hex_id(span["spanId"]),
                               "parent": hex_id(span.get("parentSpanId", "")), "status": span.get("status", {}),
                               "kind": span.get("kind"), "attributes": span.get("attributes", []),
                               "scope": scope.get("scope", scope.get("instrumentationLibrary", {}))})
    return result


def assert_path(spans):
    by_id = {s["id"]: s for s in spans}
    def has_ancestor(span, service):
        visited = set()
        while span.get("parent") in by_id and span["parent"] not in visited:
            visited.add(span["parent"])
            span = by_id[span["parent"]]
            if span["service"] == service:
                return True
        return False
    assert any(s["service"] == "customer-service" and has_ancestor(s, "payment-service") for s in spans), spans
    assert any(s["service"] == "document-service" and has_ancestor(s, "customer-service") for s in spans), spans


def safe_telemetry(data):
    text = json.dumps(data)
    for forbidden in [*SECRET_VALUES, '"exception.stacktrace"', '"db.connection_string"', '"http.request.body"']:
        if not forbidden:
            continue
        assert forbidden not in text, "Telemetry contains a forbidden secret, field or canary"


def health():
    values = {"blast-radius-api": p2.get_health(8080)}
    for service, (port, _) in SERVICES.items():
        values[service] = {kind: p2.get_health(port, "/actuator/health/" + kind) for kind in ("readiness", "liveness")}
    return values


def counters(statuses):
    values = {}
    for service, (_, route) in SERVICES.items():
        q = f'http_server_requests_seconds_count{{service="{service}",uri="{route}",status="{statuses[service]}"}}'
        rows = prom(q)
        values[service] = sum(float(r["value"][1]) for r in rows)
    return values


def collect(correlation, failed, output, before):
    def complete_logs():
        rows = logs(correlation)
        return rows if {r["stream"]["service_name"] for r in rows} == set(SERVICES) else None
    rows = eventually(complete_logs, "centralized logs from all three services")
    ids = set()
    for row in rows:
        for value in row["values"]:
            meta = dict(row["stream"])
            if len(value) > 2:
                meta.update(value[2])
            if meta.get("trace_id"):
                ids.add(hex_id(meta["trace_id"]))
    assert len(ids) == 1, ("Expected one trace ID shared by all correlated logs", ids, rows)
    trace_id = ids.pop()
    def complete_trace():
        trace = fetch(TEMPO_TRACE_URL + trace_id)
        spans = trace_spans(trace)
        assert_path(spans)
        return trace
    trace = eventually(complete_trace, "distributed trace path")
    spans = trace_spans(trace)
    db = [s for s in spans if s["service"] == "document-service" and
          ("jdbc" in s["scope"].get("name", "").lower() or "getConnection" in s["name"])]
    assert db, "No database instrumentation spans found"
    errors = [s for s in spans if s["status"].get("code") in (2, "STATUS_CODE_ERROR")]
    if failed:
        assert set(SERVICES).issubset({s["service"] for s in errors}), errors
        assert any(s in errors for s in db), "No failed database connection/operation span"
        for service in SERVICES:
            assert any("event=dependency_failed" in value[1] for row in rows if row["stream"]["service_name"] == service for value in row["values"])
    else:
        assert not errors, errors
    statuses = {s: ("503" if s == "document-service" else "502") if failed else ("200" if s == "customer-service" else "201") for s in SERVICES}
    def changed_metrics():
        after = counters(statuses)
        return after if all(after[s] > before[s] for s in SERVICES) else None
    after = eventually(changed_metrics, "Prometheus status counters advancing")
    latency = prom('http_server_requests_seconds_sum{uri=~"/api/payments|/api/customers/validate|/api/documents"}')
    pool = prom('hikaricp_connections_active{service="document-service"}')
    clients = prom('http_client_requests_seconds_count')
    assert latency and pool and clients, "Missing latency, DB pool or downstream metrics"
    record = {"observedAtUnix": time.time(), "environment": "local", "correlationId": correlation, "traceId": trace_id, "logs": rows, "trace": trace,
              "spanSummary": spans, "databaseSpans": db, "errors": errors,
              "counterBefore": before, "counterAfter": after, "latency": latency,
              "pool": pool, "clients": clients, "health": health()}
    safe_telemetry(record)
    (output / (correlation + ".json")).write_text(json.dumps(record, indent=2))
    summary = {"correlationId": correlation, "traceId": trace_id, "spans": len(spans), "databaseSpans": len(db), "errorSpans": len(errors), "counterDelta": {s: after[s]-before[s] for s in SERVICES}, "health": record["health"]}
    print(json.dumps(summary), flush=True)
    return summary



def wait_for_telemetry(timeout=90, poll_interval=2):
    """Bounded verification-side proof; never used by business applications.

    Each candidate has unique business and W3C IDs. Lost candidates are replaced
    with new synthetic transactions, not retried business operations. All network
    timeouts and sleeps share one monotonic deadline.
    """
    started = time.monotonic()
    deadline = started + timeout
    families = ("logs", "traces", "metrics", "health")
    result = {"telemetryAvailable": False, "timedOut": False,
              "timeoutSeconds": timeout, "pollIntervalSeconds": poll_interval,
              "probes": [], "families": dict.fromkeys(families, False)}
    metric_query = ('http_server_requests_seconds_count{service=~"payment-service|customer-service|document-service",'
                    'uri=~"/api/payments|/api/customers/validate|/api/documents"}')

    def remaining():
        value = deadline - time.monotonic()
        if value <= 0:
            raise TimeoutError("Telemetry availability deadline expired")
        return value

    def bounded_fetch(url, data=None, headers=None):
        request = Request(url, data=data, headers={"Accept": JSON_CONTENT_TYPE, **(headers or {})})
        try:
            response = urlopen(request, timeout=min(2, remaining()))
        except HTTPError as error:
            response = error
        with response:
            return response.status, json.load(response)

    def metric_values(query):
        _, response = bounded_fetch("http://localhost:9090/api/v1/query?" + urlencode({"query": query}))
        assert response["status"] == "success"
        return response["data"]["result"]

    def counts(rows):
        return {service: sum(float(row["value"][1]) for row in rows
                             if row["metric"].get("service") == service) for service in SERVICES}

    def inspect_family(name, check):
        try:
            evidence = check()
            result["families"][name] = bool(evidence)
            if evidence:
                result["evidence"][name] = evidence
        except (OSError, HTTPException, AssertionError, ValueError, KeyError):
            result["families"][name] = False

    probe = None
    while time.monotonic() < deadline:
        # A candidate sent during reconnect may be permanently missing. Generate a
        # fresh transaction after a bounded observation window, never reuse its IDs.
        if probe is None or time.monotonic() - probe["monotonicStart"] >= max(10, poll_interval * 2):
            probe = {"correlationId": "phase3-availability-" + uuid.uuid4().hex,
                     "traceId": uuid.uuid4().hex, "monotonicStart": time.monotonic(),
                     "sentAtUnix": time.time()}
            result["probes"].append({k: v for k, v in probe.items() if k != "monotonicStart"})
            result.update({"correlationId": probe["correlationId"], "traceId": probe["traceId"],
                           "families": dict.fromkeys(families, False), "evidence": {}})
            try:
                probe["before"] = counts(metric_values(metric_query))
            except (OSError, HTTPException, AssertionError, ValueError, KeyError):
                probe["before"] = None
            try:
                probe["sentAtUnix"] = time.time()
                result["probes"][-1]["sentAtUnix"] = probe["sentAtUnix"]
                status, body = bounded_fetch("http://localhost:8081/api/payments",
                    data=(p2.ROOT / "fixtures/payment-request.json").read_bytes(),
                    headers={"Content-Type": JSON_CONTENT_TYPE, "X-Correlation-ID": probe["correlationId"],
                             "traceparent": f'00-{probe["traceId"]}-0123456789abcdef-01'})
                result["probes"][-1]["correlationReturned"] = body.get("correlationId") == probe["correlationId"]
                result["probes"][-1]["httpStatus"] = status
            except (OSError, HTTPException, ValueError):
                result["probes"][-1]["correlationReturned"] = False
                result["probes"][-1]["httpStatus"] = None

        current_probe = probe.copy()

        def fresh_logs(probe=current_probe):
            query = '{service_name=~"payment-service|customer-service|document-service"} | correlation_id="' + probe["correlationId"] + '"'
            _, response = bounded_fetch("http://localhost:3100/loki/api/v1/query_range?" + urlencode({
                "query": query, "start": str(int((probe["sentAtUnix"] - 1) * 1e9)), "end": str(time.time_ns()), "limit": 100}))
            rows = response["data"]["result"]
            assert {row["stream"]["service_name"] for row in rows} == set(SERVICES)
            for row in rows:
                for value in row["values"]:
                    metadata = {**row["stream"], **(value[2] if len(value) > 2 else {})}
                    assert hex_id(metadata.get("trace_id")) == probe["traceId"]
            return rows

        def fresh_trace(probe=current_probe):
            _, trace = bounded_fetch(TEMPO_TRACE_URL + probe["traceId"])
            spans = trace_spans(trace)
            assert_path(spans)
            assert any(s["service"] == "document-service" and "jdbc" in s["scope"].get("name", "") for s in spans)
            return trace

        def fresh_metrics(probe=current_probe):
            rows = metric_values(metric_query)
            stamps = metric_values("timestamp(" + metric_query + ")")
            after = counts(rows)
            assert probe["before"] is not None
            assert all(after[s] > probe["before"][s] for s in SERVICES)
            for service in SERVICES:
                assert any(row["metric"].get("service") == service and
                           float(row["value"][1]) >= probe["sentAtUnix"] for row in stamps)
            return {"before": probe["before"], "after": after, "samples": rows, "scrapeTimestamps": stamps}

        def current_health():
            observations = {}
            for service, port in [("blast-radius-api", 8080), *[(s, p) for s, (p, _) in SERVICES.items()]]:
                paths = ["/actuator/health"] if port == 8080 else ["/actuator/health/liveness", "/actuator/health/readiness"]
                observations[service] = {}
                for path in paths:
                    status, body = bounded_fetch(f"http://localhost:{port}{path}")
                    # DOWN is available health evidence, not missing telemetry.
                    assert status in (200, 503) and isinstance(body.get("status"), str)
                    observations[service][path] = {"httpStatus": status, "status": body["status"], "observedAtUnix": time.time()}
            return observations

        for name, check in (("logs", fresh_logs), ("traces", fresh_trace),
                            ("metrics", fresh_metrics), ("health", current_health)):
            if time.monotonic() >= deadline:
                break
            inspect_family(name, check)
        if all(result["families"].values()) and time.monotonic() < deadline:
            result["telemetryAvailable"] = True
            break
        time.sleep(min(poll_interval, max(0, deadline - time.monotonic())))

    result.update({"timedOut": not result["telemetryAvailable"],
                   "elapsedSeconds": round(time.monotonic() - started, 3),
                   "missingEvidenceFamilies": [name for name in families if not result["families"][name]],
                   "observedAtUnix": time.time()})
    safe_telemetry(result)
    return result


def collector_action(action):
    # Bound the test's control operation too; no machine/service-wide recovery.
    command = [p2.PODMAN, action]
    if action == "stop":
        command += ["--time", "5"]
    subprocess.run([*command, "otel-collector"], check=True, timeout=30)


def write_evidence(filename, data):
    """Write only fixed evidence filenames under the repository-owned evidence directory."""
    allowed = {
        "availability-timeout.json", "availability-recovery.json", "availability.json",
        "partial.json", "summary.json"
    }
    if filename not in allowed:
        raise ValueError("Unsupported evidence filename")
    evidence_dir = p2.ROOT / ".phase3-evidence"
    evidence_dir.mkdir(mode=0o700, exist_ok=True)
    target = evidence_dir / filename
    target.write_text(json.dumps(data, indent=2))
    return target


def availability_cases(args):
    """Only timeout and successful recovery; no database failure or rebuild."""
    try:
        collector_action("stop")
        unavailable = wait_for_telemetry(args.timeout_case_seconds, args.poll_interval)
        assert unavailable["timedOut"] and not unavailable["telemetryAvailable"]
        assert set(unavailable["missingEvidenceFamilies"]) == {"logs", "traces"}, unavailable["families"]
        assert unavailable["elapsedSeconds"] <= args.timeout_case_seconds + 2
        assert all(probe["httpStatus"] == 201 and probe["correlationReturned"] for probe in unavailable["probes"])
        assert p2.count_rows(unavailable["correlationId"]) == 1
        write_evidence("availability-timeout.json", unavailable)
        print("TIMEOUT", json.dumps({k: v for k, v in unavailable.items() if k != "evidence"}), flush=True)
    finally:
        collector_action("start")
    eventually(lambda: fetch(COLLECTOR_HEALTH_URL), "Collector infrastructure readiness", timeout=args.availability_timeout)
    available = wait_for_telemetry(args.availability_timeout, args.poll_interval)
    write_evidence("availability-recovery.json", available)
    assert available["telemetryAvailable"], available["missingEvidenceFamilies"]
    assert all(probe["httpStatus"] == 201 and probe["correlationReturned"] for probe in available["probes"])
    assert p2.count_rows(available["correlationId"]) == 1
    print("RECOVERY", json.dumps({k: v for k, v in available.items() if k != "evidence"}), flush=True)
    print("Telemetry availability timeout and recovery cases PASSED", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--availability-only", action="store_true", help="Probe telemetry without stopping any service")
    modes.add_argument("--availability-cases", action="store_true", help="Run only Collector timeout/recovery cases")
    parser.add_argument("--runtime", default=os.environ.get("CONTAINER_RUNTIME", p2.PODMAN),
                        help="Container CLI executable (defaults to the existing Podman configuration)")
    parser.add_argument("--availability-timeout", type=float, default=90)
    parser.add_argument("--poll-interval", type=float, default=2)
    parser.add_argument("--timeout-case-seconds", type=float, default=8)
    args = parser.parse_args()
    if any(not 0 < value < float("inf") for value in
           (args.availability_timeout, args.poll_interval, args.timeout_case_seconds)):
        parser.error("timeouts and poll interval must be finite positive numbers")
    p2.PODMAN = args.runtime
    if args.availability_cases:
        availability_cases(args)
        return
    if args.availability_only:
        result = wait_for_telemetry(args.availability_timeout, args.poll_interval)
        write_evidence("availability.json", result)
        print(json.dumps({k: v for k, v in result.items() if k != "evidence"}), flush=True)
        raise SystemExit(0 if result["telemetryAvailable"] else 1)
    # Keep the configured local password in memory only, never in evidence/output.
    config = json.loads(p2.compose("config", "--format", "json"))
    SECRET_VALUES.append(config["services"]["postgres"]["environment"]["POSTGRES_PASSWORD"])
    del config
    for port in (8080,8081,8082,8083):
        p2.wait_healthy(port)
        for endpoint in ("env", "configprops", "heapdump"):
            try:
                fetch(f"http://localhost:{port}/actuator/{endpoint}")
                raise AssertionError(f"Sensitive endpoint {endpoint} is exposed on {port}")
            except HTTPError as error:
                assert error.code == 404
    for port,path in ((9090,"/-/ready"),(3200,"/ready"),(3100,"/ready"),(13133,"/")):
        eventually(lambda port=port, path=path: fetch(f"http://localhost:{port}{path}"), f"backend {port}")
    eventually(lambda: len(prom('up{job="applications"} == 1')) == 4, "all four application scrape targets")
    suffix = uuid.uuid4().hex[:10]
    summary = []
    ok_status = {s: "200" if s == "customer-service" else "201" for s in SERVICES}
    bad_status = {s: "503" if s == "document-service" else "502" for s in SERVICES}
    for phase,failed in (("healthy",False),("postgres-failure",True),("recovery",False)):
        correlation = f"phase3-{phase}-{suffix}"
        before = counters(bad_status if failed else ok_status)
        try:
            if failed:
                p2.compose("stop", "postgres")
            canary_token = "phase3-" + uuid.uuid4().hex
            canary_password = "phase3-" + uuid.uuid4().hex
            SECRET_VALUES.extend((canary_token, canary_password))
            result = p2.pay(correlation, {"Authorization": "Bearer " + canary_token,
                                          "X-Canary-Password": canary_password})
            assert result[0] == (502 if failed else 201), result
            if failed:
                eventually(lambda: p2.get_health(8083,"/actuator/health/readiness") == (503,"DOWN"), "document readiness DOWN")
                assert p2.get_health(8083,"/actuator/health/liveness") == (200,"UP")
                assert p2.get_health(8080) == (200,"UP")
            else:
                assert p2.count_rows(correlation) == 1
            summary.append(collect(correlation,failed,p2.ROOT / ".phase3-evidence",before))
        finally:
            if failed:
                p2.compose("start","postgres");p2.wait_healthy(8083)
                assert p2.count_rows(correlation) == 0
    # Collector outage: direct scraping + health remain usable; no telemetry classification invented.
    correlation = f"phase3-partial-{suffix}"
    try:
        p2.compose("stop","otel-collector")
        before = counters(ok_status)
        partial_trace = uuid.uuid4().hex
        assert p2.pay(correlation, {"traceparent": f"00-{partial_trace}-0123456789abcdef-01"})[0] == 201
        assert p2.count_rows(correlation) == 1
        eventually(lambda: all(counters(ok_status)[s] > before[s] for s in SERVICES), "metrics during Collector outage")
        assert not logs(correlation), "Unexpected centralized logs while Collector is stopped"
        try:
            fetch(TEMPO_TRACE_URL + partial_trace)
            raise AssertionError("Unexpected trace while Collector is stopped")
        except HTTPError as error:
            assert error.code == 404
        try:
            fetch(COLLECTOR_HEALTH_URL)
            raise AssertionError("Collector health endpoint unexpectedly reachable")
        except (OSError, HTTPException):
            pass
        h=health();assert h["document-service"]["readiness"] == (200,"UP")
        partial={"correlationId":correlation,"httpStatus":201,"persistedRows":1,"collectorReachable":False,"traceId":partial_trace,"traceHttpStatus":404,"centralizedLogsAtCheck":0,"metrics":counters(ok_status),"health":h}
        write_evidence("partial.json", partial)
        print("PARTIAL",json.dumps(partial),flush=True)
    finally:
        p2.compose("start","otel-collector")
        eventually(lambda: fetch(COLLECTOR_HEALTH_URL),"Collector recovery")
    export_probes = wait_for_telemetry(args.availability_timeout, args.poll_interval)
    assert export_probes["telemetryAvailable"], export_probes["missingEvidenceFamilies"]
    correlation=f"phase3-observability-restored-{suffix}"
    before=counters(ok_status);assert p2.pay(correlation)[0]==201
    summary.append(collect(correlation,False,args.output,before))
    labels=fetch("http://localhost:3100/loki/api/v1/labels")["data"]
    assert not set(labels).intersection({"correlation_id","trace_id","span_id"}), labels
    write_evidence("summary.json", {"experiments":summary,"partial":partial,"indexLabels":labels,"telemetryAvailability":export_probes})
    print("Phase 3 verification PASSED; evidence:", p2.ROOT / ".phase3-evidence", flush=True)


if __name__ == "__main__":
    main()

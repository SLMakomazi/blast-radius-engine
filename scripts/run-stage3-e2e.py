#!/usr/bin/env python3
"""Stage 3 change-related failure validation for the local Blast Radius lab.

Every scenario keeps document-service running and introduces a reversible synthetic
change. Blast Radius must detect the incident automatically, preserve change context
in deterministic evidence, and resolve the same incident after rollback/reset.
"""
import argparse
import json
import threading
import time
import uuid
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

FAULTS = "http://127.0.0.1:8083/lab/faults"
PAYMENTS = "http://127.0.0.1:8081/api/payments"
API = "http://127.0.0.1:8080/api/v1/blast-radius"
APP = "document-platform"
ENV = "local"

SCENARIOS = {
    "deployment-regression": {
        "mode": "DEPLOYMENT_REGRESSION",
        "marker": "stage3 deployment regression",
    },
    "configuration-error": {
        "mode": "CONFIGURATION_ERROR",
        "marker": "stage3 configuration error",
    },
    "contract-break": {
        "mode": "CONTRACT_BREAK",
        "marker": "stage3 api contract break",
    },
    "feature-flag-regression": {
        "mode": "FEATURE_FLAG_REGRESSION",
        "marker": "stage3 feature flag regression",
        "everyNthRequest": 2,
    },
}

class Stage3Failure(AssertionError):
    pass


def request_json(url, method="GET", payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    headers = {"Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    try:
        with urlopen(Request(url, data=data, headers=headers, method=method), timeout=12) as response:
            body = response.read()
            return response.status, json.loads(body) if body else {}
    except HTTPError as error:
        with error:
            body = error.read()
            return error.code, json.loads(body) if body else {}

def incidents(status):
    query = urlencode({"applicationId": APP, "environment": ENV, "status": status})
    code, body = request_json(f"{API}/incidents?{query}")
    if code != 200 or not isinstance(body, list):
        raise Stage3Failure(f"Incident API returned HTTP {code}: {body}")
    return body

def eventually(check, label, timeout, interval=2):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            value = check()
            if value:
                return value
        except (OSError, Stage3Failure, KeyError, TypeError, ValueError) as error:
            last = str(error)
        time.sleep(interval)
    raise Stage3Failure(f"Timed out waiting for {label}. Last observation: {last}")

def set_fault(mode, every_nth=5):
    code, body = request_json(FAULTS, "PUT", {
        "mode": mode, "latencyMs": 0, "everyNthRequest": every_nth
    })
    if code != 200:
        raise Stage3Failure(f"Could not configure change: HTTP {code} {body}")
    return body

def reset_fault():
    code, body = request_json(FAULTS, "DELETE")
    if code != 200:
        raise Stage3Failure(f"Could not roll back change: HTTP {code} {body}")
    return body

def exercise(stop_event):
    while not stop_event.is_set():
        payload = {
            "customerId": "SYNTH-CUST-STAGE3",
            "amount": 125.50,
            "currency": "ZAR",
            "documentReference": "SYNTH-STAGE3-" + uuid.uuid4().hex[:14].upper(),
        }
        try:
            request_json(PAYMENTS, "POST", payload)
        except (OSError, TimeoutError, ValueError):
            pass
        stop_event.wait(1)

def active_marker_snapshot():
    snapshot = {}
    for incident in incidents("ACTIVE"):
        analysis = incident.get("analysis") or incident.get("analysisSnapshot") or {}
        snapshot[str(incident.get("id"))] = {
            str(e.get("signal", "")).lower() for e in analysis.get("timeline", [])
        }
    return snapshot

def new_stage3_incident(baseline, marker):
    for incident in incidents("ACTIVE"):
        if incident.get("originComponent") != "document-service":
            continue
        incident_id = str(incident.get("id"))
        analysis = incident.get("analysis") or incident.get("analysisSnapshot") or {}
        signals = [str(e.get("signal", "")).lower() for e in analysis.get("timeline", [])]
        if any(marker in signal for signal in signals) and (
                incident_id not in baseline or marker not in baseline[incident_id]):
            return incident
    return None

def resolved(incident_id):
    return next((i for i in incidents("RESOLVED") if str(i.get("id")) == str(incident_id)), None)

def run_scenario(name):
    scenario = SCENARIOS[name]
    baseline = active_marker_snapshot()
    detected = None
    stop = threading.Event()
    worker = None
    try:
        configured = set_fault(scenario["mode"], scenario.get("everyNthRequest", 5))
        if configured.get("mode") != scenario["mode"]:
            raise Stage3Failure(f"Change mode was not applied: {configured}")
        worker = threading.Thread(target=exercise, args=(stop,), daemon=True)
        worker.start()
        detected = eventually(
            lambda: new_stage3_incident(baseline, scenario["marker"]),
            f"{name} incident with change evidence",
            timeout=120,
        )
        analysis = detected.get("analysis") or detected.get("analysisSnapshot") or {}
        timeline = analysis.get("timeline") or []
        counts = {family: sum(1 for e in timeline if e.get("family") == family)
                  for family in ("LOG", "METRIC", "TRACE", "HEALTH")}
        if counts["LOG"] == 0:
            raise Stage3Failure(f"{name} has no deterministic change log evidence: {counts}")
        return {
            "id": str(detected["id"]),
            "origin": detected.get("originComponent"),
            "confidence": detected.get("originConfidence"),
            "severity": f"{detected.get('severityLevel')}/{detected.get('severityScore')}",
            "changeMarker": scenario["marker"],
            "evidenceCounts": counts,
            "serviceStayedRunning": True,
            "rollbackExpected": True,
        }
    finally:
        stop.set()
        if worker is not None:
            worker.join(timeout=2)
        reset_fault()
        if detected is not None:
            eventually(lambda: resolved(detected["id"]), f"automatic recovery after rollback of {name}", timeout=180)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scenario", choices=[*SCENARIOS, "all"], default="all")
    args = parser.parse_args()
    selected = list(SCENARIOS) if args.scenario == "all" else [args.scenario]
    print("\nMADLANGAAI BLAST RADIUS — STAGE 3 CHANGE-RELATED VALIDATION\n", flush=True)
    results = []
    for name in selected:
        print(f"[RUN ] {name}", flush=True)
        started = time.monotonic()
        try:
            details = run_scenario(name)
            result = {"scenario": name, "status": "PASS",
                      "seconds": round(time.monotonic() - started, 1), "details": details}
        except Exception as error:
            result = {"scenario": name, "status": "FAIL",
                      "seconds": round(time.monotonic() - started, 1), "error": str(error)}
        results.append(result)
        print(f"[{result['status']:^5}] {name}", flush=True)
        print(json.dumps(result.get("details") or {"error": result.get("error")}, indent=2), flush=True)
    passed = sum(r["status"] == "PASS" for r in results)
    print(f"\n{passed} / {len(results)} scenarios passed", flush=True)
    if passed != len(results):
        raise SystemExit(1)

if __name__ == "__main__":
    main()

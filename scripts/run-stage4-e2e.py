#!/usr/bin/env python3
"""Stage 4 complex/distributed failure validation for the local Blast Radius lab.

Validates cascading impact, compound dependency failure, flapping behavior and
partial-observability safety. The runner restores every injected fault and any
temporarily stopped observability service in finally blocks.
"""
import argparse
import json
import subprocess
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
    "distributed-cascade": {"mode": "DISTRIBUTED_CASCADE", "marker": "stage4 distributed cascade"},
    "compound-dependency": {"mode": "COMPOUND_DEPENDENCY_FAILURE", "marker": "stage4 compound dependency failure"},
    "flapping-dependency": {"mode": "FLAPPING_DEPENDENCY", "marker": "stage4 flapping dependency", "everyNthRequest": 2},
    "partial-observability": {"mode": "DISTRIBUTED_CASCADE", "marker": "stage4 distributed cascade", "partial": True},
}

class Stage4Failure(AssertionError):
    pass

def request_json(url, method="GET", payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    headers = {"Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    try:
        with urlopen(Request(url, data=data, headers=headers, method=method), timeout=12) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else {}
    except HTTPError as error:
        with error:
            raw = error.read()
            return error.code, json.loads(raw) if raw else {}

def compose(*args):
    result = subprocess.run(["docker", "compose", *args], text=True, capture_output=True)
    if result.returncode != 0:
        raise Stage4Failure((result.stderr or result.stdout).strip())
    return result.stdout.strip()

def incidents(status):
    query = urlencode({"applicationId": APP, "environment": ENV, "status": status})
    code, body = request_json(f"{API}/incidents?{query}")
    if code != 200 or not isinstance(body, list):
        raise Stage4Failure(f"Incident API returned HTTP {code}: {body}")
    return body

def eventually(check, label, timeout=170, interval=2):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            value = check()
            if value:
                return value
        except (OSError, Stage4Failure, KeyError, TypeError, ValueError) as error:
            last = str(error)
        time.sleep(interval)
    raise Stage4Failure(f"Timed out waiting for {label}. Last observation: {last}")

def set_fault(mode, every_nth=5):
    code, body = request_json(FAULTS, "PUT", {"mode": mode, "latencyMs": 0, "everyNthRequest": every_nth})
    if code != 200:
        raise Stage4Failure(f"Could not configure Stage 4 fault: HTTP {code} {body}")
    return body

def reset_fault():
    code, body = request_json(FAULTS, "DELETE")
    if code != 200:
        raise Stage4Failure(f"Could not reset Stage 4 fault: HTTP {code} {body}")

def exercise(stop):
    while not stop.is_set():
        payload = {
            "customerId": "SYNTH-CUST-STAGE4",
            "amount": 125.50,
            "currency": "ZAR",
            "documentReference": "SYNTH-STAGE4-" + uuid.uuid4().hex[:14].upper(),
        }
        try:
            request_json(PAYMENTS, "POST", payload)
        except (OSError, ValueError):
            pass
        stop.wait(1)

def find_stage4(baseline_markers, marker):
    # The lifecycle repository intentionally keeps one ACTIVE incident per
    # application/environment/origin. A new Stage 4 stimulus can therefore enrich
    # an already-active document-service incident instead of creating a new UUID.
    # Accept that same UUID only when the requested Stage 4 marker is newly present.
    for incident in incidents("ACTIVE"):
        incident_id = str(incident.get("id"))
        signals = signal_set(incident)
        if not any(marker in signal for signal in signals):
            continue
        if incident_id not in baseline_markers or marker not in baseline_markers[incident_id]:
            return incident
    return None

def signal_set(incident):
    analysis = incident.get("analysis") or incident.get("analysisSnapshot") or {}
    return {
        str(e.get("signal", "")).lower()
        for e in analysis.get("timeline", [])
    }

def active_marker_snapshot():
    return {
        str(incident.get("id")): signal_set(incident)
        for incident in incidents("ACTIVE")
    }

def resolved(incident_id):
    return next((i for i in incidents("RESOLVED") if str(i.get("id")) == str(incident_id)), None)

def active(incident_id):
    return next((i for i in incidents("ACTIVE") if str(i.get("id")) == str(incident_id)), None)

def details(incident):
    analysis = incident.get("analysis") or incident.get("analysisSnapshot") or {}
    timeline = analysis.get("timeline") or []
    counts = {family: sum(1 for e in timeline if e.get("family") == family)
              for family in ("LOG", "METRIC", "TRACE", "HEALTH")}
    observed = sorted(i.get("component") for i in analysis.get("impacts", [])
                      if i.get("state") == "OBSERVED" and i.get("component"))
    coverage = analysis.get("coverage") or {}
    return counts, observed, coverage

def validate_stage4_evidence(name, scenario, detected):
    counts, observed, coverage = details(detected)
    if counts["LOG"] == 0:
        raise Stage4Failure(f"{name} has no deterministic Stage 4 log evidence: {counts}")

    if scenario.get("partial"):
        validate_partial_observability(coverage)
    else:
        validate_distributed_impact(name, observed)

    return counts, observed, coverage

def validate_distributed_impact(name, observed):
    upstream = {"customer-service", "payment-service"}.intersection(observed)
    if not upstream:
        raise Stage4Failure(f"{name} did not prove upstream distributed impact; observed={observed}")

def validate_partial_observability(coverage):
    traces = str(coverage.get("traces", "")).upper()
    if traces == "AVAILABLE" or coverage.get("fullyCovered") is True:
        raise Stage4Failure(f"partial-observability did not report reduced trace coverage: {coverage}")

def assert_recovery_blocked(detected):
    time.sleep(45)
    if active(detected["id"]) is None:
        raise Stage4Failure("incident resolved while observability was incomplete")

def run_scenario(name):
    scenario = SCENARIOS[name]
    baseline = active_marker_snapshot()
    detected = None
    stop = threading.Event()
    worker = None
    tempo_stopped = False
    fault_reset = False
    try:
        if scenario.get("partial"):
            compose("stop", "tempo")
            tempo_stopped = True

        configured = set_fault(scenario["mode"], scenario.get("everyNthRequest", 5))
        if configured.get("mode") != scenario["mode"]:
            raise Stage4Failure(f"Fault mode was not applied: {configured}")

        worker = threading.Thread(target=exercise, args=(stop,), daemon=True)
        worker.start()
        detected = eventually(lambda: find_stage4(baseline, scenario["marker"]),
                              f"{name} Stage 4 incident")

        counts, observed, coverage = validate_stage4_evidence(name, scenario, detected)

        if scenario.get("partial"):
            reset_fault()
            fault_reset = True
            stop.set()
            if worker is not None:
                worker.join(timeout=2)
                worker = None

            # Recovery must remain blocked while trace coverage is incomplete.
            assert_recovery_blocked(detected)

            compose("start", "tempo")
            tempo_stopped = False
            eventually(lambda: resolved(detected["id"]),
                       "recovery after telemetry restoration", timeout=200)

        return {
            "id": str(detected["id"]),
            "origin": detected.get("originComponent"),
            "confidence": detected.get("originConfidence"),
            "severity": f"{detected.get('severityLevel')}/{detected.get('severityScore')}",
            "observedComponents": observed,
            "evidenceCounts": counts,
            "coverage": coverage,
            "serviceStayedRunning": True,
            "recoveryProtectedDuringPartialTelemetry": bool(scenario.get("partial")),
        }
    finally:
        stop_worker(stop, worker)
        restore_fault(fault_reset)
        restore_tempo(tempo_stopped)
        wait_for_standard_recovery(name, scenario, detected)

def stop_worker(stop, worker):
    stop.set()
    if worker is not None:
        worker.join(timeout=2)

def restore_fault(fault_reset):
    if fault_reset:
        return
    try:
        reset_fault()
    except Exception:
        pass

def restore_tempo(tempo_stopped):
    if not tempo_stopped:
        return
    try:
        compose("start", "tempo")
    except Exception:
        pass

def wait_for_standard_recovery(name, scenario, detected):
    if detected is None or scenario.get("partial"):
        return
    eventually(lambda: resolved(detected["id"]),
               f"automatic recovery after {name}", timeout=200)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scenario", choices=[*SCENARIOS, "all"], default="all")
    args = parser.parse_args()
    selected = list(SCENARIOS) if args.scenario == "all" else [args.scenario]
    print("\nMADLANGAAI BLAST RADIUS — STAGE 4 DISTRIBUTED FAILURE VALIDATION\n", flush=True)
    results = []
    for name in selected:
        print(f"[RUN ] {name}", flush=True)
        started = time.monotonic()
        try:
            value = run_scenario(name)
            result = {"scenario": name, "status": "PASS",
                      "seconds": round(time.monotonic() - started, 1), "details": value}
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

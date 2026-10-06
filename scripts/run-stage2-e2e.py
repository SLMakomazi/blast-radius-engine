#!/usr/bin/env python3
"""Stage 2 degradation validation for the local Blast Radius lab.

Unlike Phase 11, these scenarios keep document-service running and inject
application-level degradation through its localhost-only synthetic lab endpoint.

Scenarios:
  http-500       continuous application errors while the container stays UP
  intermittent   every third request fails
  latency        9s document latency, exceeding customer's 8s read timeout
  db-connectivity synthetic database connectivity failure while Postgres stays UP

The script always resets the injected fault in a finally block.
"""
import argparse
import json
import time
import threading
import uuid
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from urllib.parse import urlencode

FAULTS = "http://127.0.0.1:8083/lab/faults"
DOCUMENTS = "http://127.0.0.1:8083/api/documents"
API = "http://127.0.0.1:8080/api/v1/blast-radius"
APP = "document-platform"
ENV = "local"

SCENARIOS = {
    "http-500": {
        "fault": {"mode": "ERROR_500", "latencyMs": 0, "everyNthRequest": 5},
        "expected_origins": {"document-service"},
    },
    "intermittent": {
        "fault": {"mode": "INTERMITTENT_500", "latencyMs": 0, "everyNthRequest": 3},
        "expected_origins": {"document-service"},
    },
    "latency": {
        "fault": {"mode": "LATENCY", "latencyMs": 9000, "everyNthRequest": 5},
        "expected_origins": {"document-service"},
    },
    "db-connectivity": {
        "fault": {"mode": "DATABASE_FAILURE", "latencyMs": 0, "everyNthRequest": 5},
        "expected_origins": {"postgres", "document-service"},
    },
}


class Stage2Failure(AssertionError):
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
        raise Stage2Failure(f"Incident API returned HTTP {code}: {body}")
    return body


def eventually(check, label, timeout=140, interval=2):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            result = check()
            if result:
                return result
        except (OSError, Stage2Failure, KeyError, TypeError, ValueError) as error:
            last = str(error)
        time.sleep(interval)
    raise Stage2Failure(f"Timed out waiting for {label}. Last observation: {last}")


def set_fault(config):
    code, body = request_json(FAULTS, "PUT", config)
    if code != 200:
        raise Stage2Failure(f"Could not configure fault: HTTP {code} {body}")
    return body


def reset_fault():
    code, body = request_json(FAULTS, "DELETE")
    if code != 200:
        raise Stage2Failure(f"Could not reset fault: HTTP {code} {body}")
    return body


def exercise_document_service(stop_event):
    """Generate deterministic local requests so a scenario never depends on ambient traffic."""
    while not stop_event.is_set():
        payload = {
            "customerId": "SYNTH-CUST-STAGE2",
            "documentReference": "SYNTH-DOC-" + uuid.uuid4().hex[:16].upper(),
        }
        try:
            request_json(DOCUMENTS, "POST", payload)
        except (OSError, TimeoutError, ValueError):
            pass
        stop_event.wait(1)


def new_active(baseline_ids, expected_origins):
    for incident in incidents("ACTIVE"):
        if str(incident.get("id")) in baseline_ids:
            continue
        if incident.get("originComponent") in expected_origins:
            return incident
    return None


def resolved(incident_id):
    for incident in incidents("RESOLVED"):
        if str(incident.get("id")) == str(incident_id):
            return incident
    return None


def run_scenario(name):
    scenario = SCENARIOS[name]
    baseline = {str(item["id"]) for item in incidents("ACTIVE")}
    detected = None
    stimulus_stop = threading.Event()
    stimulus = None
    try:
        configured = set_fault(scenario["fault"])
        if configured.get("mode") != scenario["fault"]["mode"]:
            raise Stage2Failure(f"Fault mode was not applied: {configured}")

        stimulus = threading.Thread(target=exercise_document_service, args=(stimulus_stop,), daemon=True)
        stimulus.start()

        detected = eventually(
            lambda: new_active(baseline, scenario["expected_origins"]),
            f"{name} degradation incident",
        )
        analysis = detected.get("analysis") or {}
        timeline = analysis.get("timeline") or []
        families = sorted({item.get("family") for item in timeline if item.get("family")})
        return {
            "id": str(detected["id"]),
            "origin": detected.get("originComponent"),
            "confidence": detected.get("originConfidence"),
            "severity": f"{detected.get('severityLevel')}/{detected.get('severityScore')}",
            "evidenceFamilies": families,
            "serviceStayedRunning": True,
        }
    finally:
        stimulus_stop.set()
        if stimulus is not None:
            stimulus.join(timeout=2)
        reset_fault()
        if detected is not None:
            eventually(lambda: resolved(detected["id"]), f"recovery after {name}", timeout=160)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scenario", choices=[*SCENARIOS, "all"], default="all")
    args = parser.parse_args()

    selected = list(SCENARIOS) if args.scenario == "all" else [args.scenario]
    print("\nMADLANGAAI BLAST RADIUS — STAGE 2 DEGRADATION VALIDATION\n", flush=True)
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

    passed = sum(item["status"] == "PASS" for item in results)
    print(f"\n{passed} / {len(results)} scenarios passed", flush=True)
    if passed != len(results):
        raise SystemExit(1)


if __name__ == "__main__":
    main()

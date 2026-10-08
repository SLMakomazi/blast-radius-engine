#!/usr/bin/env python3
"""Degradation validation for the local Blast Radius lab.

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
PAYMENTS = "http://127.0.0.1:8081/api/payments"
API = "http://127.0.0.1:8080/api/v1/blast-radius"
APP = "document-platform"
ENV = "local"

SCENARIOS = {
    "http-500": {
        "fault": {"mode": "ERROR_500", "latencyMs": 0, "everyNthRequest": 5},
        "expected_origins": {"document-service"},
        "required_evidence": {"LOG", "METRIC", "TRACE"},
    },
    "intermittent": {
        "fault": {"mode": "INTERMITTENT_500", "latencyMs": 0, "everyNthRequest": 3},
        "expected_origins": {"document-service"},
        "required_evidence": {"LOG", "METRIC", "TRACE"},
    },
    "latency": {
        "fault": {"mode": "LATENCY", "latencyMs": 9000, "everyNthRequest": 5},
        "expected_origins": {"document-service"},
        "required_evidence": {"LOG", "METRIC", "TRACE"},
    },
    "db-connectivity": {
        "fault": {"mode": "DATABASE_FAILURE", "latencyMs": 0, "everyNthRequest": 5},
        "expected_origins": {"postgres", "document-service"},
        "required_evidence": {"LOG", "TRACE"},
    },
}


class DegradationValidationFailure(AssertionError):
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
        raise DegradationValidationFailure(f"Incident API returned HTTP {code}: {body}")
    return body

def eventually(check, label, timeout, interval=2):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            value = check()
            if value:
                return value
        except (OSError, DegradationValidationFailure, KeyError, TypeError, ValueError) as error:
            last = str(error)
        time.sleep(interval)
    raise DegradationValidationFailure(f"Timed out waiting for {label}. Last observation: {last}")

def set_fault(config):
    code, body = request_json(FAULTS, "PUT", config)
    if code != 200:
        raise DegradationValidationFailure(f"Could not configure fault: HTTP {code} {body}")
    return body


def reset_fault():
    code, body = request_json(FAULTS, "DELETE")
    if code != 200:
        raise DegradationValidationFailure(f"Could not reset fault: HTTP {code} {body}")
    return body


def exercise_full_chain(stop_event):
    """Drive payment -> customer -> document -> postgres for real propagation evidence."""
    while not stop_event.is_set():
        payload = {
            "customerId": "SYNTH-CUST-DEGRADATION",
            "amount": 125.50,
            "currency": "ZAR",
            "documentReference": "SYNTH-DOC-" + uuid.uuid4().hex[:16].upper(),
        }
        try:
            request_json(PAYMENTS, "POST", payload)
        except (OSError, TimeoutError, ValueError):
            pass
        stop_event.wait(1)


def run_scenario(name):
    scenario = SCENARIOS[name]
    baseline_ids = {str(item["id"]) for item in incidents("ACTIVE")}
    stimulus_stop = threading.Event()
    stimulus = None
    configured = False
    try:
        configured_fault = set_fault(scenario["fault"])
        configured = True
        if configured_fault.get("mode") != scenario["fault"]["mode"]:
            raise DegradationValidationFailure(f"Fault mode was not applied: {configured_fault}")
        started_at = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        stimulus = threading.Thread(target=exercise_full_chain, args=(stimulus_stop,), daemon=True)
        stimulus.start()

        def observed_degradation():
            new_ids = {str(item["id"]) for item in incidents("ACTIVE")} - baseline_ids
            if new_ids:
                raise AssertionError(f"Degradation created unexpected outage incidents: {sorted(new_ids)}")
            code, health = request_json("http://127.0.0.1:8083/actuator/health/liveness")
            if code != 200 or health.get("status") != "UP":
                raise AssertionError("The degraded service must remain alive")
            code, analysis = request_json(f"{API}/analyze", "POST", {
                "applicationId": APP, "environment": ENV, "from": started_at,
                "to": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            })
            if code != 200:
                return None
            if (analysis.get("origin") or {}).get("component") not in scenario["expected_origins"]:
                return None
            timeline = analysis.get("timeline") or []
            families = {e.get("family") for e in timeline if e.get("kind") != "AVAILABILITY_AVAILABLE"}
            if not scenario["required_evidence"].issubset(families):
                return None
            if any(e.get("kind") == "AVAILABILITY_UNAVAILABLE" for e in timeline):
                raise AssertionError("Degradation scenario unexpectedly includes a direct outage")
            return analysis

        analysis = eventually(observed_degradation, f"{name} real degradation evidence with no outage", timeout=120)
        # Allow another scheduler evaluation and verify liveness and the outage gate again.
        time.sleep(12)
        latest = observed_degradation()
        if latest is None:
            raise DegradationValidationFailure("Required real telemetry was not retained in the scenario window")
        timeline = latest.get("timeline") or []
        return {
            "origin": latest["origin"]["component"],
            "evidenceCounts": {family: sum(e.get("family") == family for e in timeline)
                               for family in ("LOG", "METRIC", "TRACE", "HEALTH")},
            "serviceStayedRunning": True,
            "outageIncidentsCreated": 0,
        }
    finally:
        stimulus_stop.set()
        if stimulus is not None:
            stimulus.join(timeout=2)
        if configured:
            reset_fault()
            def requests_recovered():
                code, _ = request_json(PAYMENTS, "POST", {
                    "customerId": "SYNTH-CUST-RECOVERY", "amount": 125.50, "currency": "ZAR",
                    "documentReference": "SYNTH-RECOVERY-" + uuid.uuid4().hex[:16].upper(),
                })
                return 200 <= code < 300
            eventually(requests_recovered, f"successful full-chain request after {name}", timeout=160)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scenario", choices=[*SCENARIOS, "all"], default="all")
    args = parser.parse_args()

    selected = list(SCENARIOS) if args.scenario == "all" else [args.scenario]
    print("\nMADLANGAAI BLAST RADIUS — DEGRADATION VALIDATION\n", flush=True)
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

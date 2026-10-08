#!/usr/bin/env python3
"""Phase 11 end-to-end acceptance suite for the local Blast Radius lab.

This script intentionally stops local Compose services to create controlled
availability failures. It never removes volumes and restores every service in
finally blocks. Gemini is not required.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
API = "http://127.0.0.1:8080/api/v1/blast-radius"
APP = "document-platform"
ENV = "local"

SCENARIOS = {
    "postgres": {"origin": "postgres", "max_depth": 3},
    "document-service": {"origin": "document-service", "max_depth": 2},
    "customer-service": {"origin": "customer-service", "max_depth": 1},
    "payment-service": {"origin": "payment-service", "max_depth": 0},
}


class Phase11Failure(AssertionError):
    pass


def request_json(url):
    try:
        with urlopen(Request(url, headers={"Accept": "application/json"}), timeout=8) as response:
            return response.status, json.load(response)
    except HTTPError as error:
        with error:
            try:
                return error.code, json.load(error)
            except json.JSONDecodeError:
                return error.code, {}


def compose(runtime, *args):
    subprocess.run([runtime, "compose", *args], cwd=ROOT, check=True, timeout=120)


def incidents(status):
    query = urlencode({"applicationId": APP, "environment": ENV, "status": status})
    code, body = request_json(f"{API}/incidents?{query}")
    if code != 200 or not isinstance(body, list):
        raise Phase11Failure(f"Incident API returned HTTP {code}: {body}")
    return body


def eventually(check, label, timeout=100, interval=2):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            result = check()
            if result:
                return result
        except (OSError, Phase11Failure, KeyError, TypeError, ValueError) as error:
            last = str(error)
        time.sleep(interval)
    raise Phase11Failure(f"Timed out waiting for {label}. Last observation: {last}")


def wait_api():
    def api_is_up():
        status, body = request_json("http://127.0.0.1:8080/actuator/health")
        return status == 200 and isinstance(body, dict) and body.get("status") == "UP"

    return eventually(
        api_is_up,
        "Blast Radius API health",
        timeout=120,
    )


def active_for_origin(origin):
    matches = [item for item in incidents("ACTIVE") if item.get("originComponent") == origin]
    return matches[0] if matches else None


def resolved_by_id(incident_id):
    matches = [item for item in incidents("RESOLVED") if str(item.get("id")) == str(incident_id)]
    return matches[0] if matches else None


def analysis_of(incident):
    return incident.get("analysis") or {}


def assert_incident_shape(incident, expected):
    origin = incident.get("originComponent")
    if origin != expected["origin"]:
        raise Phase11Failure(f"Expected origin {expected['origin']}, got {origin}")

    analysis = analysis_of(incident)
    if incident.get("incidentType") != "OUTAGE" or incident.get("availabilityStatus") != "UNAVAILABLE":
        raise Phase11Failure("Direct outage must be classified OUTAGE / UNAVAILABLE")
    if not any(e.get("kind") == "AVAILABILITY_UNAVAILABLE" for e in (analysis.get("origin") or {}).get("evidence", [])):
        raise Phase11Failure("Origin lacks direct availability confirmation")
    impacts = analysis.get("impacts") or []
    observed = [item for item in impacts if item.get("state") == "OBSERVED"]
    max_depth = max((item.get("distance") or 0 for item in observed), default=0)

    if max_depth > expected["max_depth"]:
        raise Phase11Failure(
            f"Observed propagation depth {max_depth} exceeds topology expectation {expected['max_depth']}"
        )

    if expected["origin"] == "payment-service" and observed:
        raise Phase11Failure(f"Payment outage invented downstream observed impact: {observed}")

    if not incident.get("severityLevel"):
        raise Phase11Failure("Incident has no deterministic severity")
    if incident.get("severityScore") is None:
        raise Phase11Failure("Incident has no deterministic severity score")

    return {
        "id": str(incident["id"]),
        "origin": origin,
        "confidence": str(incident.get("originConfidence")),
        "severity": f"{incident.get('severityLevel')}/{incident.get('severityScore')}",
        "observed": [item.get("component") for item in observed],
        "maxObservedDepth": max_depth,
    }


def wait_no_new_active(baseline_ids, seconds=25):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        new_ids = {str(item["id"]) for item in incidents("ACTIVE")} - baseline_ids
        if new_ids:
            raise Phase11Failure(f"Healthy baseline created unexpected ACTIVE incident(s): {sorted(new_ids)}")
        time.sleep(2)


def healthy_baseline():
    baseline_ids = {str(item["id"]) for item in incidents("ACTIVE")}
    wait_no_new_active(baseline_ids)
    return {"activeBefore": len(baseline_ids), "newIncidents": 0}


def outage(runtime, service):
    expected = SCENARIOS[service]
    baseline_ids = {str(item["id"]) for item in incidents("ACTIVE")}
    detected = None
    try:
        compose(runtime, "stop", service)

        def detect():
            candidate = active_for_origin(expected["origin"])
            if candidate and str(candidate["id"]) not in baseline_ids:
                return candidate
            return None

        detected = eventually(detect, f"proactive {service} incident")
        summary = assert_incident_shape(detected, expected)
        incident_id = str(detected["id"])

        # Repeated lifecycle evaluations must update the same active incident.
        time.sleep(12)
        same_origin = [i for i in incidents("ACTIVE") if i.get("originComponent") == expected["origin"]]
        ids = {str(i["id"]) for i in same_origin}
        if ids != {incident_id}:
            raise Phase11Failure(f"Expected one stable ACTIVE incident UUID {incident_id}, got {sorted(ids)}")
        current = same_origin[0]
        if current.get("startedAt") != detected.get("startedAt"):
            raise Phase11Failure("Polling changed the original incident start")
        original_keys = {json.dumps(e, sort_keys=True) for e in analysis_of(detected).get("timeline", [])}
        current_keys = {json.dumps(e, sort_keys=True) for e in analysis_of(current).get("timeline", [])}
        if not original_keys.issubset(current_keys):
            raise Phase11Failure("Polling lost previously collected evidence")
    finally:
        compose(runtime, "start", service)

    if detected is None:
        raise Phase11Failure(f"No {service} incident was detected")

    resolved = eventually(lambda: resolved_by_id(detected["id"]), f"automatic resolution of {service}", timeout=120)
    if not resolved.get("resolvedAt"):
        raise Phase11Failure("Resolved incident has no resolvedAt timestamp")
    retained = {json.dumps(e, sort_keys=True) for e in analysis_of(resolved).get("timeline", [])}
    if not original_keys.issubset(retained):
        raise Phase11Failure("Resolution lost historical evidence")
    summary["lifecycle"] = "ACTIVE -> RESOLVED"
    summary["sameUuidResolved"] = True
    return summary


def partial_observability(runtime):
    """Prove missing telemetry cannot falsely resolve an active incident."""
    baseline_ids = {str(item["id"]) for item in incidents("ACTIVE")}
    incident = None
    tempo_stopped = False
    customer_stopped = False
    try:
        compose(runtime, "stop", "customer-service")
        customer_stopped = True

        def detect():
            candidate = active_for_origin("customer-service")
            if candidate and str(candidate["id"]) not in baseline_ids:
                return candidate
            return None

        incident = eventually(detect, "customer incident for partial-observability test")
        incident_id = str(incident["id"])

        compose(runtime, "stop", "tempo")
        tempo_stopped = True
        compose(runtime, "start", "customer-service")
        customer_stopped = False

        # Three healthy windows would normally resolve in ~30s. With the trace
        # provider unavailable, the incident must remain ACTIVE.
        time.sleep(45)
        still_active = active_for_origin("customer-service")
        if not still_active or str(still_active["id"]) != incident_id:
            raise Phase11Failure("Incident resolved while telemetry coverage was partial")

        coverage = analysis_of(still_active).get("coverage") or {}
        if coverage.get("fullyCovered") is True:
            raise Phase11Failure(f"Expected partial telemetry coverage, got {coverage}")

        return {
            "id": incident_id,
            "coverage": "PARTIAL",
            "remainedActive": True,
            "automaticRecoveryBlocked": True,
        }
    finally:
        if customer_stopped:
            compose(runtime, "start", "customer-service")
        if tempo_stopped:
            compose(runtime, "start", "tempo")
        if incident is not None:
            eventually(lambda: resolved_by_id(incident["id"]), "resolution after telemetry restoration", timeout=150)


def run(name, function):
    started = time.monotonic()
    try:
        details = function()
        return {"scenario": name, "status": "PASS", "seconds": round(time.monotonic() - started, 1), "details": details}
    except Exception as error:
        return {"scenario": name, "status": "FAIL", "seconds": round(time.monotonic() - started, 1), "error": str(error)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", default=os.environ.get("CONTAINER_RUNTIME", "docker"))
    parser.add_argument(
        "--scenario",
        choices=["healthy", *SCENARIOS, "partial-observability", "all"],
        default="all",
        help="Run one scenario or the complete acceptance suite",
    )
    args = parser.parse_args()

    wait_api()
    selected = args.scenario
    cases = [
        ("Healthy baseline", healthy_baseline),
        ("PostgreSQL outage", lambda: outage(args.runtime, "postgres")),
        ("Document service outage", lambda: outage(args.runtime, "document-service")),
        ("Customer service outage", lambda: outage(args.runtime, "customer-service")),
        ("Payment service outage", lambda: outage(args.runtime, "payment-service")),
        ("Partial observability", lambda: partial_observability(args.runtime)),
    ]
    key = {
        "healthy": "Healthy baseline",
        "postgres": "PostgreSQL outage",
        "document-service": "Document service outage",
        "customer-service": "Customer service outage",
        "payment-service": "Payment service outage",
        "partial-observability": "Partial observability",
    }
    if selected != "all":
        cases = [case for case in cases if case[0] == key[selected]]

    print("\nMADLANGAAI BLAST RADIUS — PHASE 11 E2E VALIDATION\n", flush=True)
    results = []
    for name, function in cases:
        print(f"[RUN ] {name}", flush=True)
        result = run(name, function)
        results.append(result)
        print(f"[{result['status']:^5}] {name}", flush=True)
        print(json.dumps(result.get("details") or {"error": result.get("error")}, indent=2), flush=True)

    passed = sum(item["status"] == "PASS" for item in results)
    print(f"\n{passed} / {len(results)} scenarios passed", flush=True)
    if passed != len(results):
        raise SystemExit(1)


if __name__ == "__main__":
    main()

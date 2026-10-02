#!/usr/bin/env python3
"""Verify the real lab, including an explicitly destructive-to-availability DB stop.

Run against the local synthetic Compose stack only. PostgreSQL is restarted in a
finally block, even if an assertion fails. No volumes are removed.
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import uuid

ROOT = Path(__file__).resolve().parents[1]
PODMAN = os.environ.get("PODMAN", shutil.which("podman") or "/opt/podman/bin/podman")


def compose(*args):
    result = subprocess.run([PODMAN, "compose", *args], cwd=ROOT, check=True,
                            text=True, stdout=subprocess.PIPE)
    return result.stdout.strip()


def get_health(port, path="/actuator/health"):
    try:
        with urlopen(f"http://127.0.0.1:{port}{path}", timeout=5) as response:
            return response.status, json.load(response)["status"]
    except HTTPError as error:
        with error:
            return error.code, json.load(error)["status"]
    except (URLError, TimeoutError):
        return 0, "UNREACHABLE"


def wait_healthy(port):
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        if get_health(port) == (200, "UP"):
            return
        time.sleep(2)
    raise AssertionError(f"Port {port} did not become healthy")


def pay(correlation):
    request = Request("http://127.0.0.1:8081/api/payments",
                      data=(ROOT / "fixtures/payment-request.json").read_bytes(), method="POST",
                      headers={"Content-Type": "application/json", "X-Correlation-ID": correlation})
    try:
        response = urlopen(request, timeout=20)
    except HTTPError as error:
        response = error
    with response:
        result = (response.status, dict(response.headers), json.load(response))
    assert result[1].get("X-Correlation-ID") == correlation
    assert result[2]["correlationId"] == correlation
    return result


def count_rows(correlation):
    # IDs are generated locally from UUID hex; never interpolate caller input into SQL.
    assert all(c.isalnum() or c == "-" for c in correlation)
    query = f"SELECT count(*) FROM synthetic_documents WHERE correlation_id = '{correlation}'"
    return int(compose("exec", "-T", "postgres", "sh", "-c",
                       'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "$1"', "sh", query))


def main():
    suffix = uuid.uuid4().hex[:12]
    healthy_id, failure_id, recovery_id = [f"phase2-{phase}-{suffix}" for phase in ("healthy", "failure", "recovery")]
    for port in (8080, 8081, 8082, 8083):
        wait_healthy(port)
    print("All four Spring health endpoints: UP", flush=True)
    result = pay(healthy_id)
    assert result[0] == 201, result
    assert result[2]["document"]["correlationId"] == healthy_id
    assert count_rows(healthy_id) == 1
    print("HEALTHY", json.dumps(result[2]), "persistedRows=1", flush=True)
    try:
        compose("stop", "postgres")
        result = pay(failure_id)
        assert result[0] == 502, result
        assert result[2]["dependency"] == "customer-service"
        assert result[2]["downstreamStatus"] == 502
        assert get_health(8083, "/actuator/health/readiness") == (503, "DOWN")
        assert get_health(8083, "/actuator/health/liveness") == (200, "UP")
        assert get_health(8080) == (200, "UP")
        print("FAILURE", json.dumps(result[2]), "documentReadiness=DOWN liveness=UP api=UP", flush=True)
        for service, dependency in (("document-service", "postgres"), ("customer-service", "document-service"), ("payment-service", "customer-service")):
            lines = [line for line in compose("logs", "--no-color", service).splitlines()
                     if failure_id in line and "event=dependency_failed" in line]
            assert any(f"dependency={dependency}" in line for line in lines), (service, lines)
            print("FAILURE_LOG", *lines, sep="\n", flush=True)
    finally:
        compose("start", "postgres")
        wait_healthy(8083)
    result = pay(recovery_id)
    assert result[0] == 201, result
    assert result[2]["document"]["correlationId"] == recovery_id
    assert count_rows(recovery_id) == 1
    assert count_rows(failure_id) == 0
    assert count_rows(healthy_id) == 1
    print("RECOVERY", json.dumps(result[2]), "persistedRows=1 failedRequestRows=0 originalRowRetained=1", flush=True)
    print(compose("ps"), flush=True)
    print("Phase 2 healthy -> PostgreSQL failure -> recovery verification PASSED", flush=True)


if __name__ == "__main__":
    main()

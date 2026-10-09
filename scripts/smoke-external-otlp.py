#!/usr/bin/env python3
"""Local-only OTLP/HTTP -> Collector -> Tempo smoke test (stdlib only).

Never send real application data to this script. It emits a synthetic span.
"""
import json
import os
import secrets
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

OTLP = os.environ.get("OTLP_SMOKE_URL", "http://127.0.0.1:4318/v1/traces")
TEMPO = os.environ.get("TEMPO_SMOKE_URL", "http://127.0.0.1:3200")
if OTLP != "http://127.0.0.1:4318/v1/traces" or TEMPO != "http://127.0.0.1:3200":
    sys.exit("Refusing non-loopback endpoints; this test is restricted to the local lab.")

trace_id = secrets.token_hex(16)
span_id = secrets.token_hex(8)
now = time.time_ns()
payload = {
    "resourceSpans": [{
        "resource": {"attributes": [
            {"key": "service.name", "value": {"stringValue": "wil-evaluation-backend"}},
            {"key": "deployment.environment.name", "value": {"stringValue": "local-smoke"}}
        ]},
        "scopeSpans": [{
            "scope": {"name": "madlanga.local.smoke"},
            "spans": [{
                "traceId": trace_id,
                "spanId": span_id,
                "name": "GET /api/health/live",
                "kind": 2,
                "startTimeUnixNano": str(now),
                "endTimeUnixNano": str(now + 1_000_000),
                "attributes": [
                    {"key": "http.request.method", "value": {"stringValue": "GET"}},
                    {"key": "http.route", "value": {"stringValue": "/api/health/live"}},
                    {"key": "http.response.status_code", "value": {"intValue": "200"}}
                ],
                "status": {"code": 1}
            }]
        }]
    }]
}
request = urllib.request.Request(
    OTLP,
    data=json.dumps(payload).encode("utf-8"),
    headers={"Content-Type": "application/json"},
    method="POST",
)
try:
    with urllib.request.urlopen(request, timeout=5) as response:
        print("Collector accepted synthetic trace:", response.status, "traceId:", trace_id)
except (urllib.error.URLError, TimeoutError) as exc:
    sys.exit(f"FAIL: Collector did not accept trace: {exc}")

for attempt in range(1, 16):
    time.sleep(2)
    try:
        with urllib.request.urlopen(f"{TEMPO}/api/traces/{trace_id}", timeout=5) as response:
            result = json.load(response)
        batches = result.get("batches", [])
        if batches:
            print(f"PASS: Tempo returned trace {trace_id} after {attempt * 2}s")
            sys.exit(0)
    except (urllib.error.URLError, TimeoutError, ValueError):
        pass

sys.exit(f"FAIL: Collector accepted the trace but Tempo did not return {trace_id} within 30 seconds")

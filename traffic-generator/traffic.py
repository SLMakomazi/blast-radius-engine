"""Continuous synthetic transactions; no downstream access or automatic retries."""
import json
import os
import signal
import threading
import uuid
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


def send_transaction(target):
    correlation_id = str(uuid.uuid4())
    payload = {
        "customerId": "SYNTH-CUST-001",
        "amount": 125.50,
        "currency": "ZAR",
        "documentReference": "SYNTH-DOC-" + uuid.uuid4().hex.upper(),
    }
    request = Request(target, data=json.dumps(payload).encode(), method="POST",
                      headers={"Content-Type": "application/json", "X-Correlation-ID": correlation_id})
    try:
        with urlopen(request, timeout=20) as response:
            status = response.status
    except HTTPError as error:
        status = error.code
        error.close()
    except (URLError, TimeoutError, OSError):
        status = "CONNECTION_FAILURE"
    # Do not log payloads, URLs, raw response bodies or exception details.
    print(json.dumps({"event": "synthetic_transaction", "correlationId": correlation_id,
                      "status": status}), flush=True)
    return status


def settings(environ):
    target = environ.get("TARGET_URL", "http://payment-service:8081/api/payments")
    parsed = urlsplit(target)
    if parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username or parsed.password:
        raise ValueError("TARGET_URL must be an HTTP(S) URL without credentials")
    interval = float(environ.get("REQUEST_INTERVAL", "2"))
    if not 0.1 <= interval <= 3600:
        raise ValueError("REQUEST_INTERVAL must be between 0.1 and 3600 seconds")
    enabled = environ.get("ENABLED", "true").lower()
    if enabled not in ("true", "false"):
        raise ValueError("ENABLED must be true or false")
    return target, interval, enabled == "true"


def main():
    target, interval, enabled = settings(os.environ)
    stopped = threading.Event()
    for sig in (signal.SIGTERM, signal.SIGINT):
        signal.signal(sig, lambda *_: stopped.set())
    if not enabled:
        print('{"event":"traffic_disabled"}', flush=True)
        stopped.wait()
        return
    while not stopped.is_set():
        send_transaction(target)
        stopped.wait(interval)


if __name__ == "__main__":
    main()

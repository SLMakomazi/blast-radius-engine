import io
import json
import unittest
from contextlib import redirect_stdout
from unittest.mock import MagicMock, patch
from urllib.error import HTTPError, URLError
import traffic


class TrafficTests(unittest.TestCase):
    @patch("traffic.urlopen")
    def test_sends_synthetic_payment_and_correlation(self, urlopen):
        response = MagicMock()
        response.__enter__.return_value.status = 201
        urlopen.return_value = response
        with redirect_stdout(io.StringIO()):
            self.assertEqual(traffic.send_transaction("http://payment-service:8081/api/payments"), 201)
        request = urlopen.call_args.args[0]
        self.assertEqual(request.full_url, "http://payment-service:8081/api/payments")
        self.assertEqual(request.method, "POST")
        self.assertTrue(request.get_header("X-correlation-id"))
        self.assertTrue(json.loads(request.data)["documentReference"].startswith("SYNTH-DOC-"))

    @patch("traffic.urlopen", side_effect=HTTPError("http://payment-service", 502, "private", {}, io.BytesIO(b"untrusted")))
    def test_reports_http_failure_without_response_details(self, _):
        output = io.StringIO()
        with redirect_stdout(output):
            self.assertEqual(traffic.send_transaction("http://payment-service:8081/api/payments"), 502)
        self.assertNotIn("private", output.getvalue())

    @patch("traffic.urlopen", side_effect=URLError("private"))
    def test_reports_connection_failure(self, _):
        with redirect_stdout(io.StringIO()):
            self.assertEqual(traffic.send_transaction("http://payment-service:8081/api/payments"), "CONNECTION_FAILURE")

    def test_configuration_and_disabled_mode(self):
        self.assertEqual(traffic.settings({"ENABLED": "false", "REQUEST_INTERVAL": "3"})[1:], (3.0, False))
        for env in ({"REQUEST_INTERVAL": "0"}, {"ENABLED": "maybe"}, {"TARGET_URL": "file:///tmp/x"}):
            with self.assertRaises(ValueError):
                traffic.settings(env)


if __name__ == "__main__":
    unittest.main()

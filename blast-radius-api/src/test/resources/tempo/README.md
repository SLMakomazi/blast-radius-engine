# Captured synthetic lab evidence

Raw OTLP JSON retrieved directly from Tempo during the 2026-10-02 investigation.
The Collector had already removed SQL text, credentials, exception messages and stack traces.
These are test/bootstrap evidence, never runtime topology configuration.

- `healthy-database.json`: trace `00213806d536843849e3cc53fdcd1534`, healthy payment/customer/document/SQL chain. The INTERNAL connection-pool span has technology only; CLIENT SQL spans identify the concrete database.
- `failed-connection.json`: trace `09bd2780ed21d691b741ce42cc386a95`, failed chain. The INTERNAL connection-pool span has ERROR status and no peer attributes.

Keep original span timestamps, kinds, parent relationships and attributes; do not fabricate current evidence from these fixtures.

import com.madlanga.blastradius.adapters.persistence.FileTopologyStore;
import com.madlanga.blastradius.adapters.telemetry.tempo.*;
import com.madlanga.blastradius.adapters.topology.RetainedTopologyProvider;
import com.madlanga.blastradius.domain.evidence.*;
import com.madlanga.blastradius.sanitization.TelemetrySanitizer;
import com.sun.net.httpserver.HttpServer;
import org.springframework.web.client.RestClient;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.time.*;
import java.util.List;

/** Offline acceptance helper. Never packaged into the application or run at application startup. */
class BootstrapCapturedTopology {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException(
                "Usage: BootstrapCapturedTopology.java <captured-OTLP-json> <offline-store-directory> <applicationId> <environment>");
        byte[] payload = Files.readAllBytes(Path.of(args[0]));
        var tree = tools.jackson.databind.json.JsonMapper.builder().build().readTree(payload);
        String encodedId = tree.get("batches").get(0).get("scopeSpans").get(0).get("spans").get(0).get("traceId").asString();
        String traceId = encodedId.matches("[0-9a-fA-F]{32}") ? encodedId.toLowerCase(java.util.Locale.ROOT)
                : java.util.HexFormat.of().formatHex(java.util.Base64.getDecoder().decode(encodedId));
        // Loopback replay exercises the real adapter, including sanitization and identity rules.
        HttpServer replay = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        replay.createContext("/api/traces/" + traceId, exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (var body = exchange.getResponseBody()) { body.write(payload); }
        });
        replay.start();
        try {
            TempoProperties properties = new TempoProperties();
            properties.setBaseUrl("http://127.0.0.1:" + replay.getAddress().getPort());
            properties.setProviderId("captured-tempo-bootstrap");
            var adapter = new TempoTraceAdapter(properties, new TelemetrySanitizer(), RestClient.builder());
            Instant now = Instant.now();
            var result = adapter.fetchSpans(TelemetryQuery.builder().applicationId(args[2]).environment(args[3])
                    .from(Instant.EPOCH).to(now).traceId(traceId).build());
            if (result.getCoverage() != CoverageStatus.AVAILABLE) throw new IllegalStateException("Captured evidence could not be normalized");
            // Match the documented default, optionally overridden for the target lab's actual TTL.
            Duration ttl = Duration.parse(System.getenv().getOrDefault("BOOTSTRAP_TOPOLOGY_TTL", "P7D"));
            var provider = new RetainedTopologyProvider(new FileTopologyStore(Path.of(args[1])), q -> List.of(),
                    Clock.systemUTC(), ttl, Duration.ofMinutes(5));
            var topology = provider.learn(args[2], args[3], result.getSpans());
            if (topology.getEdges().isEmpty()) throw new IllegalStateException("No unexpired dependencies discovered; no timestamps were refreshed");
            System.out.println("Offline bootstrap only; original observation times preserved.");
            topology.getNodes().forEach(n -> System.out.println("NODE " + n.getId() + " " + n.getType() + " " + n.getTechnology()));
            topology.getEdges().forEach(e -> System.out.println("EDGE " + e.getDependentId() + " -> " + e.getDependencyId()));
        } finally { replay.stop(0); }
    }
}

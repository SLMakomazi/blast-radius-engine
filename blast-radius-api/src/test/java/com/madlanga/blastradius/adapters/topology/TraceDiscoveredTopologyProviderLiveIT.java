package com.madlanga.blastradius.adapters.topology;

import static org.assertj.core.api.Assertions.assertThat;

import com.madlanga.blastradius.domain.topology.DependencyTopology;
import com.madlanga.blastradius.ports.TelemetryProvider;
import com.madlanga.blastradius.domain.topology.DeterministicGraphEngine;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "blast-radius.telemetry.loki.base-url=http://localhost:3100",
        "blast-radius.telemetry.prometheus.base-url=http://localhost:9090",
        "blast-radius.telemetry.tempo.base-url=http://localhost:3200",
        "blast-radius.telemetry.health.endpoints.blast-radius-api=http://localhost:8080",
        "blast-radius.telemetry.health.endpoints.payment-service=http://localhost:8081",
        "blast-radius.telemetry.health.endpoints.customer-service=http://localhost:8082",
        "blast-radius.telemetry.health.endpoints.document-service=http://localhost:8083"
})
class TraceDiscoveredTopologyProviderLiveIT {

    @Autowired
    private TelemetryProvider telemetryProvider;

    @Test
    void discoversCanonicalDependencyChainFromRealTempoEvidence() {
        TraceDiscoveredTopologyProvider provider =
                new TraceDiscoveredTopologyProvider(telemetryProvider, Clock.systemUTC(), Duration.ofMinutes(30));

        DependencyTopology topology = provider.getTopology("document-platform", "local");

        assertThat(topology.getEdges()).extracting(e -> e.getDependentId() + "->" + e.getDependencyId())
                .contains(
                        "payment-service->customer-service",
                        "customer-service->document-service",
                        "document-service->postgres");

        assertThat(new DeterministicGraphEngine().calculate(topology, "postgres").getImpacts())
                .extracting(i -> i.getComponent().getId())
                .containsSubsequence("document-service", "customer-service", "payment-service");
    }
}

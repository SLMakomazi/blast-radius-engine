package com.madlanga.blastradius.topology.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.madlanga.blastradius.topology.model.DependencyTopology;
import com.madlanga.blastradius.telemetry.provider.TelemetryProvider;
import com.madlanga.blastradius.topology.model.DeterministicGraphEngine;
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
class TraceTopologyServiceLiveIT {

    @Autowired
    private TelemetryProvider telemetryProvider;

    @Test
    void discoversCanonicalDependencyChainFromRealTempoEvidence() {
        TraceTopologyService provider =
                new TraceTopologyService(telemetryProvider, Clock.systemUTC(), Duration.ofMinutes(30));

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

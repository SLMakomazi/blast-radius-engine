package com.madlanga.blastradius.topology.config;

import com.madlanga.blastradius.incident.service.IncidentAnalysisService;
import com.madlanga.blastradius.incident.model.ComponentImpact;
import com.madlanga.blastradius.telemetry.model.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConfiguredTopologyTest {
    @Test void secondApplicationUsesSameAnalysisWithoutInventingDownstreamFailures() {
        var config = new ConfiguredTopology();
        config.setComponents(List.of("ledger-api", "ledger-worker", "ledger-db"));
        config.setDependencies(Map.of("ledger-api",List.of("ledger-worker"),"ledger-worker",List.of("ledger-db")));
        var topology = config.build("ledger", "staging");
        Instant now = Instant.now();
        var down = HealthEvidence.builder().id("probe").timestamp(now.minusSeconds(1)).service("ledger-db")
                .endpoint("database/availability").state(HealthState.DOWN)
                .provenance(EvidenceProvenance.of(EvidenceFamily.HEALTH,"jdbc",now,"database/availability")).build();
        var bundle = TelemetryBundle.builder().health(List.of(down)).coverage(TelemetryCoverage.builder()
                .health(CoverageStatus.AVAILABLE).logs(CoverageStatus.UNAVAILABLE).build()).build();
        var engine = new IncidentAnalysisService(q -> bundle, (app,env) -> topology);
        var result = engine.analyze("ledger","staging",now.minusSeconds(20),now,null);
        assertThat(result.origin().component()).isEqualTo("ledger-db");
        assertThat(result.origin().evidence()).anyMatch(e -> e.confirmsUnavailable());
        assertThat(result.impacts()).filteredOn(i -> !i.component().equals("ledger-db"))
                .allMatch(i -> i.state() == ComponentImpact.State.UNKNOWN && i.evidence().isEmpty());
    }
    @Test void readinessDownWhileLivenessUpIsNotAnAvailabilityFailure() {
        var config = new ConfiguredTopology(); config.setComponents(List.of("api"));
        var topology = config.build("other", "test");
        Instant now = Instant.now();
        var provenance = EvidenceProvenance.of(EvidenceFamily.HEALTH,"http",now,"liveness");
        var readiness = HealthEvidence.builder().id("ready").timestamp(now.minusSeconds(1)).service("api")
                .endpoint("/actuator/health/readiness").state(HealthState.DOWN).provenance(provenance).build();
        var liveness = HealthEvidence.builder().id("live").timestamp(now.minusSeconds(1)).service("api")
                .endpoint("/actuator/health/liveness").state(HealthState.UP).provenance(provenance).build();
        var engine = new IncidentAnalysisService(q -> TelemetryBundle.builder().health(List.of(readiness,liveness))
                .coverage(TelemetryCoverage.allAvailable()).build(), (app,env) -> topology);
        var result = engine.analyze("other","test",now.minusSeconds(20),now,null);
        assertThat(result.origin().evidence()).noneMatch(e -> e.confirmsUnavailable());
        assertThat(result.timeline()).anyMatch(e -> e.kind() == com.madlanga.blastradius.incident.model.EvidenceSignal.Kind.AVAILABILITY_AVAILABLE);
    }

    @Test void profilePropertiesBindComponentsAndDependencies() {
        var source = new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(Map.of(
                "blast-radius.topology.configured.components[0]", "checkout",
                "blast-radius.topology.configured.components[1]", "inventory",
                "blast-radius.topology.configured.dependencies.checkout[0]", "inventory"));
        var config = new org.springframework.boot.context.properties.bind.Binder(source)
                .bind("blast-radius.topology.configured", ConfiguredTopology.class).get();
        var topology = config.build("retail", "production");
        assertThat(topology.getEdges()).hasSize(1);
        assertThat(topology.getEdges().getFirst().getDependencyId()).isEqualTo("inventory");
    }

    @Test void invalidConfiguredEdgesAreRejected() {
        var config = new ConfiguredTopology();
        config.setComponents(List.of("api"));
        config.setDependencies(Map.of("api", List.of("invented-db")));
        assertThatThrownBy(() -> config.build("other","test")).isInstanceOf(IllegalArgumentException.class);
    }
}

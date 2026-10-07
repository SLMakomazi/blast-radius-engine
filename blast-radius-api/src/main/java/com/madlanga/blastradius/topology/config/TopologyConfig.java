package com.madlanga.blastradius.topology.config;

import com.madlanga.blastradius.topology.repository.FileTopologyRepository;
import com.madlanga.blastradius.telemetry.provider.tempo.TempoTraceAdapter;
import com.madlanga.blastradius.topology.service.TopologyService;
import com.madlanga.blastradius.topology.repository.TopologyRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class TopologyConfig {
    @Bean
    TopologyRepository topologyStore(@Value("${blast-radius.topology.directory:./data/topology}") String directory) {
        return new FileTopologyRepository(Path.of(directory));
    }

    @Bean
    TopologyService retainedTopologyProvider(TopologyRepository store, TempoTraceAdapter adapter,
            @Value("${blast-radius.topology.ttl:7d}") Duration ttl,
            @Value("${blast-radius.topology.discovery-window:5m}") Duration window,
            @Value("${blast-radius.topology.application-id:document-platform}") String applicationId,
            @Value("${blast-radius.topology.environment:local}") String environment) {
        return new TopologyService(store, query -> {
            // This lab has one telemetry backend/application. Never relabel its evidence into another scope.
            if (!applicationId.equals(query.getApplicationId()) || !environment.equals(query.getEnvironment()))
                throw new IllegalArgumentException("Application/environment is not configured for local topology discovery");
            return adapter.fetchSpans(query).getSpans();
        }, Clock.systemUTC(), ttl, window);
    }

    @Bean
    @ConditionalOnProperty(name="blast-radius.topology.refresh-enabled", havingValue="true", matchIfMissing=true)
    TopologyRefresh topologyRefresh(TopologyService provider,
            @Value("${blast-radius.topology.application-id:document-platform}") String applicationId,
            @Value("${blast-radius.topology.environment:local}") String environment) {
        return new TopologyRefresh(provider, applicationId, environment);
    }

    static final class TopologyRefresh {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TopologyRefresh.class);
        private final TopologyService provider;
        private final String applicationId;
        private final String environment;
        TopologyRefresh(TopologyService provider, String applicationId, String environment) {
            this.provider = provider; this.applicationId = applicationId; this.environment = environment;
        }
        @Scheduled(fixedDelayString="${blast-radius.topology.refresh-interval:30s}", initialDelayString="${blast-radius.topology.refresh-interval:30s}")
        public void refresh() {
            try { provider.getTopology(applicationId, environment); }
            catch (RuntimeException e) { log.warn("Topology refresh failed ({}); previous snapshot remains on disk", e.getClass().getSimpleName()); }
        }
    }
}

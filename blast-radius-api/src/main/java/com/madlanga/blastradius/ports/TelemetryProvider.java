package com.madlanga.blastradius.ports;

import com.madlanga.blastradius.domain.evidence.TelemetryBundle;
import com.madlanga.blastradius.domain.evidence.TelemetryQuery;

/**
 * Gets sanitized, normalized telemetry from an external source.
 *
 * <p>The Blast Radius domain consumes this interface exclusively. Implementations
 * live in the {@code adapters} layer and may back onto Loki, Prometheus, Tempo,
 * Spring Actuator, Datadog, or any future provider — without the domain ever
 * depending on provider-specific DTOs, query syntax or wire formats.</p>
 *
 * <p>Contract guarantees:
 * <ul>
 *   <li>This method must never throw for a provider failure; it must return a
 *       {@link TelemetryBundle} with appropriate {@code coverage} and
 *       {@code warnings} instead.</li>
 *   <li>The returned bundle contains only sanitized, normalized evidence.
 *       Raw provider payloads must never reach the caller.</li>
 *   <li>Empty evidence lists do NOT imply healthy components. Coverage status
 *       expresses availability; health state comes from HealthEvidence.</li>
 *   <li>Partial availability (some families unavailable) must not suppress the
 *       families that are available.</li>
 * </ul>
 */
public interface TelemetryProvider {

    /**
     * Retrieve all available normalized telemetry for the given query window.
     *
     * @param query provider-neutral query (application, environment, time window,
     *              optional component/correlation/trace filters)
     * @return a {@link TelemetryBundle} with evidence, coverage metadata, and warnings;
     *         never {@code null}
     */
    TelemetryBundle getTelemetry(TelemetryQuery query);
}

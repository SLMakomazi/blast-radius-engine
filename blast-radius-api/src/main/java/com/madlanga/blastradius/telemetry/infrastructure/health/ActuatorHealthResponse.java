package com.madlanga.blastradius.telemetry.infrastructure.health;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Map;

/**
 * Internal DTO for the Spring Boot Actuator {@code /actuator/health} HTTP response.
 *
 * <p>INTERNAL TO THIS PACKAGE. Never referenced outside the health adapter.
 *
 * <p>Shape:
 * <pre>
 * {
 *   "status": "UP",
 *   "components": {
 *     "db":        {"status": "UP"},
 *     "diskSpace": {"status": "UP", "details": {...}},
 *     "ping":      {"status": "UP"}
 *   }
 * }
 * </pre>
 *
 * <p>The {@code show-details: never} configuration in our services means
 * components may be absent in production-facing responses; the adapter handles
 * both cases.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class ActuatorHealthResponse {

    @JsonProperty("status")
    String status;

    /** Top-level components; may be absent when show-details is "never". */
    @JsonProperty("components")
    Map<String, ComponentHealth> components = Collections.emptyMap();

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ComponentHealth {
        @JsonProperty("status")
        String status;
    }
}

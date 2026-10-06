package com.madlanga.blastradius.telemetry.domain;

/**
 * Provider-neutral health states for normalized health evidence.
 * These map from any health provider (Spring Actuator, Datadog, custom monitors, etc.)
 * without leaking provider-specific status names into the domain.
 */
public enum HealthState {
    UP,
    DOWN,
    DEGRADED,
    UNKNOWN
}

package com.madlanga.blastradius.telemetry.model;

/**
 * The four normalized evidence families that the Blast Radius engine consumes.
 * These are provider-neutral; Loki/Prometheus/Tempo/Actuator are implementation
 * details that live in the adapters layer.
 */
public enum EvidenceFamily {
    LOGS,
    METRICS,
    TRACES,
    HEALTH
}

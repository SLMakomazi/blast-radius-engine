package com.madlanga.blastradius.telemetry.model;

/**
 * Availability classification for a single evidence family within a TelemetryBundle.
 *
 * <p>CRITICAL invariant: a status of UNAVAILABLE or NOT_SUPPORTED does NOT mean
 * the corresponding service is healthy. Telemetry availability and application
 * health are entirely separate concepts (ADR-017).</p>
 *
 * <ul>
 *   <li>AVAILABLE     – evidence was retrieved and covers the requested window.</li>
 *   <li>PARTIAL       – evidence was retrieved but is incomplete (gaps, truncation,
 *                       single-provider subset, etc.).</li>
 *   <li>UNAVAILABLE   – the family was requested but the provider could not supply
 *                       it (timeout, connection failure, empty result, etc.).</li>
 *   <li>NOT_SUPPORTED – the provider does not support this evidence family at all.</li>
 * </ul>
 */
public enum CoverageStatus {
    AVAILABLE,
    PARTIAL,
    UNAVAILABLE,
    NOT_SUPPORTED
}

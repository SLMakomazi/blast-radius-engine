package com.madlanga.blastradius.telemetry.domain;

/**
 * Provider-neutral span/trace status.
 * Normalised from OpenTelemetry STATUS_CODE or equivalent vendor fields.
 */
public enum SpanStatus {
    OK,
    ERROR,
    UNSET
}

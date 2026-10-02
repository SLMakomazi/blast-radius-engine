package com.madlanga.blastradius.domain.evidence;

/**
 * Provider-neutral span/trace status.
 * Normalised from OpenTelemetry STATUS_CODE or equivalent vendor fields.
 */
public enum SpanStatus {
    OK,
    ERROR,
    UNSET
}

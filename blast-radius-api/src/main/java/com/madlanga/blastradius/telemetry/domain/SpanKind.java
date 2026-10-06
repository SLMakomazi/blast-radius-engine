package com.madlanga.blastradius.telemetry.domain;

/** Provider-neutral span role, retained so server errors are not mistaken for dependency calls. */
public enum SpanKind { UNKNOWN, INTERNAL, SERVER, CLIENT, PRODUCER, CONSUMER }

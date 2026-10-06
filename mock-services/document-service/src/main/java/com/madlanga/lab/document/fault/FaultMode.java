package com.madlanga.lab.document.fault;

/**
 * Controlled degradation modes for the synthetic local lab only.
 */
public enum FaultMode {
    NONE,
    ERROR_500,
    INTERMITTENT_500,
    LATENCY,
    DATABASE_FAILURE
}

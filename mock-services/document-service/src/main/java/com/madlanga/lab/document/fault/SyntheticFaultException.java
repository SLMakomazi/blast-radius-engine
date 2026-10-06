package com.madlanga.lab.document.fault;

/**
 * Deliberate application error used only by the local degradation lab.
 */
public final class SyntheticFaultException extends RuntimeException {
    public SyntheticFaultException() {
        super("Synthetic Stage 2 application failure");
    }
}

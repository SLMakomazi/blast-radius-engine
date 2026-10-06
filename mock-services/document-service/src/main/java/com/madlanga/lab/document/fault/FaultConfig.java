package com.madlanga.lab.document.fault;

public record FaultConfig(FaultMode mode, long latencyMs, int everyNthRequest) {
    public FaultConfig {
        mode = mode == null ? FaultMode.NONE : mode;
        if (latencyMs < 0 || latencyMs > 15_000) {
            throw new IllegalArgumentException("latencyMs must be between 0 and 15000");
        }
        if (everyNthRequest < 1 || everyNthRequest > 100) {
            throw new IllegalArgumentException("everyNthRequest must be between 1 and 100");
        }
    }

    public static FaultConfig healthy() {
        return new FaultConfig(FaultMode.NONE, 0, 5);
    }
}

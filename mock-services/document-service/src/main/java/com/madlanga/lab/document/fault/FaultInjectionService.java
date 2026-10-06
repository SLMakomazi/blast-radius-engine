package com.madlanga.lab.document.fault;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.stereotype.Component;

/**
 * Bounded, in-process fault injection for the synthetic document service.
 *
 * <p>This never stops containers or mutates infrastructure. It lets the lab prove
 * that Blast Radius can detect a service that is still UP but degraded.</p>
 */
@Component
public class FaultInjectionService {
    private static final Logger log = LoggerFactory.getLogger(FaultInjectionService.class);

    private final AtomicReference<FaultConfig> config = new AtomicReference<>(FaultConfig.healthy());
    private final AtomicLong requestCounter = new AtomicLong();

    public FaultConfig current() {
        return config.get();
    }

    public FaultConfig configure(FaultConfig requested) {
        FaultConfig next = requested == null ? FaultConfig.healthy() : requested;
        requestCounter.set(0);
        config.set(next);
        log.warn("event=lab_fault_configured mode={} latencyMs={} everyNthRequest={}",
                next.mode(), next.latencyMs(), next.everyNthRequest());
        return next;
    }

    public FaultConfig reset() {
        return configure(FaultConfig.healthy());
    }

    public void beforeDocumentRequest() {
        FaultConfig active = config.get();
        switch (active.mode()) {
            case NONE -> { }
            case ERROR_500 -> failApplication(FaultMode.ERROR_500);
            case INTERMITTENT_500 -> {
                long request = requestCounter.incrementAndGet();
                if (request % active.everyNthRequest() == 0) {
                    failApplication(FaultMode.INTERMITTENT_500);
                }
            }
            case LATENCY -> delay(active.latencyMs());
            case DATABASE_FAILURE -> failDatabase();
        }
    }

    private void failApplication(FaultMode mode) {
        log.error("event=synthetic_application_failure mode={}", mode);
        throw new SyntheticFaultException();
    }

    private void failDatabase() {
        log.error("event=synthetic_dependency_failure dependency=postgres mode=DATABASE_FAILURE");
        throw new CannotGetJdbcConnectionException("Synthetic local database connectivity failure");
    }

    private void delay(long latencyMs) {
        if (latencyMs <= 0) return;
        log.warn("event=synthetic_latency mode=LATENCY delayMs={}", latencyMs);
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SyntheticFaultException();
        }
    }
}

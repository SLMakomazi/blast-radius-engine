package com.madlanga.lab.document.controller;

import com.madlanga.lab.document.fault.FaultConfig;
import com.madlanga.lab.document.fault.FaultInjectionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP fault controls for the local degradation lab scenarios.
 *
 * <p>The compose file only publishes document-service on 127.0.0.1. This endpoint
 * is intentionally part of the synthetic service and must not be copied into a
 * production application.</p>
 */
@RestController
@RequestMapping(path = "/lab/faults", produces = MediaType.APPLICATION_JSON_VALUE)
public class FaultInjectionController {
    private final FaultInjectionService faults;

    public FaultInjectionController(FaultInjectionService faults) {
        this.faults = faults;
    }

    @GetMapping
    public FaultConfig current() {
        return faults.current();
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public FaultConfig configure(@RequestBody FaultConfig config) {
        return faults.configure(config);
    }

    @DeleteMapping
    public FaultConfig reset() {
        return faults.reset();
    }
}

package com.madlanga.blastradius.api;

import com.madlanga.blastradius.domain.incident.IncidentAnalysis;
import com.madlanga.blastradius.service.IncidentAnalysisService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/blast-radius")
public class BlastRadiusController {
    private final IncidentAnalysisService service;
    public BlastRadiusController(IncidentAnalysisService service){this.service=service;}

    @GetMapping("/analyze")
    public IncidentAnalysis analyze(
            @RequestParam String applicationId,
            @RequestParam(defaultValue="local") String environment,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required=false) String originHint) {
        Instant resolvedTo=to==null?Instant.now():to;
        Instant resolvedFrom=from==null?resolvedTo.minus(15, ChronoUnit.MINUTES):from;
        if(!resolvedFrom.isBefore(resolvedTo)) throw new IllegalArgumentException("from must be before to");
        return service.analyze(applicationId,environment,resolvedFrom,resolvedTo,originHint);
    }

    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class})
    ResponseEntity<ErrorResponse> badRequest(RuntimeException e){
        return ResponseEntity.badRequest().body(new ErrorResponse("ANALYSIS_NOT_AVAILABLE",e.getMessage()));
    }
    record ErrorResponse(String code,String message){}
}

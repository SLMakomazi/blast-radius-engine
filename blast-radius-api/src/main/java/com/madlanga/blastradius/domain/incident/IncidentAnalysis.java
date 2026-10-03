package com.madlanga.blastradius.domain.incident;
import com.madlanga.blastradius.domain.evidence.TelemetryCoverage;
import java.time.Instant;
import java.util.List;
public record IncidentAnalysis(String applicationId,String environment,Instant from,Instant to,OriginAssessment origin,TelemetryCoverage coverage,List<ComponentImpact> impacts,List<EvidenceSignal> timeline,IncidentSeverity severity,List<String> warnings) {
 public IncidentAnalysis { impacts=List.copyOf(impacts); timeline=List.copyOf(timeline); warnings=List.copyOf(warnings); }
}

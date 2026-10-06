package com.madlanga.blastradius.incident.domain;
import java.util.List;
public record OriginAssessment(String component, ConfidenceLevel confidence, int evidenceScore, List<EvidenceSignal> evidence) {
 public OriginAssessment { evidence=evidence==null?List.of():List.copyOf(evidence); }
}

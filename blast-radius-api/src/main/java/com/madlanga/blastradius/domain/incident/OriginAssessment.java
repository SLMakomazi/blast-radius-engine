package com.madlanga.blastradius.domain.incident;
import java.util.List;
public record OriginAssessment(String component, ConfidenceLevel confidence, int evidenceScore, List<EvidenceSignal> evidence) {
 public OriginAssessment { evidence=evidence==null?List.of():List.copyOf(evidence); }
}

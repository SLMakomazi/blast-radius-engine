package com.madlanga.blastradius.domain.incident;
import java.util.List;
public record ComponentImpact(String component, ObservedState state, Integer distance, List<String> path, List<EvidenceSignal> evidence) {
 public ComponentImpact { path=path==null?List.of():List.copyOf(path); evidence=evidence==null?List.of():List.copyOf(evidence); }
}

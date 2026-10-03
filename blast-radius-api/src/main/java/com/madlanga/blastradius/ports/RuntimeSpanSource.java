package com.madlanga.blastradius.ports;

import com.madlanga.blastradius.domain.evidence.SpanEvidence;
import com.madlanga.blastradius.domain.evidence.TelemetryQuery;
import java.util.List;

/** Discovery input independent of the incident's four-family telemetry coverage. */
@FunctionalInterface
public interface RuntimeSpanSource {
    List<SpanEvidence> getSpans(TelemetryQuery query);
}

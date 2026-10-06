package com.madlanga.blastradius.topology.application.port;

import com.madlanga.blastradius.telemetry.domain.SpanEvidence;
import com.madlanga.blastradius.telemetry.domain.TelemetryQuery;
import java.util.List;

/** Discovery input independent of the incident's four-family telemetry coverage. */
@FunctionalInterface
public interface RuntimeSpanSource {
    List<SpanEvidence> getSpans(TelemetryQuery query);
}

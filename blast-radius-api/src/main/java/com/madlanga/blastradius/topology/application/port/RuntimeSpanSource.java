package com.madlanga.blastradius.topology.application.port;

import com.madlanga.blastradius.telemetry.model.SpanEvidence;
import com.madlanga.blastradius.telemetry.model.TelemetryQuery;
import java.util.List;

/** Discovery input independent of the incident's four-family telemetry coverage. */
@FunctionalInterface
public interface RuntimeSpanSource {
    List<SpanEvidence> getSpans(TelemetryQuery query);
}

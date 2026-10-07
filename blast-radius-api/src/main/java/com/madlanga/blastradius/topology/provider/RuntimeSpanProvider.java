package com.madlanga.blastradius.topology.provider;

import com.madlanga.blastradius.telemetry.model.SpanEvidence;
import com.madlanga.blastradius.telemetry.model.TelemetryQuery;
import java.util.List;

/** Discovery input independent of the incident's four-family telemetry coverage. */
@FunctionalInterface
public interface RuntimeSpanProvider {
    List<SpanEvidence> getSpans(TelemetryQuery query);
}

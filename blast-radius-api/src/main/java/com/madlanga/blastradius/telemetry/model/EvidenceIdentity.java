package com.madlanga.blastradius.telemetry.model;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Stable, opaque identity for the same provider observation across overlapping fetches. */
public final class EvidenceIdentity {
    private EvidenceIdentity() {}
    public static String of(Object... fields) {
        StringBuilder key = new StringBuilder();
        for (Object field : fields) {
            String value = String.valueOf(field);
            key.append(value.length()).append(':').append(value);
        }
        return "stable-" + UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8));
    }
}

package com.madlanga.blastradius.api.dto;

import java.time.Instant;

/** Optional resolution time supplied by the HTTP caller. */
public record ResolveIncidentRequest(Instant resolvedAt) {}

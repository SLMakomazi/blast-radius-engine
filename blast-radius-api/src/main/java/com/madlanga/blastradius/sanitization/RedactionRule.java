package com.madlanga.blastradius.sanitization;

/**
 * A single pluggable redaction policy.
 *
 * <p>Additional policies (new PII patterns, vendor-specific secrets, etc.) can be
 * added by implementing this interface and registering the implementation — without
 * changing the core {@link TelemetrySanitizer}.</p>
 */
public interface RedactionRule {

    /**
     * Human-readable name used for diagnostics and logging.
     * Must not include secret values.
     */
    String name();

    /**
     * Returns {@code true} when this rule applies to the given attribute key.
     * Case-insensitive matching is recommended so "Authorization" and "authorization"
     * both match.
     *
     * @param key the attribute/header key; never {@code null}
     */
    boolean appliesTo(String key);

    /**
     * Redact the value associated with the matched key.
     *
     * @param key   the attribute/header key; never {@code null}
     * @param value the raw value that must be sanitized; may be {@code null}
     * @return a safe replacement string; never {@code null}
     */
    String redact(String key, String value);
}

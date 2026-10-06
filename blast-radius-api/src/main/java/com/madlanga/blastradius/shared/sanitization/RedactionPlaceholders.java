package com.madlanga.blastradius.shared.sanitization;

/**
 * Canonical placeholder strings used by redaction rules.
 *
 * <p>Using explicit, recognizable placeholders (rather than empty strings or
 * nulls) makes it clear in downstream logs, exports and AI context that a value
 * was present but deliberately removed — not that it was absent. This preserves
 * diagnostic structure while eliminating sensitive content.</p>
 */
public final class RedactionPlaceholders {

    /** General-purpose sensitive value placeholder. */
    public static final String REDACTED = "[REDACTED]";

    /** Authorization header / Bearer token placeholder. */
    public static final String REDACTED_AUTH = "[REDACTED]";

    /** Password field placeholder. */
    public static final String REDACTED_PASSWORD = "[REDACTED]";

    /** API key placeholder. */
    public static final String REDACTED_API_KEY = "[REDACTED]";

    /** Access / session token placeholder. */
    public static final String REDACTED_TOKEN = "[REDACTED]";

    /** Cookie / session value placeholder. */
    public static final String REDACTED_SESSION = "[REDACTED]";

    /**
     * South African ID number placeholder — used only when a value clearly
     * matches the 13-digit SA ID format. Not a general-purpose PII scanner.
     */
    public static final String REDACTED_ID_NUMBER = "[ID_NUMBER]";

    /** Given/display name placeholder — applied only to explicitly named fields. */
    public static final String REDACTED_GIVEN_NAME = "[GIVEN_NAME]";

    private RedactionPlaceholders() {
        // constants only
    }
}

package com.madlanga.blastradius.sanitization;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Factory for all built-in {@link RedactionRule} implementations.
 *
 * <p>Rules are intentionally conservative: they redact clearly sensitive keys and
 * patterns rather than attempting unreliable broad PII scanning. Additional rules
 * can be supplied at construction time without modifying this class.</p>
 *
 * <p>No sensitive literal values appear anywhere in this class or in any test
 * fixtures. Test data uses structural patterns only (e.g. validating that a
 * 13-digit numeric string is replaced, not providing a real ID).</p>
 */
public final class BuiltInRedactionRules {

    private BuiltInRedactionRules() {}

    /** All built-in rules in recommended evaluation order. */
    public static List<RedactionRule> all() {
        return List.of(
                authorizationHeaderRule(),
                passwordRule(),
                apiKeyRule(),
                accessTokenRule(),
                secretRule(),
                cookieSessionRule(),
                saIdNumberRule()
        );
    }

    // -------------------------------------------------------------------------
    // Authorization header
    // -------------------------------------------------------------------------

    /**
     * Redacts the value of any key whose name is "authorization" (case-insensitive),
     * including Bearer tokens.
     */
    static RedactionRule authorizationHeaderRule() {
        return new RedactionRule() {
            @Override
            public String name() { return "authorization-header"; }

            @Override
            public boolean appliesTo(String key) {
                return "authorization".equalsIgnoreCase(key);
            }

            @Override
            public String redact(String key, String value) {
                return RedactionPlaceholders.REDACTED_AUTH;
            }
        };
    }

    // -------------------------------------------------------------------------
    // Password fields
    // -------------------------------------------------------------------------

    private static final Set<String> PASSWORD_KEYS = Set.of(
            "password", "passwd", "pass", "pwd", "userpassword",
            "db.password", "database.password", "jdbc.password"
    );

    static RedactionRule passwordRule() {
        return new RedactionRule() {
            @Override
            public String name() { return "password-field"; }

            @Override
            public boolean appliesTo(String key) {
                return PASSWORD_KEYS.contains(key.toLowerCase());
            }

            @Override
            public String redact(String key, String value) {
                return RedactionPlaceholders.REDACTED_PASSWORD;
            }
        };
    }

    // -------------------------------------------------------------------------
    // API keys
    // -------------------------------------------------------------------------

    private static final Set<String> API_KEY_KEYS = Set.of(
            "api_key", "apikey", "api-key",
            "x-api-key", "x_api_key",
            "dd-api-key", "datadog-api-key",
            "client_secret", "client-secret", "clientsecret",
            "app_secret", "app-secret", "appsecret"
    );

    static RedactionRule apiKeyRule() {
        return new RedactionRule() {
            @Override
            public String name() { return "api-key-field"; }

            @Override
            public boolean appliesTo(String key) {
                return API_KEY_KEYS.contains(key.toLowerCase());
            }

            @Override
            public String redact(String key, String value) {
                return RedactionPlaceholders.REDACTED_API_KEY;
            }
        };
    }

    // -------------------------------------------------------------------------
    // Access / session tokens
    // -------------------------------------------------------------------------

    private static final Set<String> TOKEN_KEYS = Set.of(
            "token", "access_token", "accesstoken", "access-token",
            "refresh_token", "refreshtoken", "id_token", "idtoken",
            "bearer_token", "bearer-token",
            "auth_token", "auth-token", "authtoken"
    );

    static RedactionRule accessTokenRule() {
        return new RedactionRule() {
            @Override
            public String name() { return "access-token-field"; }

            @Override
            public boolean appliesTo(String key) {
                return TOKEN_KEYS.contains(key.toLowerCase());
            }

            @Override
            public String redact(String key, String value) {
                return RedactionPlaceholders.REDACTED_TOKEN;
            }
        };
    }

    // -------------------------------------------------------------------------
    // Generic secret fields
    // -------------------------------------------------------------------------

    private static final Set<String> SECRET_KEYS = Set.of(
            "secret", "app_secret", "app-secret", "private_key", "private-key",
            "signing_key", "signing-key", "encryption_key", "encryption-key",
            "aws_secret_access_key", "aws-secret-access-key"
    );

    static RedactionRule secretRule() {
        return new RedactionRule() {
            @Override
            public String name() { return "secret-field"; }

            @Override
            public boolean appliesTo(String key) {
                return SECRET_KEYS.contains(key.toLowerCase());
            }

            @Override
            public String redact(String key, String value) {
                return RedactionPlaceholders.REDACTED;
            }
        };
    }

    // -------------------------------------------------------------------------
    // Cookie / session identifiers
    // -------------------------------------------------------------------------

    private static final Set<String> COOKIE_KEYS = Set.of(
            "cookie", "set-cookie", "set_cookie",
            "session", "session_id", "sessionid", "session-id",
            "jsessionid", "phpsessid"
    );

    static RedactionRule cookieSessionRule() {
        return new RedactionRule() {
            @Override
            public String name() { return "cookie-session-field"; }

            @Override
            public boolean appliesTo(String key) {
                return COOKIE_KEYS.contains(key.toLowerCase());
            }

            @Override
            public String redact(String key, String value) {
                return RedactionPlaceholders.REDACTED_SESSION;
            }
        };
    }

    // -------------------------------------------------------------------------
    // South African ID number — structural pattern only
    // -------------------------------------------------------------------------

    /**
     * Replaces values that match the 13-digit SA ID number pattern when the key
     * is explicitly named as an ID number field.
     *
     * <p>Scope is intentionally limited to explicitly named fields to avoid
     * false positives. Broad document scanning for numeric patterns is not
     * implemented because it would cause unreliable false positives on metric
     * counters, trace IDs, and other numeric diagnostic data.</p>
     */
    private static final Pattern SA_ID_PATTERN = Pattern.compile("^\\d{13}$");

    private static final Set<String> ID_NUMBER_KEYS = Set.of(
            "id_number", "id-number", "idnumber",
            "sa_id", "sa-id", "said",
            "national_id", "national-id", "nationalid",
            "south_african_id", "south-african-id"
    );

    static RedactionRule saIdNumberRule() {
        return new RedactionRule() {
            @Override
            public String name() { return "sa-id-number-field"; }

            @Override
            public boolean appliesTo(String key) {
                return ID_NUMBER_KEYS.contains(key.toLowerCase());
            }

            @Override
            public String redact(String key, String value) {
                if (value != null && SA_ID_PATTERN.matcher(value).matches()) {
                    return RedactionPlaceholders.REDACTED_ID_NUMBER;
                }
                // Value doesn't match the expected pattern — still redact for safety
                return RedactionPlaceholders.REDACTED;
            }
        };
    }
}

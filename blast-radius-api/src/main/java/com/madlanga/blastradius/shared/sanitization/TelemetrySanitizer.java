package com.madlanga.blastradius.shared.sanitization;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Core sanitization service — applies {@link RedactionRule} policies to raw attribute
 * maps and string values before they enter the normalized telemetry domain.
 *
 * <p>Architectural position:
 * <pre>
 *   RAW PROVIDER DATA
 *        ↓
 *   TelemetrySanitizer  ← this class
 *        ↓
 *   NORMALIZED EVIDENCE (LogEvidence / MetricEvidence / SpanEvidence / HealthEvidence)
 *        ↓
 *   DOMAIN / STORAGE / AI
 * </pre>
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Rules are evaluated in order; first match wins for a given key.</li>
 *   <li>Keys with no matching rule are passed through unchanged, preserving
 *       useful diagnostic structure.</li>
 *   <li>Null attribute values are kept as-is (null is different from an empty string
 *       in diagnostic context).</li>
 *   <li>The sanitizer is stateless except for its immutable rule list; it is safe
 *       to use as a singleton Spring component.</li>
 *   <li>This class does NOT log raw values that may be sensitive.</li>
 * </ul>
 */
@Component
public class TelemetrySanitizer {


    private static final String REDACTED = "[REDACTED]";
    private static final String REDACTED_ID_NUMBER = "[ID_NUMBER]";

    private static final Set<String> PASSWORD_KEYS = Set.of(
            "password", "passwd", "pass", "pwd", "userpassword",
            "db.password", "database.password", "jdbc.password");
    private static final Set<String> API_KEY_KEYS = Set.of(
            "api_key", "apikey", "api-key", "x-api-key", "x_api_key",
            "dd-api-key", "datadog-api-key", "client_secret", "client-secret",
            "clientsecret", "app_secret", "app-secret", "appsecret");
    private static final Set<String> TOKEN_KEYS = Set.of(
            "token", "access_token", "accesstoken", "access-token",
            "refresh_token", "refreshtoken", "id_token", "idtoken",
            "bearer_token", "bearer-token", "auth_token", "auth-token", "authtoken");
    private static final Set<String> SECRET_KEYS = Set.of(
            "secret", "app_secret", "app-secret", "private_key", "private-key",
            "signing_key", "signing-key", "encryption_key", "encryption-key",
            "aws_secret_access_key", "aws-secret-access-key");
    private static final Set<String> SESSION_KEYS = Set.of(
            "cookie", "set-cookie", "set_cookie", "session", "session_id",
            "sessionid", "session-id", "jsessionid", "phpsessid");
    private static final Set<String> ID_NUMBER_KEYS = Set.of(
            "id_number", "id-number", "idnumber", "sa_id", "sa-id", "said",
            "national_id", "national-id", "nationalid",
            "south_african_id", "south-african-id");
    private static final Pattern SA_ID_PATTERN = Pattern.compile("^\\d{13}$");

    private static List<RedactionRule> builtInRules() {
        return List.of(
                rule("authorization", key -> "authorization".equalsIgnoreCase(key), (key, value) -> REDACTED),
                rule("password", key -> PASSWORD_KEYS.contains(key.toLowerCase()), (key, value) -> REDACTED),
                rule("api-key", key -> API_KEY_KEYS.contains(key.toLowerCase()), (key, value) -> REDACTED),
                rule("token", key -> TOKEN_KEYS.contains(key.toLowerCase()), (key, value) -> REDACTED),
                rule("secret", key -> SECRET_KEYS.contains(key.toLowerCase()), (key, value) -> REDACTED),
                rule("session", key -> SESSION_KEYS.contains(key.toLowerCase()), (key, value) -> REDACTED),
                rule("sa-id", key -> ID_NUMBER_KEYS.contains(key.toLowerCase()),
                        (key, value) -> value != null && SA_ID_PATTERN.matcher(value).matches()
                                ? REDACTED_ID_NUMBER : REDACTED));
    }

    private static RedactionRule rule(
            String name,
            java.util.function.Predicate<String> matches,
            java.util.function.BiFunction<String, String, String> redactor) {
        return new RedactionRule() {
            @Override public String name() { return name; }
            @Override public boolean appliesTo(String key) { return matches.test(key); }
            @Override public String redact(String key, String value) { return redactor.apply(key, value); }
        };
    }

    private final List<RedactionRule> rules;

    /**
     * Construct with the full built-in rule set.
     * Additional rules can be injected via the other constructor.
     */
    public TelemetrySanitizer() {
        this.rules = List.copyOf(builtInRules());
    }

    /**
     * Construct with a custom rule list.
     * The built-in rules are NOT automatically prepended; supply all required rules.
     *
     * @param rules ordered list of redaction rules; must not be null or contain nulls
     */
    public TelemetrySanitizer(List<RedactionRule> rules) {
        Objects.requireNonNull(rules, "rules must not be null");
        this.rules = List.copyOf(rules);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Sanitize a map of string key/value attributes, returning a new map where
     * sensitive values have been replaced with explicit placeholders.
     *
     * <p>Non-sensitive keys are preserved verbatim so that useful diagnostic
     * structure (HTTP method, status code, URI, error type, span operation, etc.)
     * remains intact.</p>
     *
     * @param attributes raw attribute map from a provider; may be null or empty
     * @return new map with sensitive values redacted; never null
     */
    public Map<String, String> sanitizeAttributes(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Map<String, String> result = new LinkedHashMap<>(attributes.size());
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            result.put(key, sanitizeValue(key, value));
        }
        return result;
    }

    /**
     * Sanitize a single string value for a known key.
     *
     * <p>Returns the placeholder if any rule matches; otherwise returns the
     * original value unchanged. A null value is returned as-is.</p>
     *
     * @param key   the attribute/header key; must not be null
     * @param value the raw value; may be null
     * @return sanitized value or the original value if no rule matches
     */
    public String sanitizeValue(String key, String value) {
        Objects.requireNonNull(key, "key must not be null");
        if (value == null) {
            return null;
        }
        for (RedactionRule rule : rules) {
            if (rule.appliesTo(key)) {
                return rule.redact(key, value);
            }
        }
        return value;
    }

    /**
     * Sanitize a free-text message by checking whether it contains any obviously
     * sensitive patterns that slipped through structured attribute sanitization.
     *
     * <p>This is a lightweight structural check for common header formats only.
     * It is NOT a general-purpose PII scanner and makes no guarantees about
     * arbitrary text content.</p>
     *
     * @param message the raw log/span message; may be null
     * @return sanitized message, or the original if no patterns were detected; never null
     */
    public String sanitizeMessage(String message) {
        if (message == null) {
            return "";
        }
        // Redact Authorization: Bearer <value> patterns in free-text messages
        return BEARER_PATTERN.matcher(message)
                .replaceAll("Authorization: " + REDACTED);
    }

    /**
     * Sanitize a list of attributes maps in bulk.
     * Convenience helper for adapters that build evidence from multiple records.
     */
    public List<Map<String, String>> sanitizeAttributesList(List<Map<String, String>> list) {
        if (list == null) {
            return new ArrayList<>();
        }
        List<Map<String, String>> result = new ArrayList<>(list.size());
        for (Map<String, String> attrs : list) {
            result.add(sanitizeAttributes(attrs));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Pattern to detect "Authorization: Bearer ..." in free-text messages.
     * Only the Authorization header value is replaced; surrounding text is kept.
     */
    private static final Pattern BEARER_PATTERN =
            Pattern.compile(
                    "(?i)Authorization\\s*:\\s*(?:Bearer\\s+)?[^\\s,;]+",
                    Pattern.CASE_INSENSITIVE);
}

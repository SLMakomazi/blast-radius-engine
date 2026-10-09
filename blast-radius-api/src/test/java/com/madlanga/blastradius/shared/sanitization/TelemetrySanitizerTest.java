package com.madlanga.blastradius.shared.sanitization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TelemetrySanitizer}.
 *
 * <p>SECURITY NOTE: No real credentials, tokens, passwords or PII appear in this
 * file. All test values are clearly synthetic structures used only to verify that
 * the redaction pattern fires correctly. They carry no diagnostic or operational value.</p>
 */
class TelemetrySanitizerTest {

    private TelemetrySanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new TelemetrySanitizer();
    }

    // -------------------------------------------------------------------------
    // Authorization header redaction
    // -------------------------------------------------------------------------

    @Test
    void redactsAuthorizationHeader() {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("Authorization", "Bearer synthetic-test-value-not-a-real-token");
        Map<String, String> result = sanitizer.sanitizeAttributes(attrs);
        assertEquals("[REDACTED]", result.get("Authorization"));
    }

    @Test
    void redactsAuthorizationHeaderCaseInsensitive() {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("authorization", "Basic dXNlcjpwYXNz");
        Map<String, String> result = sanitizer.sanitizeAttributes(attrs);
        assertEquals("[REDACTED]", result.get("authorization"));
    }

    @Test
    void redactsAuthorizationInFreeTextMessage() {
        // The sanitizeMessage path catches Bearer patterns in log messages
        String raw = "Outbound call failed: Authorization: Bearer synthetic-test-value-not-real, retrying";
        String safe = sanitizer.sanitizeMessage(raw);
        assertFalse(safe.contains("Bearer synthetic-test-value-not-real"),
                "Bearer value should have been redacted from message");
        assertTrue(safe.contains("Authorization"), "Key name should be preserved");
        assertTrue(safe.contains("[REDACTED]"));
    }

    // -------------------------------------------------------------------------
    // Password redaction
    // -------------------------------------------------------------------------

    @Test
    void redactsPasswordField() {
        String result = sanitizer.sanitizeValue("password", "synthetic-password-for-testing");
        assertEquals("[REDACTED]", result);
    }

    @Test
    void redactsPasswdField() {
        String result = sanitizer.sanitizeValue("passwd", "synthetic-value");
        assertEquals("[REDACTED]", result);
    }

    @Test
    void redactsDatabasePasswordField() {
        String result = sanitizer.sanitizeValue("db.password", "synthetic-db-pass");
        assertEquals("[REDACTED]", result);
    }

    // -------------------------------------------------------------------------
    // API key redaction
    // -------------------------------------------------------------------------

    @Test
    void redactsApiKeyField() {
        String result = sanitizer.sanitizeValue("api_key", "synthetic-api-key-12345");
        assertEquals("[REDACTED]", result);
    }

    @Test
    void redactsXApiKeyHeader() {
        Map<String, String> attrs = Map.of("x-api-key", "synthetic-key-value");
        Map<String, String> result = sanitizer.sanitizeAttributes(attrs);
        assertEquals("[REDACTED]", result.get("x-api-key"));
    }

    @Test
    void redactsClientSecretField() {
        String result = sanitizer.sanitizeValue("client_secret", "synthetic-client-secret");
        assertEquals("[REDACTED]", result);
    }

    // -------------------------------------------------------------------------
    // Access token / bearer token redaction
    // -------------------------------------------------------------------------

    @Test
    void redactsAccessTokenField() {
        String result = sanitizer.sanitizeValue("access_token", "synthetic-access-token-abc");
        assertEquals("[REDACTED]", result);
    }

    @Test
    void redactsRefreshTokenField() {
        String result = sanitizer.sanitizeValue("refresh_token", "synthetic-refresh-token");
        assertEquals("[REDACTED]", result);
    }

    @Test
    void redactsBearerTokenField() {
        String result = sanitizer.sanitizeValue("bearer_token", "synthetic-bearer");
        assertEquals("[REDACTED]", result);
    }

    // -------------------------------------------------------------------------
    // Cookie / session redaction
    // -------------------------------------------------------------------------

    @Test
    void redactsCookieField() {
        String result = sanitizer.sanitizeValue("cookie", "JSESSIONID=synthetic-session-value");
        assertEquals("[REDACTED]", result);
    }

    @Test
    void redactsSessionIdField() {
        String result = sanitizer.sanitizeValue("session_id", "synthetic-session-id");
        assertEquals("[REDACTED]", result);
    }

    // -------------------------------------------------------------------------
    // Safe diagnostic fields are preserved
    // -------------------------------------------------------------------------

    @Test
    void preservesHttpMethod() {
        String result = sanitizer.sanitizeValue("http.request.method", "POST");
        assertEquals("POST", result);
    }

    @Test
    void preservesHttpStatusCode() {
        String result = sanitizer.sanitizeValue("http.response.status_code", "503");
        assertEquals("503", result);
    }

    @Test
    void preservesHttpRoute() {
        String result = sanitizer.sanitizeValue("http.route", "/api/documents");
        assertEquals("/api/documents", result);
    }

    @Test
    void preservesErrorType() {
        String result = sanitizer.sanitizeValue("error.type", "ConnectionException");
        assertEquals("ConnectionException", result);
    }

    @Test
    void preservesDbSystem() {
        String result = sanitizer.sanitizeValue("db.system", "postgresql");
        assertEquals("postgresql", result);
    }

    @Test
    void preservesServiceName() {
        String result = sanitizer.sanitizeValue("service.name", "storage-api");
        assertEquals("storage-api", result);
    }

    // -------------------------------------------------------------------------
    // Structured attribute map sanitization
    // -------------------------------------------------------------------------

    @Test
    void sanitizesMapPreservingSafeKeysRedactingSensitiveKeys() {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("http.request.method", "POST");
        attrs.put("http.route", "/api/documents");
        attrs.put("http.response.status_code", "201");
        attrs.put("authorization", "Bearer synthetic-not-real");
        attrs.put("error.type", "ConnectionException");
        attrs.put("password", "synthetic-password");

        Map<String, String> result = sanitizer.sanitizeAttributes(attrs);

        assertEquals("POST", result.get("http.request.method"));
        assertEquals("/api/documents", result.get("http.route"));
        assertEquals("201", result.get("http.response.status_code"));
        assertEquals("[REDACTED]", result.get("authorization"));
        assertEquals("ConnectionException", result.get("error.type"));
        assertEquals("[REDACTED]", result.get("password"));
    }

    @Test
    void nullAttributeMapReturnsEmptyMap() {
        Map<String, String> result = sanitizer.sanitizeAttributes(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void nullValueForKeyIsPreservedAsNull() {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("safe.key", null);
        Map<String, String> result = sanitizer.sanitizeAttributes(attrs);
        assertNull(result.get("safe.key"));
    }

    // -------------------------------------------------------------------------
    // SA ID number (structural pattern only)
    // -------------------------------------------------------------------------

    @Test
    void redactsSaIdNumberFieldWith13DigitValue() {
        // Synthetic 13-digit number — not a real ID, just validates the structural pattern
        String result = sanitizer.sanitizeValue("id_number", "0000000000000");
        assertEquals("[ID_NUMBER]", result);
    }

    @Test
    void redactsSaIdFieldWithNonMatchingValueStillRedacts() {
        // Even if the value doesn't match the 13-digit pattern, named ID fields are still redacted
        String result = sanitizer.sanitizeValue("national_id", "short");
        assertEquals("[REDACTED]", result);
    }

    @Test
    void doesNotRedactNonIdNumericFields() {
        // A metric counter value must NOT be misidentified as an ID number
        String result = sanitizer.sanitizeValue("http.response.status_code", "0000000000000");
        assertEquals("0000000000000", result); // Not an ID field name, so passes through
    }

    // -------------------------------------------------------------------------
    // Null / blank message handling
    // -------------------------------------------------------------------------

    @Test
    void nullMessageReturnsEmptyString() {
        String result = sanitizer.sanitizeMessage(null);
        assertEquals("", result);
    }

    @Test
    void safeMessagePassesThroughUnchanged() {
        String safe = "event=document_inserted dependency=postgres documentId=fa9f0655";
        String result = sanitizer.sanitizeMessage(safe);
        assertEquals(safe, result);
    }

    // -------------------------------------------------------------------------
    // Custom rule injection
    // -------------------------------------------------------------------------

    @Test
    void customRuleCanBeInjectedAtConstruction() {
        RedactionRule customRule = new RedactionRule() {
            @Override public String name() { return "custom-internal-key"; }
            @Override public boolean appliesTo(String key) {
                return "internal.secret.header".equalsIgnoreCase(key);
            }
            @Override public String redact(String key, String value) {
                return "[CUSTOM-REDACTED]";
            }
        };

        TelemetrySanitizer custom = new TelemetrySanitizer(
                java.util.List.of(customRule));

        assertEquals("[CUSTOM-REDACTED]",
                custom.sanitizeValue("internal.secret.header", "some-value"));
        // Without the built-in rules, password passes through
        assertEquals("some-password",
                custom.sanitizeValue("password", "some-password"));
    }
}

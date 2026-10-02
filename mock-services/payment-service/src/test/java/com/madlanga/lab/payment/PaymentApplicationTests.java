package com.madlanga.lab.payment;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PaymentApplicationTests.ClientTestConfiguration.class)
class PaymentApplicationTests {
    private static final String PAYLOAD = """
            {"customerId":"SYNTH-CUST-001","documentReference":"SYNTH-DOC-001","amount":125.50,"currency":"ZAR"}
            """;
    @Autowired MockMvc mvc;
    @Autowired MockRestServiceServer server;

    @BeforeEach void reset() { server.reset(); }
    @AfterEach void verify() { server.verify(); }

    @Test void prometheusIsExposedButSensitiveEndpointsAreNot() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_memory_used_bytes")));
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/configprops")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/heapdump")).andExpect(status().isNotFound());
    }

    @Test void contextLoadsAndHealthIsExposed() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }

    @Test void invokesDownstreamAndPreservesCorrelationId() throws Exception {
        expectSuccess("phase2-test-001");
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "phase2-test-001").content(PAYLOAD))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-ID", "phase2-test-001"))
                .andExpect(jsonPath("$.correlationId").value("phase2-test-001"))
                .andExpect(jsonPath("$.document.documentId").value("00000000-0000-0000-0000-000000000001"));
        assertThat(org.slf4j.MDC.get("correlationId")).isNull();
    }

    @Test void generatesCorrelationIdAndPropagatesIt() throws Exception {
        expectSuccess(null);
        var result = mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isCreated()).andReturn();
        String id = result.getResponse().getHeader("X-Correlation-ID");
        assertThat(id).matches("[a-f0-9-]{36}");
        assertThat(result.getResponse().getContentAsString()).contains(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "null", "{\"customerId\":\"NOT-SYNTHETIC\",\"documentReference\":\"SYNTH-DOC-001\"}"})
    void rejectsInvalidInputWithoutCallingDownstream(String input) throws Exception {
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("invalidPayments")
    void rejectsInvalidAmountOrCurrency(String input) throws Exception {
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isBadRequest());
    }

    static java.util.stream.Stream<String> invalidPayments() {
        return java.util.stream.Stream.of(
                PAYLOAD.replace("125.50", "0"),
                PAYLOAD.replace("125.50", "-1"),
                PAYLOAD.replace("125.50", "1.234"),
                PAYLOAD.replace("125.50", "null"),
                PAYLOAD.replace("ZAR", "invalid"));
    }

    @Test void rejectsUnsafeCorrelationId() throws Exception {
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "unsafe id").content(PAYLOAD))
                .andExpect(status().isBadRequest());
    }

    @Test void downstreamFailureIsSanitizedAndNotSuccessful() throws Exception {
        server.expect(requestTo("http://downstream.test/api/customers/validate"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON).body("untrusted downstream details"));
        var result = mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "failure-001").content(PAYLOAD))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.dependency").value("customer-service"))
                .andExpect(jsonPath("$.downstreamStatus").value(503))
                .andExpect(jsonPath("$.correlationId").value("failure-001")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("untrusted", "Exception");
    }

    @Test void connectionFailureIsNotSuccessful() throws Exception {
        server.expect(requestTo("http://downstream.test/api/customers/validate"))
                .andRespond(withException(new java.io.IOException("private connection details")));
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("DOWNSTREAM_FAILURE"));
    }

    @Test void emptySuccessBodyIsNotAccepted() throws Exception {
        server.expect(requestTo("http://downstream.test/api/customers/validate")).andRespond(withSuccess());
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isBadGateway());
    }

    private void expectSuccess(String expectedId) {
        server.expect(requestTo("http://downstream.test/api/customers/validate"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().json("""
                        {"customerId":"SYNTH-CUST-001","documentReference":"SYNTH-DOC-001"}
                        """))
                .andExpect(request -> {
                    String id = request.getHeaders().getFirst("X-Correlation-ID");
                    assertThat(id).isNotBlank();
                    if (expectedId != null) assertThat(id).isEqualTo(expectedId);
                })
                .andRespond(request -> {
                    String id = request.getHeaders().getFirst("X-Correlation-ID");
                    String document = """
                            {"documentId":"00000000-0000-0000-0000-000000000001",
                             "documentReference":"SYNTH-DOC-001","customerId":"SYNTH-CUST-001",
                             "createdAt":"2026-10-01T10:00:00Z","correlationId":"%s"}
                            """.formatted(id);
                    String body = "{\"status\":\"VALIDATED\",\"document\":" + document + ",\"correlationId\":\"" + id + "\"}";
                    var response = new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
                    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    return response;
                });
    }

    @TestConfiguration
    static class ClientTestConfiguration {
        private final RestClient.Builder builder = RestClient.builder().baseUrl("http://downstream.test");
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        @Bean MockRestServiceServer mockServer() { return server; }
        @Bean @Primary RestClient testClient() { return builder.build(); }
    }
}

package com.madlanga.lab.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentApplicationTests {
    static final String PAYLOAD = """
            {"customerId":"SYNTH-CUST-001","documentReference":"SYNTH-DOC-001"}
            """;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test void prometheusIsExposedButSensitiveEndpointsAreNot() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_memory_used_bytes")));
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/configprops")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/heapdump")).andExpect(status().isNotFound());
    }

    @Test void contextLoadsWithMigratedDatabaseAndHealth() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }

    @Test void persistsAndReturnsRealDocumentWithCorrelationId() throws Exception {
        String id = "document-test-" + java.util.UUID.randomUUID();
        mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", id).content(PAYLOAD))
                .andExpect(status().isCreated()).andExpect(header().string("X-Correlation-ID", id))
                .andExpect(jsonPath("$.documentId").isNotEmpty())
                .andExpect(jsonPath("$.correlationId").value(id));
        var row = jdbc.queryForMap("SELECT * FROM synthetic_documents WHERE correlation_id = ?", id);
        assertThat(row.get("CUSTOMER_REFERENCE")).isEqualTo("SYNTH-CUST-001");
        assertThat(row.get("DOCUMENT_REFERENCE")).isEqualTo("SYNTH-DOC-001");
        assertThat(row.get("CREATED_AT")).isNotNull();
        assertThat(org.slf4j.MDC.get("correlationId")).isNull();
    }

    @Test void generatesMissingCorrelationId() throws Exception {
        var response = mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isCreated()).andReturn().getResponse();
        String id = response.getHeader("X-Correlation-ID");
        assertThat(id).matches("[a-f0-9-]{36}");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM synthetic_documents WHERE correlation_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test void invalidInputNeverPersists() throws Exception {
        int before = jdbc.queryForObject("SELECT count(*) FROM synthetic_documents", Integer.class);
        mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "unsafe id").content(PAYLOAD))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM synthetic_documents", Integer.class)).isEqualTo(before);
    }
}

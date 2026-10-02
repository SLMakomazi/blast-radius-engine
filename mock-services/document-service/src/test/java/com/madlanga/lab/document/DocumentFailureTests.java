package com.madlanga.lab.document;

import com.madlanga.lab.document.persistence.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentFailureTests {
    @Autowired MockMvc mvc;
    @MockitoBean DocumentRepository repository;

    @Test void databaseFailureReturnsSanitized503() throws Exception {
        when(repository.insert(anyString(), anyString(), anyString()))
                .thenThrow(new CannotGetJdbcConnectionException("private JDBC connection details"));
        var response = mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "database-failure-001").content(DocumentApplicationTests.PAYLOAD))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"))
                .andExpect(jsonPath("$.dependency").value("postgres"))
                .andExpect(jsonPath("$.correlationId").value("database-failure-001"))
                .andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("private", "JDBC", "Exception");
    }
}

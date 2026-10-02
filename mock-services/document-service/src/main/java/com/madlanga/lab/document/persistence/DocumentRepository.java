package com.madlanga.lab.document.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import com.madlanga.lab.document.dto.DocumentReceipt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {
    private final JdbcTemplate jdbc;

    public DocumentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public DocumentReceipt insert(String customerId, String documentReference, String correlationId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO synthetic_documents
                    (id, document_reference, customer_reference, created_at, correlation_id)
                VALUES (?, ?, ?, ?, ?)
                """, id, documentReference, customerId, Timestamp.from(Instant.now()), correlationId);
        return jdbc.queryForObject("""
                SELECT id, document_reference, customer_reference, created_at, correlation_id
                FROM synthetic_documents WHERE id = ?
                """, (rs, row) -> new DocumentReceipt(rs.getObject("id", UUID.class),
                rs.getString("document_reference"), rs.getString("customer_reference"),
                rs.getTimestamp("created_at").toInstant(), rs.getString("correlation_id")), id);
    }
}

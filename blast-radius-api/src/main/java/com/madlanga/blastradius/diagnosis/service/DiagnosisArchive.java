package com.madlanga.blastradius.diagnosis.service;

import com.madlanga.blastradius.incident.model.PersistedIncident;
import com.madlanga.blastradius.diagnosis.dto.DiagnosisResponse;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class DiagnosisArchive {
    private final JdbcTemplate jdbc;
    private final JsonMapper mapper;
    public DiagnosisArchive(JdbcTemplate jdbc, JsonMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }
    public JsonNode save(PersistedIncident incident, DiagnosisResponse response) {
        UUID id = UUID.randomUUID();
        long version = mapper.readTree(incident.analysisSnapshot()).path("evidenceVersion").asLong(0);
        ObjectNode result = (ObjectNode) mapper.valueToTree(response);
        result.put("diagnosisId", id.toString());
        result.put("evidenceVersion", version);
        result.put("evidenceCapturedAt", incident.updatedAt().toString());
        jdbc.update("INSERT INTO incident_diagnoses(id,incident_id,evidence_version,evidence_snapshot,diagnosis) VALUES (?,?,?,?::jsonb,?::jsonb)",
                id, incident.id(), version, incident.analysisSnapshot(), mapper.writeValueAsString(result));
        return result;
    }
    public java.util.List<JsonNode> list(UUID incidentId) {
        return jdbc.query("SELECT diagnosis FROM incident_diagnoses WHERE incident_id=? ORDER BY created_at DESC",
                (rs, row) -> mapper.readTree(rs.getString(1)), incidentId);
    }
}

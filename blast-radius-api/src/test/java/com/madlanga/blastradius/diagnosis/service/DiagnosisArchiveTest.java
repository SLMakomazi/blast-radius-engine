package com.madlanga.blastradius.diagnosis.service;

import com.madlanga.blastradius.diagnosis.dto.DiagnosisResponse;
import com.madlanga.blastradius.incident.model.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DiagnosisArchiveTest {
    @Test void storesExactEvidenceRevisionWithoutUpdatingIncidentOrPriorDiagnosis() {
        var jdbc = mock(JdbcTemplate.class);
        var archive = new DiagnosisArchive(jdbc,JsonMapper.builder().build());
        Instant now = Instant.now();
        var incident = new PersistedIncident(UUID.randomUUID(),"test","local",PersistedIncident.Status.ACTIVE,
                now,null,"db",OriginAssessment.Confidence.HIGH,IncidentSeverity.Level.HIGH,50,now,now,
                "{\"evidenceVersion\":7,\"timeline\":[{\"signal\":\"retained observation\"}]}",now,now);
        var diagnosis = new DiagnosisResponse("test","test","facts","undetermined",List.of(),List.of(),List.of(),List.of());
        var result = archive.save(incident,diagnosis);
        assertThat(result.path("evidenceVersion").asLong()).isEqualTo(7);
        assertThat(result.path("evidenceCapturedAt").asText()).isEqualTo(now.toString());
        verify(jdbc).update(startsWith("INSERT INTO incident_diagnoses"), any(UUID.class),eq(incident.id()),eq(7L),
                eq(incident.analysisSnapshot()),contains("diagnosisId"));
        verifyNoMoreInteractions(jdbc);
    }
}

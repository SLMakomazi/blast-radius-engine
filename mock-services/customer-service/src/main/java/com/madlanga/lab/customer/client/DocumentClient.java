package com.madlanga.lab.customer.client;

import com.madlanga.lab.customer.dto.*;
import com.madlanga.lab.customer.exception.DownstreamException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class DocumentClient {
    private final RestClient client;

    public DocumentClient(RestClient client) { this.client = client; }

    public DocumentReceipt invoke(DownstreamRequest request, String correlationId) {
        try {
            var result = client.post().uri("/api/documents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Correlation-ID", correlationId).body(request)
                    .retrieve().body(DocumentReceipt.class);
            if (result == null ||
                    result.documentId() == null ||
                    !correlationId.equals(result.correlationId())) {
                throw new DownstreamException(null);
            }
            return result;
        } catch (RestClientResponseException ex) {
            // Never forward untrusted downstream response bodies or exception messages.
            throw new DownstreamException(ex.getStatusCode().value());
        } catch (RestClientException ex) {
            throw new DownstreamException(null);
        }
    }
}

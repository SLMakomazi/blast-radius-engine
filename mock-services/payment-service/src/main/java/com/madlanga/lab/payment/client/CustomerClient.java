package com.madlanga.lab.payment.client;

import com.madlanga.lab.payment.dto.*;
import com.madlanga.lab.payment.exception.DownstreamException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class CustomerClient {
    private final RestClient client;

    public CustomerClient(RestClient client) { this.client = client; }

    public CustomerValidation invoke(DownstreamRequest request, String correlationId) {
        try {
            var result = client.post().uri("/api/customers/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Correlation-ID", correlationId).body(request)
                    .retrieve().body(CustomerValidation.class);
            if (result == null || result.document() == null || !"VALIDATED".equals(result.status()) ||
                    result.document().documentId() == null ||
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

package com.madlanga.lab.payment.service;

import com.madlanga.lab.payment.client.CustomerClient;
import com.madlanga.lab.payment.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final CustomerClient client;

    public PaymentService(CustomerClient client) { this.client = client; }

    public PaymentResult process(PaymentRequest request, String correlationId) {
        log.info("event=downstream_request dependency=customer-service");
        var downstream = client.invoke(new DownstreamRequest(request.customerId(), request.documentReference()), correlationId);
        log.info("event=request_succeeded dependency=customer-service");
        return new PaymentResult(java.util.UUID.randomUUID(), "PROCESSED", downstream.document(), correlationId);
    }
}

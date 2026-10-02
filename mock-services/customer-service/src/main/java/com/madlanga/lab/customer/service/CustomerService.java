package com.madlanga.lab.customer.service;

import com.madlanga.lab.customer.client.DocumentClient;
import com.madlanga.lab.customer.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {
    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);
    private final DocumentClient client;

    public CustomerService(DocumentClient client) { this.client = client; }

    public CustomerValidation process(CustomerRequest request, String correlationId) {
        log.info("event=downstream_request dependency=document-service");
        var downstream = client.invoke(new DownstreamRequest(request.customerId(), request.documentReference()), correlationId);
        log.info("event=request_succeeded dependency=document-service");
        return new CustomerValidation("VALIDATED", downstream, correlationId);
    }
}

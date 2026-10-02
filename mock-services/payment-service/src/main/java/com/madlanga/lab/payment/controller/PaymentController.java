package com.madlanga.lab.payment.controller;

import com.madlanga.lab.payment.dto.*;
import com.madlanga.lab.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class PaymentController {
    private final PaymentService service;

    public PaymentController(PaymentService service) { this.service = service; }

    @PostMapping("/api/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResult process(@Valid @RequestBody PaymentRequest request,
                          @RequestAttribute("X-Correlation-ID") String correlationId) {
        return service.process(request, correlationId);
    }
}

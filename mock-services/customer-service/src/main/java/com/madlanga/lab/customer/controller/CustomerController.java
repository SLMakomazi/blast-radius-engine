package com.madlanga.lab.customer.controller;

import com.madlanga.lab.customer.dto.*;
import com.madlanga.lab.customer.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class CustomerController {
    private final CustomerService service;

    public CustomerController(CustomerService service) { this.service = service; }

    @PostMapping("/api/customers/validate")
    @ResponseStatus(HttpStatus.OK)
    public CustomerValidation process(@Valid @RequestBody CustomerRequest request,
                          @RequestAttribute("X-Correlation-ID") String correlationId) {
        return service.process(request, correlationId);
    }
}

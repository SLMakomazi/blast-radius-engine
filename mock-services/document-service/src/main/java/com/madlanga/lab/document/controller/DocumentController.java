package com.madlanga.lab.document.controller;

import com.madlanga.lab.document.dto.*;
import com.madlanga.lab.document.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class DocumentController {
    private final DocumentService service;

    public DocumentController(DocumentService service) { this.service = service; }

    @PostMapping("/api/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentReceipt process(@Valid @RequestBody DocumentRequest request,
                          @RequestAttribute("X-Correlation-ID") String correlationId) {
        return service.process(request, correlationId);
    }
}

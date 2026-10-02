package com.madlanga.lab.document.service;

import com.madlanga.lab.document.dto.*;
import com.madlanga.lab.document.persistence.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private final DocumentRepository repository;

    public DocumentService(DocumentRepository repository) { this.repository = repository; }

    @Transactional
    public DocumentReceipt process(DocumentRequest request, String correlationId) {
        var receipt = repository.insert(request.customerId(), request.documentReference(), correlationId);
        log.info("event=document_inserted dependency=postgres documentId={}", receipt.documentId());
        return receipt;
    }
}

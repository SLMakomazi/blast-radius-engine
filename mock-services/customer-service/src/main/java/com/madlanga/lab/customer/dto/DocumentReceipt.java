package com.madlanga.lab.customer.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentReceipt(UUID documentId, String documentReference, String customerId,
                              Instant createdAt, String correlationId) {}

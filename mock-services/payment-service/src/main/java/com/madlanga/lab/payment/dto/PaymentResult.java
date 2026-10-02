package com.madlanga.lab.payment.dto;

import java.util.UUID;

public record PaymentResult(UUID paymentId, String status, DocumentReceipt document, String correlationId) {}

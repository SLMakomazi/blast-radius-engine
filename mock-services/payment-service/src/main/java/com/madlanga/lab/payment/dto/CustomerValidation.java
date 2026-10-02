package com.madlanga.lab.payment.dto;

public record CustomerValidation(String status, DocumentReceipt document, String correlationId) {}

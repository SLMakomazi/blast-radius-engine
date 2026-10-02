package com.madlanga.lab.payment.dto;

public record ApiError(String code, String message, String service, String dependency,
                       Integer downstreamStatus, String correlationId) {}

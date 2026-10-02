package com.madlanga.lab.customer.dto;

public record ApiError(String code, String message, String service, String dependency,
                       Integer downstreamStatus, String correlationId) {}

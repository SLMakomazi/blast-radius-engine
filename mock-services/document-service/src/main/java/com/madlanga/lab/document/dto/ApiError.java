package com.madlanga.lab.document.dto;

public record ApiError(String code, String message, String service, String dependency,
                       Integer downstreamStatus, String correlationId) {}

package com.madlanga.lab.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DocumentRequest(
        @NotBlank @Pattern(regexp = "SYNTH-CUST-[A-Z0-9-]{1,48}") String customerId,
        @NotBlank @Pattern(regexp = "SYNTH-DOC-[A-Z0-9-]{1,48}") String documentReference) {}

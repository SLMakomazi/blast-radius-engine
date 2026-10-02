package com.madlanga.lab.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CustomerRequest(
        @NotBlank @Pattern(regexp = "SYNTH-CUST-[A-Z0-9-]{1,48}") String customerId,
        @NotBlank @Pattern(regexp = "SYNTH-DOC-[A-Z0-9-]{1,48}") String documentReference) {}

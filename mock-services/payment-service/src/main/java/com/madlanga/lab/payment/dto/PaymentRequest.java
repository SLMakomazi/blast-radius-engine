package com.madlanga.lab.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record PaymentRequest(
        @NotBlank @Pattern(regexp = "SYNTH-CUST-[A-Z0-9-]{1,48}") String customerId,
        @NotBlank @Pattern(regexp = "SYNTH-DOC-[A-Z0-9-]{1,48}") String documentReference,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {}

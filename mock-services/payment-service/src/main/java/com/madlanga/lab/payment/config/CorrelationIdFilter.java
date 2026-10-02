package com.madlanga.lab.payment.config;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        boolean invalid = supplied != null && !supplied.matches("[A-Za-z0-9._-]{1,128}");
        String id = supplied == null || invalid ? UUID.randomUUID().toString() : supplied;
        response.setHeader(HEADER, id);
        request.setAttribute(HEADER, id);
        try (var ignored = MDC.putCloseable("correlationId", id)) {
            if (invalid) {
                response.setStatus(400);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"INVALID_CORRELATION_ID\",\"message\":\"Use 1-128 letters, digits, dots, underscores or hyphens\"}");
                return;
            }
            chain.doFilter(request, response);
        }
    }
}

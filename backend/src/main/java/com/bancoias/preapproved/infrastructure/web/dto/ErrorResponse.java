package com.bancoias.preapproved.infrastructure.web.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        List<String> details,
        UsageRequestResponse originalRequest,
        Instant timestamp
) {
    public static ErrorResponse of(String code, String message, List<String> details) {
        return new ErrorResponse(code, message, details, null, Instant.now());
    }

    public static ErrorResponse conflict(String message, UsageRequestResponse original) {
        return new ErrorResponse("REQUEST_REFERENCE_CONFLICT", message, List.of(), original, Instant.now());
    }
}
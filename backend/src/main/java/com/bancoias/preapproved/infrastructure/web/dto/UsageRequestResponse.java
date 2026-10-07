package com.bancoias.preapproved.infrastructure.web.dto;

import com.bancoias.preapproved.domain.model.UsageRequest;

import java.math.BigDecimal;
import java.time.Instant;

public record UsageRequestResponse(
        String requestReference,
        String preApprovedId,
        String customerId,
        BigDecimal amount,
        String status,
        String rejectionReason,
        String rejectionMessage,
        Instant processedAt,
        boolean replayed
) {
    public static UsageRequestResponse from(UsageRequest r, boolean replayed) {
        return new UsageRequestResponse(
                r.requestReference(),
                r.preApprovedId(),
                r.customerId(),
                r.amount(),
                r.status().name(),
                r.rejectionReason() == null ? null : r.rejectionReason().name(),
                r.rejectionReason() == null ? null : r.rejectionReason().getDescription(),
                r.processedAt(),
                replayed);
    }

    public static UsageRequestResponse from(UsageRequest r) {
        return from(r, false);
    }
}
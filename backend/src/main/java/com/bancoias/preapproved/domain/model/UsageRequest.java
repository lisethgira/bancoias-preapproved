package com.bancoias.preapproved.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record UsageRequest(
        Long id,
        String requestReference,
        String preApprovedId,
        String customerId,
        BigDecimal amount,
        String payloadHash,
        UsageRequestStatus status,
        RejectionReason rejectionReason,
        Instant processedAt
) {
    public static UsageRequest authorized(UsageRequestCommand command, Instant processedAt) {
        return new UsageRequest(null, command.requestReference(), command.preApprovedId(),
                command.customerId(), command.amount(), command.payloadHash(),
                UsageRequestStatus.AUTHORIZED, null, processedAt);
    }

    public static UsageRequest rejected(UsageRequestCommand command, RejectionReason reason, Instant processedAt) {
        return new UsageRequest(null, command.requestReference(), command.preApprovedId(),
                command.customerId(), command.amount(), command.payloadHash(),
                UsageRequestStatus.REJECTED, reason, processedAt);
    }

    public boolean isAuthorized() {
        return status == UsageRequestStatus.AUTHORIZED;
    }
}
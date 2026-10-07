package com.bancoias.preapproved.domain.model;

import java.math.BigDecimal;

public record PreApproved(
        String id,
        String customerId,
        PreApprovedStatus status,
        BigDecimal availableAmount
) {
    public boolean belongsTo(String customerId) {
        return this.customerId.equals(customerId);
    }

    public boolean isActive() {
        return status == PreApprovedStatus.ACTIVE;
    }
}
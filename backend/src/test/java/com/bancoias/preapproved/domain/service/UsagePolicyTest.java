package com.bancoias.preapproved.domain.service;

import com.bancoias.preapproved.domain.model.PreApproved;
import com.bancoias.preapproved.domain.model.PreApprovedStatus;
import com.bancoias.preapproved.domain.model.RejectionReason;
import com.bancoias.preapproved.domain.model.UsageRequestCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UsagePolicyTest {

    private final UsagePolicy policy = new UsagePolicy();

    private final PreApproved activePra = new PreApproved(
            "PRA-1001", "USR-10", PreApprovedStatus.ACTIVE, new BigDecimal("1000000"));

    private final PreApproved blockedPra = new PreApproved(
            "PRA-1002", "USR-10", PreApprovedStatus.BLOCKED, new BigDecimal("800000"));

    private UsageRequestCommand command(String praId, String customerId, String amount) {
        return new UsageRequestCommand("REF-001", praId, customerId,
                amount == null ? null : new BigDecimal(amount));
    }

    @Test
    @DisplayName("Permite una solicitud válida dentro del cupo")
    void allowsValidRequest() {
        assertThat(policy.evaluate(command("PRA-1001", "USR-10", "600000"), Optional.of(activePra)))
                .isEmpty();
    }

    @Test
    @DisplayName("Permite usar exactamente el cupo disponible")
    void allowsExactAvailableAmount() {
        assertThat(policy.evaluate(command("PRA-1001", "USR-10", "1000000"), Optional.of(activePra)))
                .isEmpty();
    }

    @Test
    @DisplayName("Rechaza monto cero")
    void rejectsZeroAmount() {
        assertThat(policy.evaluate(command("PRA-1001", "USR-10", "0"), Optional.of(activePra)))
                .contains(RejectionReason.INVALID_AMOUNT);
    }

    @Test
    @DisplayName("Rechaza monto negativo")
    void rejectsNegativeAmount() {
        assertThat(policy.evaluate(command("PRA-1001", "USR-10", "-100"), Optional.of(activePra)))
                .contains(RejectionReason.INVALID_AMOUNT);
    }

    @Test
    @DisplayName("Rechaza monto nulo")
    void rejectsNullAmount() {
        assertThat(policy.evaluate(command("PRA-1001", "USR-10", null), Optional.of(activePra)))
                .contains(RejectionReason.INVALID_AMOUNT);
    }

    @Test
    @DisplayName("Rechaza preaprobado inexistente")
    void rejectsUnknownPreApproved() {
        assertThat(policy.evaluate(command("PRA-9999", "USR-10", "100"), Optional.empty()))
                .contains(RejectionReason.PRE_APPROVED_NOT_FOUND);
    }

    @Test
    @DisplayName("Rechaza cuando el preaprobado pertenece a otro cliente")
    void rejectsCustomerMismatch() {
        assertThat(policy.evaluate(command("PRA-1001", "USR-20", "100"), Optional.of(activePra)))
                .contains(RejectionReason.CUSTOMER_MISMATCH);
    }

    @Test
    @DisplayName("Rechaza preaprobado bloqueado")
    void rejectsBlockedPreApproved() {
        assertThat(policy.evaluate(command("PRA-1002", "USR-10", "100"), Optional.of(blockedPra)))
                .contains(RejectionReason.PRE_APPROVED_NOT_ACTIVE);
    }

    @Test
    @DisplayName("Rechaza cuando el monto supera el cupo disponible")
    void rejectsAmountAboveAvailable() {
        assertThat(policy.evaluate(command("PRA-1001", "USR-10", "1000001"), Optional.of(activePra)))
                .contains(RejectionReason.INSUFFICIENT_AVAILABLE_AMOUNT);
    }
}
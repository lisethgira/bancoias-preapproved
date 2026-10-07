package com.bancoias.preapproved.domain.service;

import com.bancoias.preapproved.domain.model.PreApproved;
import com.bancoias.preapproved.domain.model.RejectionReason;
import com.bancoias.preapproved.domain.model.UsageRequestCommand;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Reglas de negocio de RF02. Devuelve la razón de rechazo o vacío si la solicitud
 * puede intentar autorizarse.
 *
 * La validación del cupo aquí es una verificación previa sobre una lectura.
 * La garantía real frente a solicitudes simultáneas (RF04) la da el UPDATE
 * condicional atómico en la base de datos.
 */
public class UsagePolicy {

    public Optional<RejectionReason> evaluate(UsageRequestCommand command, Optional<PreApproved> preApproved) {
        if (command.amount() == null || command.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.of(RejectionReason.INVALID_AMOUNT);
        }
        if (preApproved.isEmpty()) {
            return Optional.of(RejectionReason.PRE_APPROVED_NOT_FOUND);
        }
        PreApproved current = preApproved.get();
        if (!current.belongsTo(command.customerId())) {
            return Optional.of(RejectionReason.CUSTOMER_MISMATCH);
        }
        if (!current.isActive()) {
            return Optional.of(RejectionReason.PRE_APPROVED_NOT_ACTIVE);
        }
        if (command.amount().compareTo(current.availableAmount()) > 0) {
            return Optional.of(RejectionReason.INSUFFICIENT_AVAILABLE_AMOUNT);
        }
        return Optional.empty();
    }
}
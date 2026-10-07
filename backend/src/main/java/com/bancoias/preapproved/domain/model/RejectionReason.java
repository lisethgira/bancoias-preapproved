package com.bancoias.preapproved.domain.model;

public enum RejectionReason {
    INVALID_AMOUNT("El valor solicitado debe ser mayor que cero"),
    PRE_APPROVED_NOT_FOUND("El preaprobado no existe"),
    CUSTOMER_MISMATCH("El preaprobado no corresponde al cliente informado"),
    PRE_APPROVED_NOT_ACTIVE("El preaprobado no se encuentra habilitado"),
    INSUFFICIENT_AVAILABLE_AMOUNT("El valor solicitado supera el cupo disponible");

    private final String description;

    RejectionReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
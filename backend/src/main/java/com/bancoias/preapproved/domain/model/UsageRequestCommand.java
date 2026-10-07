package com.bancoias.preapproved.domain.model;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record UsageRequestCommand(
        String requestReference,
        String preApprovedId,
        String customerId,
        BigDecimal amount
) {
    /**
     * Huella SHA-256 del contenido. Permite detectar si una referencia repetida
     * llega con datos diferentes a los de la solicitud original (RF05).
     * El monto se normaliza para que 600000 y 600000.00 produzcan la misma huella.
     */
    public String payloadHash() {
        String normalizedAmount = amount == null ? "null" : amount.stripTrailingZeros().toPlainString();
        String canonical = String.join("|", requestReference, preApprovedId, customerId, normalizedAmount);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
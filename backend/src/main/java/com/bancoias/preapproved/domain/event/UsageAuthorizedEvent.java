package com.bancoias.preapproved.domain.event;

import com.bancoias.preapproved.domain.model.UsageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Hecho de negocio: se autorizó la utilización de un preaprobado.
 * El eventId permite a los consumidores descartar duplicados (entrega al menos una vez).
 */
public record UsageAuthorizedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String requestReference,
        String preApprovedId,
        String customerId,
        BigDecimal amount
) {
    public static final String TYPE = "PreApprovedUsageAuthorized";

    public static UsageAuthorizedEvent from(UsageRequest request, UUID eventId, Instant occurredAt) {
        return new UsageAuthorizedEvent(eventId, TYPE, occurredAt,
                request.requestReference(), request.preApprovedId(),
                request.customerId(), request.amount());
    }
}
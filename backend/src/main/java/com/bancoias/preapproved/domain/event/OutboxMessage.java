package com.bancoias.preapproved.domain.event;

import java.util.UUID;

/** Evento pendiente de publicar, ya serializado. */
public record OutboxMessage(UUID id, String eventType, String payload) {
}
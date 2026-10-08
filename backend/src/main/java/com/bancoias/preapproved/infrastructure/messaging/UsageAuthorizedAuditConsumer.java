package com.bancoias.preapproved.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Consumidor de ejemplo que simula un sistema que reacciona a la utilización autorizada.
 * RabbitMQ entrega "al menos una vez", así que descarta duplicados por eventId.
 */
@Component
public class UsageAuthorizedAuditConsumer {

    private static final Logger log = LoggerFactory.getLogger(UsageAuthorizedAuditConsumer.class);

    private final DatabaseClient client;
    private final Clock clock;

    public UsageAuthorizedAuditConsumer(DatabaseClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    @RabbitListener(queues = RabbitTopologyConfig.AUDIT_QUEUE)
    public void onUsageAuthorized(Message message) {
        UUID eventId = UUID.fromString(message.getMessageProperties().getMessageId());
        Object eventType = message.getMessageProperties().getHeaders().get("eventType");

        Long inserted = client.sql("""
                        INSERT INTO processed_event (event_id, event_type, processed_at)
                        VALUES (:eventId, :eventType, :processedAt)
                        ON CONFLICT (event_id) DO NOTHING
                        """)
                .bind("eventId", eventId)
                .bind("eventType", eventType == null ? "unknown" : eventType.toString())
                .bind("processedAt", Instant.now(clock))
                .fetch()
                .rowsUpdated()
                .block(Duration.ofSeconds(10));

        if (inserted == null || inserted == 0) {
            log.info("Evento {} duplicado: se descarta", eventId);
            return;
        }
       // Solo el identificador del evento: no se registran datos del cliente ni montos en los logs.
        log.info("Auditoría registrada para el evento {}", eventId);
    }
}
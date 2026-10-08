package com.bancoias.preapproved.infrastructure.messaging;

import com.bancoias.preapproved.domain.event.OutboxMessage;
import com.bancoias.preapproved.domain.port.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Publica los eventos pendientes del outbox.
 *
 * Se ejecuta en el hilo del planificador de Spring, no en el event loop de WebFlux,
 * por eso es seguro usar RabbitTemplate (bloqueante) y block() aquí.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final long CONFIRM_TIMEOUT_MS = 5000;

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;
    private final int batchSize;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           RabbitTemplate rabbitTemplate,
                           Clock clock,
                           @Value("${app.outbox.batch-size:50}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.outbox.publish-interval-ms:2000}")
    public void publishPending() {
        List<OutboxMessage> pending = outboxRepository.findPending(batchSize).collectList().block(TIMEOUT);
        if (pending == null || pending.isEmpty()) {
            return;
        }

        for (OutboxMessage outboxMessage : pending) {
            try {
                publish(outboxMessage);
                outboxRepository.markPublished(outboxMessage.id(), Instant.now(clock)).block(TIMEOUT);
                log.info("Evento {} publicado en RabbitMQ", outboxMessage.id());
            } catch (Exception e) {
                // El evento sigue pendiente y se reintentará en el siguiente ciclo.
                log.warn("No se pudo publicar el evento {}: {}", outboxMessage.id(), e.getMessage());
                return;
            }
        }
    }

    private void publish(OutboxMessage outboxMessage) {
        Message message = MessageBuilder
                .withBody(outboxMessage.payload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(outboxMessage.id().toString())
                .setHeader("eventType", outboxMessage.eventType())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();

        rabbitTemplate.invoke(operations -> {
            operations.send(RabbitTopologyConfig.EXCHANGE, RabbitTopologyConfig.ROUTING_KEY, message);
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            return true;
        });
    }
}
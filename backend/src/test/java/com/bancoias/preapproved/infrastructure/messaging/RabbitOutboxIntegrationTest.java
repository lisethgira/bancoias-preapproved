package com.bancoias.preapproved.infrastructure.messaging;

import com.bancoias.preapproved.TestcontainersConfiguration;
import com.bancoias.preapproved.application.UsageRequestService;
import com.bancoias.preapproved.domain.model.UsageRequestCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RabbitOutboxIntegrationTest {

    @Autowired
    private UsageRequestService service;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void resetData() {
        databaseClient.sql("DELETE FROM processed_event").then().block();
        databaseClient.sql("DELETE FROM outbox_event").then().block();
        databaseClient.sql("DELETE FROM usage_request").then().block();
        databaseClient.sql("UPDATE pre_approved SET available_amount = 1000000 WHERE id = 'PRA-1001'").then().block();
    }

    private void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(250);
        }
        fail("La condición no se cumplió en 15 segundos");
    }

    private long count(String sql, String param, Object value) {
        return databaseClient.sql(sql)
                .bind(param, value)
                .map(row -> row.get("total", Long.class))
                .one()
                .block();
    }

    @Test
    @DisplayName("Una utilización autorizada se publica en RabbitMQ y la recibe el consumidor")
    void authorizedUsageIsPublishedAndConsumed() throws InterruptedException {
        service.process(new UsageRequestCommand("MQ-001", "PRA-1001", "USR-10", new BigDecimal("100000"))).block();

        UUID eventId = databaseClient.sql("SELECT id FROM outbox_event WHERE aggregate_id = 'MQ-001'")
                .map(row -> row.get("id", UUID.class))
                .one()
                .block();

        await(() -> count("SELECT COUNT(*) AS total FROM processed_event WHERE event_id = :id", "id", eventId) == 1);

        assertThat(count("SELECT COUNT(*) AS total FROM outbox_event WHERE id = :id AND published_at IS NOT NULL",
                "id", eventId)).isEqualTo(1);
    }

    @Test
    @DisplayName("El consumidor descarta un mensaje duplicado con el mismo eventId")
    void consumerDiscardsDuplicateMessages() throws InterruptedException {
        UUID eventId = UUID.randomUUID();
        Message message = MessageBuilder.withBody("{}".getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(eventId.toString())
                .setHeader("eventType", "PreApprovedUsageAuthorized")
                .build();

        rabbitTemplate.send(RabbitTopologyConfig.EXCHANGE, RabbitTopologyConfig.ROUTING_KEY, message);
        rabbitTemplate.send(RabbitTopologyConfig.EXCHANGE, RabbitTopologyConfig.ROUTING_KEY, message);

        await(() -> count("SELECT COUNT(*) AS total FROM processed_event WHERE event_id = :id", "id", eventId) == 1);
        Thread.sleep(2000); // margen para que el segundo mensaje también se consuma

        assertThat(count("SELECT COUNT(*) AS total FROM processed_event WHERE event_id = :id", "id", eventId))
                .isEqualTo(1);
    }
}
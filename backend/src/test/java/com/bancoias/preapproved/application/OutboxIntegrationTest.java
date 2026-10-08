package com.bancoias.preapproved.application;

import com.bancoias.preapproved.TestcontainersConfiguration;
import com.bancoias.preapproved.domain.model.UsageRequestCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxIntegrationTest {

    @Autowired
    private UsageRequestService service;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void resetData() {
        databaseClient.sql("DELETE FROM outbox_event").then().block();
        databaseClient.sql("DELETE FROM usage_request").then().block();
        databaseClient.sql("UPDATE pre_approved SET available_amount = 1000000 WHERE id = 'PRA-1001'").then().block();
    }

    private long outboxEventsFor(String reference) {
        return databaseClient.sql("SELECT COUNT(*) AS total FROM outbox_event WHERE aggregate_id = :ref")
                .bind("ref", reference)
                .map(row -> row.get("total", Long.class))
                .one()
                .block();
    }

    private UsageRequestCommand command(String reference, String preApprovedId, String amount) {
        return new UsageRequestCommand(reference, preApprovedId, "USR-10", new BigDecimal(amount));
    }

    @Test
    @DisplayName("Una solicitud autorizada registra exactamente un evento en el outbox")
    void authorizedRequestWritesOutboxEvent() {
        service.process(command("OUT-001", "PRA-1001", "100000")).block();

        assertThat(outboxEventsFor("OUT-001")).isEqualTo(1);
    }

    @Test
    @DisplayName("Una solicitud rechazada no registra eventos")
    void rejectedRequestDoesNotWriteOutboxEvent() {
        service.process(command("OUT-002", "PRA-1002", "100000")).block();

        assertThat(outboxEventsFor("OUT-002")).isZero();
    }

    @Test
    @DisplayName("Un reintento no genera un segundo evento")
    void replayDoesNotWriteSecondEvent() {
        service.process(command("OUT-003", "PRA-1001", "100000")).block();
        service.process(command("OUT-003", "PRA-1001", "100000")).block();

        assertThat(outboxEventsFor("OUT-003")).isEqualTo(1);
    }

    @Test
    @DisplayName("Reintentos simultáneos: el rollback de los perdedores también revierte su evento")
    void concurrentReplaysWriteSingleEvent() {
        Flux.range(1, 5)
                .flatMap(i -> service.process(command("OUT-004", "PRA-1001", "100000"))
                        .subscribeOn(Schedulers.parallel()), 5)
                .collectList()
                .block();

        assertThat(outboxEventsFor("OUT-004")).isEqualTo(1);
    }
}
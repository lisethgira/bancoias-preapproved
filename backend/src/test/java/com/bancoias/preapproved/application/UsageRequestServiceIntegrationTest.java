package com.bancoias.preapproved.application;

import com.bancoias.preapproved.TestcontainersConfiguration;
import com.bancoias.preapproved.domain.exception.RequestReferenceConflictException;
import com.bancoias.preapproved.domain.model.RejectionReason;
import com.bancoias.preapproved.domain.model.UsageRequestCommand;
import com.bancoias.preapproved.domain.model.UsageRequestStatus;
import com.bancoias.preapproved.domain.port.PreApprovedRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UsageRequestServiceIntegrationTest {

    @Autowired
    private UsageRequestService service;

    @Autowired
    private PreApprovedRepository preApprovedRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void resetData() {
        databaseClient.sql("DELETE FROM usage_request").then().block();
        databaseClient.sql("UPDATE pre_approved SET available_amount = 1000000 WHERE id = 'PRA-1001'").then().block();
        databaseClient.sql("UPDATE pre_approved SET available_amount = 800000 WHERE id = 'PRA-1002'").then().block();
        databaseClient.sql("UPDATE pre_approved SET available_amount = 2000000 WHERE id = 'PRA-2001'").then().block();
    }

    private UsageRequestCommand command(String reference, String preApprovedId, String customerId, String amount) {
        return new UsageRequestCommand(reference, preApprovedId, customerId, new BigDecimal(amount));
    }

    private BigDecimal availableAmount(String preApprovedId) {
        return preApprovedRepository.findById(preApprovedId).block().availableAmount();
    }

    private long countRequests(String reference) {
        return databaseClient.sql("SELECT COUNT(*) AS total FROM usage_request WHERE request_reference = :ref")
                .bind("ref", reference)
                .map(row -> row.get("total", Long.class))
                .one()
                .block();
    }

    @Test
    @DisplayName("RF01/RF02: autoriza una solicitud válida y descuenta el cupo")
    void authorizesAndDecreasesAvailableAmount() {
        ProcessingResult result = service.process(command("REF-001", "PRA-1001", "USR-10", "600000")).block();

        assertThat(result.replayed()).isFalse();
        assertThat(result.request().status()).isEqualTo(UsageRequestStatus.AUTHORIZED);
        assertThat(result.request().id()).isNotNull();
        assertThat(result.request().processedAt()).isNotNull();
        assertThat(availableAmount("PRA-1001")).isEqualByComparingTo("400000");
    }

    @Test
    @DisplayName("RF03: una solicitud rechazada se guarda y no disminuye el cupo")
    void rejectedRequestIsPersistedAndDoesNotDecrease() {
        ProcessingResult blocked = service.process(command("REF-002", "PRA-1002", "USR-10", "100000")).block();
        ProcessingResult tooHigh = service.process(command("REF-003", "PRA-1001", "USR-10", "1500000")).block();

        assertThat(blocked.request().status()).isEqualTo(UsageRequestStatus.REJECTED);
        assertThat(blocked.request().rejectionReason()).isEqualTo(RejectionReason.PRE_APPROVED_NOT_ACTIVE);
        assertThat(tooHigh.request().rejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_AVAILABLE_AMOUNT);

        assertThat(availableAmount("PRA-1002")).isEqualByComparingTo("800000");
        assertThat(availableAmount("PRA-1001")).isEqualByComparingTo("1000000");
        assertThat(countRequests("REF-002")).isEqualTo(1);
        assertThat(countRequests("REF-003")).isEqualTo(1);
    }

    @Test
    @DisplayName("RF04: 10 solicitudes simultáneas de 200.000 sobre 1.000.000 autorizan exactamente 5")
    void concurrentRequestsNeverExceedAvailableAmount() {
        List<ProcessingResult> results = Flux.range(1, 10)
                .flatMap(i -> service.process(command("REF-C-" + i, "PRA-1001", "USR-10", "200000"))
                        .subscribeOn(Schedulers.parallel()), 10)
                .collectList()
                .block();

        long authorized = results.stream()
                .filter(r -> r.request().status() == UsageRequestStatus.AUTHORIZED)
                .count();
        long rejected = results.stream()
                .filter(r -> r.request().rejectionReason() == RejectionReason.INSUFFICIENT_AVAILABLE_AMOUNT)
                .count();

        assertThat(results).hasSize(10);
        assertThat(authorized).isEqualTo(5);
        assertThat(rejected).isEqualTo(5);
        assertThat(availableAmount("PRA-1001")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("RF05: una referencia repetida devuelve el resultado original sin descontar de nuevo")
    void repeatedReferenceReturnsOriginalResult() {
        UsageRequestCommand cmd = command("REF-010", "PRA-1001", "USR-10", "600000");

        ProcessingResult first = service.process(cmd).block();
        ProcessingResult second = service.process(cmd).block();

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.request().id()).isEqualTo(first.request().id());
        assertThat(second.request().status()).isEqualTo(UsageRequestStatus.AUTHORIZED);
        assertThat(availableAmount("PRA-1001")).isEqualByComparingTo("400000");
        assertThat(countRequests("REF-010")).isEqualTo(1);
    }

    @Test
    @DisplayName("RF04+RF05: la misma referencia enviada 5 veces en simultáneo descuenta una sola vez")
    void concurrentRepeatedReferenceIsProcessedOnce() {
        UsageRequestCommand cmd = command("REF-020", "PRA-1001", "USR-10", "600000");

        List<ProcessingResult> results = Flux.range(1, 5)
                .flatMap(i -> service.process(cmd).subscribeOn(Schedulers.parallel()), 5)
                .collectList()
                .block();

        Long originalId = results.get(0).request().id();
        assertThat(results).allSatisfy(r -> {
            assertThat(r.request().id()).isEqualTo(originalId);
            assertThat(r.request().status()).isEqualTo(UsageRequestStatus.AUTHORIZED);
        });
        assertThat(results.stream().filter(r -> !r.replayed()).count()).isEqualTo(1);
        assertThat(availableAmount("PRA-1001")).isEqualByComparingTo("400000");
        assertThat(countRequests("REF-020")).isEqualTo(1);
    }

    @Test
    @DisplayName("RF05: la misma referencia con datos diferentes genera conflicto y preserva la original")
    void sameReferenceWithDifferentDataIsRejectedAsConflict() {
        service.process(command("REF-030", "PRA-1001", "USR-10", "600000")).block();

        StepVerifier.create(service.process(command("REF-030", "PRA-1001", "USR-10", "300000")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(RequestReferenceConflictException.class);
                    var original = ((RequestReferenceConflictException) error).getOriginalRequest();
                    assertThat(original.amount()).isEqualByComparingTo("600000");
                })
                .verify();

        assertThat(availableAmount("PRA-1001")).isEqualByComparingTo("400000");
        assertThat(countRequests("REF-030")).isEqualTo(1);
    }

    @Test
    @DisplayName("RF06: consulta por referencia y solicitudes recientes")
    void findsByReferenceAndRecent() {
        service.process(command("REF-040", "PRA-1001", "USR-10", "100000")).block();
        service.process(command("REF-041", "PRA-2001", "USR-20", "100000")).block();

        StepVerifier.create(service.findByReference("REF-040"))
                .assertNext(r -> assertThat(r.requestReference()).isEqualTo("REF-040"))
                .verifyComplete();

        StepVerifier.create(service.findByReference("NO-EXISTE"))
                .verifyComplete();

        StepVerifier.create(service.findRecent(10).collectList())
                .assertNext(list -> {
                    assertThat(list).hasSize(2);
                    assertThat(list.get(0).requestReference()).isEqualTo("REF-041");
                })
                .verifyComplete();
    }
}
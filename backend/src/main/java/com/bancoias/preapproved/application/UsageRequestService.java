package com.bancoias.preapproved.application;

import com.bancoias.preapproved.domain.exception.RequestReferenceConflictException;
import com.bancoias.preapproved.domain.model.RejectionReason;
import com.bancoias.preapproved.domain.model.UsageRequest;
import com.bancoias.preapproved.domain.model.UsageRequestCommand;
import com.bancoias.preapproved.domain.port.PreApprovedRepository;
import com.bancoias.preapproved.domain.port.UsageRequestRepository;
import com.bancoias.preapproved.domain.service.UsagePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import com.bancoias.preapproved.domain.event.UsageAuthorizedEvent;
import com.bancoias.preapproved.domain.port.OutboxRepository;
import java.util.UUID;

@Service
public class UsageRequestService {

    private static final Logger log = LoggerFactory.getLogger(UsageRequestService.class);
    private static final int MAX_RECENT = 100;

        private final PreApprovedRepository preApprovedRepository;
    private final UsageRequestRepository usageRequestRepository;
    private final OutboxRepository outboxRepository;
    private final UsagePolicy policy;
    private final TransactionalOperator transactionalOperator;
    private final Clock clock;

    public UsageRequestService(PreApprovedRepository preApprovedRepository,
                               UsageRequestRepository usageRequestRepository,
                               OutboxRepository outboxRepository,
                               UsagePolicy policy,
                               TransactionalOperator transactionalOperator,
                               Clock clock) {
        this.preApprovedRepository = preApprovedRepository;
        this.usageRequestRepository = usageRequestRepository;
        this.outboxRepository = outboxRepository;
        this.policy = policy;
        this.transactionalOperator = transactionalOperator;
        this.clock = clock;
    }

    /** RF01 a RF05: procesa una solicitud de uso de forma idempotente y consistente. */
    public Mono<ProcessingResult> process(UsageRequestCommand command) {
        String payloadHash = command.payloadHash();

        return usageRequestRepository.findByReference(command.requestReference())
                .map(existing -> replay(existing, payloadHash))
                .switchIfEmpty(Mono.defer(() -> processNew(command)))
                // Carrera: dos solicitudes con la misma referencia pasaron la consulta inicial.
                // La restricción UNIQUE rechaza la segunda, su transacción hace rollback
                // (incluido el descuento del cupo) y se responde con la original.
                .onErrorResume(DataIntegrityViolationException.class, error ->
                        usageRequestRepository.findByReference(command.requestReference())
                                .map(existing -> replay(existing, payloadHash))
                                .switchIfEmpty(Mono.error(error)));
    }

    /** RF06: consulta por referencia. */
    public Mono<UsageRequest> findByReference(String requestReference) {
        return usageRequestRepository.findByReference(requestReference);
    }

    /** RF06: solicitudes procesadas recientemente. */
    public Flux<UsageRequest> findRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_RECENT));
        return usageRequestRepository.findRecent(safeLimit);
    }

    private Mono<ProcessingResult> processNew(UsageRequestCommand command) {
        Mono<UsageRequest> flow = preApprovedRepository.findById(command.preApprovedId())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(preApproved -> policy.evaluate(command, preApproved)
                        .map(reason -> Mono.just(UsageRequest.rejected(command, reason, now())))
                        .orElseGet(() -> consume(command)))
                .flatMap(usageRequestRepository::save)
                // Outbox: el evento se guarda en la MISMA transacción que la autorización.
                // Si la transacción se revierte, el evento también; nunca se anuncia algo que no ocurrió.
                .flatMap(saved -> saved.isAuthorized()
                        ? outboxRepository.save(UsageAuthorizedEvent.from(saved, UUID.randomUUID(), now()))
                                .thenReturn(saved)
                        : Mono.just(saved))
                .doOnNext(saved -> log.info("Solicitud {} procesada: {} {}",
                        saved.requestReference(), saved.status(),
                        saved.rejectionReason() == null ? "" : saved.rejectionReason()));

        return transactionalOperator.transactional(flow)
                .map(saved -> new ProcessingResult(saved, false));
    }

    private Mono<UsageRequest> consume(UsageRequestCommand command) {
        return preApprovedRepository
                .tryConsume(command.preApprovedId(), command.customerId(), command.amount())
                .map(consumed -> consumed
                        ? UsageRequest.authorized(command, now())
                        : UsageRequest.rejected(command, RejectionReason.INSUFFICIENT_AVAILABLE_AMOUNT, now()));
    }

    private ProcessingResult replay(UsageRequest existing, String payloadHash) {
        if (!existing.payloadHash().equals(payloadHash)) {
            throw new RequestReferenceConflictException(existing);
        }
        log.info("Referencia {} repetida: se devuelve el resultado original", existing.requestReference());
        return new ProcessingResult(existing, true);
    }

    private Instant now() {
        // PostgreSQL guarda microsegundos; se trunca para que la respuesta original
        // y la de un reintento sean idénticas.
        return Instant.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
}
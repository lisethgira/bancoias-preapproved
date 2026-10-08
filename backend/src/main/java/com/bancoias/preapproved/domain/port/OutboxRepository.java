package com.bancoias.preapproved.domain.port;

import com.bancoias.preapproved.domain.event.OutboxMessage;
import com.bancoias.preapproved.domain.event.UsageAuthorizedEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface OutboxRepository {

    Mono<Void> save(UsageAuthorizedEvent event);

    Flux<OutboxMessage> findPending(int limit);

    Mono<Void> markPublished(UUID id, Instant publishedAt);
}
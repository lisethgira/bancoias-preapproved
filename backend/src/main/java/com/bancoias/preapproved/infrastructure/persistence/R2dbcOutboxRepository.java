package com.bancoias.preapproved.infrastructure.persistence;

import com.bancoias.preapproved.domain.event.OutboxMessage;
import com.bancoias.preapproved.domain.event.UsageAuthorizedEvent;
import com.bancoias.preapproved.domain.port.OutboxRepository;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Repository
public class R2dbcOutboxRepository implements OutboxRepository {

    private final DatabaseClient client;
    private final ObjectMapper objectMapper;

    public R2dbcOutboxRepository(DatabaseClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> save(UsageAuthorizedEvent event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .flatMap(payload -> client.sql("""
                                INSERT INTO outbox_event (id, aggregate_id, event_type, payload, created_at)
                                VALUES (:id, :aggregateId, :eventType, :payload, :createdAt)
                                """)
                        .bind("id", event.eventId())
                        .bind("aggregateId", event.requestReference())
                        .bind("eventType", event.eventType())
                        .bind("payload", payload)
                        .bind("createdAt", event.occurredAt())
                        .then());
    }

    @Override
    public Flux<OutboxMessage> findPending(int limit) {
        return client.sql("""
                        SELECT id, event_type, payload
                        FROM outbox_event
                        WHERE published_at IS NULL
                        ORDER BY created_at
                        LIMIT :limit
                        """)
                .bind("limit", limit)
                .map(row -> new OutboxMessage(
                        row.get("id", UUID.class),
                        row.get("event_type", String.class),
                        row.get("payload", String.class)))
                .all();
    }

    @Override
    public Mono<Void> markPublished(UUID id, Instant publishedAt) {
        return client.sql("UPDATE outbox_event SET published_at = :publishedAt WHERE id = :id")
                .bind("publishedAt", publishedAt)
                .bind("id", id)
                .then();
    }
}
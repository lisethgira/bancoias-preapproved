package com.bancoias.preapproved.infrastructure.persistence;

import com.bancoias.preapproved.domain.model.RejectionReason;
import com.bancoias.preapproved.domain.model.UsageRequest;
import com.bancoias.preapproved.domain.model.UsageRequestStatus;
import com.bancoias.preapproved.domain.port.UsageRequestRepository;
import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

@Repository
public class R2dbcUsageRequestRepository implements UsageRequestRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, request_reference, pre_approved_id, customer_id, amount,
                   payload_hash, status, rejection_reason, processed_at
            FROM usage_request
            """;

    private final DatabaseClient client;

    public R2dbcUsageRequestRepository(DatabaseClient client) {
        this.client = client;
    }

    @Override
    public Mono<UsageRequest> save(UsageRequest r) {
        DatabaseClient.GenericExecuteSpec spec = client.sql("""
                        INSERT INTO usage_request
                            (request_reference, pre_approved_id, customer_id, amount,
                             payload_hash, status, rejection_reason, processed_at)
                        VALUES (:reference, :preApprovedId, :customerId, :amount,
                                :payloadHash, :status, :rejectionReason, :processedAt)
                        RETURNING id
                        """)
                .bind("reference", r.requestReference())
                .bind("preApprovedId", r.preApprovedId())
                .bind("customerId", r.customerId())
                .bind("amount", r.amount())
                .bind("payloadHash", r.payloadHash())
                .bind("status", r.status().name())
                .bind("processedAt", r.processedAt());

        spec = r.rejectionReason() == null
                ? spec.bindNull("rejectionReason", String.class)
                : spec.bind("rejectionReason", r.rejectionReason().name());

        return spec.map(row -> row.get("id", Long.class))
                .one()
                .map(id -> new UsageRequest(id, r.requestReference(), r.preApprovedId(),
                        r.customerId(), r.amount(), r.payloadHash(), r.status(),
                        r.rejectionReason(), r.processedAt()));
    }

    @Override
    public Mono<UsageRequest> findByReference(String requestReference) {
        return client.sql(SELECT_COLUMNS + " WHERE request_reference = :reference")
                .bind("reference", requestReference)
                .map(R2dbcUsageRequestRepository::toDomain)
                .one();
    }

    @Override
    public Flux<UsageRequest> findRecent(int limit) {
        return client.sql(SELECT_COLUMNS + " ORDER BY processed_at DESC, id DESC LIMIT :limit")
                .bind("limit", limit)
                .map(R2dbcUsageRequestRepository::toDomain)
                .all();
    }

    private static UsageRequest toDomain(Readable row) {
        String reason = row.get("rejection_reason", String.class);
        return new UsageRequest(
                row.get("id", Long.class),
                row.get("request_reference", String.class),
                row.get("pre_approved_id", String.class),
                row.get("customer_id", String.class),
                row.get("amount", BigDecimal.class),
                row.get("payload_hash", String.class),
                UsageRequestStatus.valueOf(row.get("status", String.class)),
                reason == null ? null : RejectionReason.valueOf(reason),
                row.get("processed_at", Instant.class));
    }
}
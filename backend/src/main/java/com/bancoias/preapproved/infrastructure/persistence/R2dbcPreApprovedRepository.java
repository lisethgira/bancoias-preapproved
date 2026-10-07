package com.bancoias.preapproved.infrastructure.persistence;

import com.bancoias.preapproved.domain.model.PreApproved;
import com.bancoias.preapproved.domain.model.PreApprovedStatus;
import com.bancoias.preapproved.domain.port.PreApprovedRepository;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Repository
public class R2dbcPreApprovedRepository implements PreApprovedRepository {

    private final DatabaseClient client;

    public R2dbcPreApprovedRepository(DatabaseClient client) {
        this.client = client;
    }

    @Override
    public Mono<PreApproved> findById(String id) {
        return client.sql("""
                        SELECT id, customer_id, status, available_amount
                        FROM pre_approved
                        WHERE id = :id
                        """)
                .bind("id", id)
                .map(row -> new PreApproved(
                        row.get("id", String.class),
                        row.get("customer_id", String.class),
                        PreApprovedStatus.valueOf(row.get("status", String.class)),
                        row.get("available_amount", BigDecimal.class)))
                .one();
    }

    @Override
    public Mono<Boolean> tryConsume(String id, String customerId, BigDecimal amount) {
        // Una sola sentencia: la base de datos bloquea la fila mientras actualiza,
        // así dos solicitudes simultáneas nunca pueden descontar el mismo cupo.
        return client.sql("""
                        UPDATE pre_approved
                        SET available_amount = available_amount - :amount
                        WHERE id = :id
                          AND customer_id = :customerId
                          AND status = 'ACTIVE'
                          AND available_amount >= :amount
                        """)
                .bind("amount", amount)
                .bind("id", id)
                .bind("customerId", customerId)
                .fetch()
                .rowsUpdated()
                .map(rows -> rows == 1);
    }
}
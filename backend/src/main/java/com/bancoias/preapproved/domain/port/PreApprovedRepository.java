package com.bancoias.preapproved.domain.port;

import com.bancoias.preapproved.domain.model.PreApproved;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface PreApprovedRepository {

    Mono<PreApproved> findById(String id);

    /**
     * Descuenta el cupo de forma atómica solo si el preaprobado está activo,
     * pertenece al cliente y tiene cupo suficiente.
     * Devuelve true si se descontó, false si no se cumplieron las condiciones.
     */
    Mono<Boolean> tryConsume(String id, String customerId, BigDecimal amount);
}
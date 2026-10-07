package com.bancoias.preapproved.domain.port;

import com.bancoias.preapproved.domain.model.UsageRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UsageRequestRepository {

    Mono<UsageRequest> save(UsageRequest request);

    Mono<UsageRequest> findByReference(String requestReference);

    Flux<UsageRequest> findRecent(int limit);
}
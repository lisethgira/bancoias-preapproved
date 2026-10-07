package com.bancoias.preapproved.infrastructure.web;

import com.bancoias.preapproved.application.UsageRequestService;
import com.bancoias.preapproved.infrastructure.web.dto.UsageRequestDto;
import com.bancoias.preapproved.infrastructure.web.dto.UsageRequestResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/usage-requests")
public class UsageRequestController {

    private final UsageRequestService service;

    public UsageRequestController(UsageRequestService service) {
        this.service = service;
    }

    /**
     * RF01: procesa una solicitud.
     * 201 si es nueva (autorizada o rechazada), 200 si es un reintento de una referencia ya procesada.
     */
    @PostMapping
    public Mono<ResponseEntity<UsageRequestResponse>> process(@Valid @RequestBody UsageRequestDto body) {
        return service.process(body.toCommand())
                .map(result -> ResponseEntity
                        .status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                        .body(UsageRequestResponse.from(result.request(), result.replayed())));
    }

    /** RF06: consulta por referencia. */
    @GetMapping("/{requestReference}")
    public Mono<ResponseEntity<UsageRequestResponse>> findByReference(@PathVariable String requestReference) {
        return service.findByReference(requestReference)
                .map(r -> ResponseEntity.ok(UsageRequestResponse.from(r)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /** RF06: solicitudes procesadas recientemente. */
    @GetMapping
    public Flux<UsageRequestResponse> findRecent(@RequestParam(defaultValue = "20") int limit) {
        return service.findRecent(limit).map(UsageRequestResponse::from);
    }
}
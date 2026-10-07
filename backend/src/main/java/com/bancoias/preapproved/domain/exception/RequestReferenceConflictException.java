package com.bancoias.preapproved.domain.exception;

import com.bancoias.preapproved.domain.model.UsageRequest;

public class RequestReferenceConflictException extends RuntimeException {

    private final UsageRequest originalRequest;

    public RequestReferenceConflictException(UsageRequest originalRequest) {
        super("La referencia " + originalRequest.requestReference()
                + " ya fue procesada con información diferente");
        this.originalRequest = originalRequest;
    }

    public UsageRequest getOriginalRequest() {
        return originalRequest;
    }
}
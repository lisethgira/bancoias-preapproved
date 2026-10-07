package com.bancoias.preapproved.infrastructure.web;

import com.bancoias.preapproved.domain.exception.RequestReferenceConflictException;
import com.bancoias.preapproved.infrastructure.web.dto.ErrorResponse;
import com.bancoias.preapproved.infrastructure.web.dto.UsageRequestResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** RF05: misma referencia con información diferente. */
    @ExceptionHandler(RequestReferenceConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(RequestReferenceConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.conflict(ex.getMessage(),
                        UsageRequestResponse.from(ex.getOriginalRequest())));
    }

    /** Campos obligatorios faltantes o con formato inválido. */
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleValidation(WebExchangeBindException ex) {
        List<String> details = ex.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("INVALID_REQUEST", "La solicitud tiene datos inválidos", details));
    }

    /** JSON mal formado o tipos incorrectos. */
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ErrorResponse> handleInput(ServerWebInputException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("MALFORMED_REQUEST", "El cuerpo de la solicitud no es válido", List.of()));
    }

    /** Cualquier otro error: no se exponen detalles internos al cliente. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Error inesperado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "Ocurrió un error inesperado", List.of()));
    }
}
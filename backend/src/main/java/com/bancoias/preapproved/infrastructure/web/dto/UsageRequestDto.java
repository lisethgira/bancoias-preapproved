package com.bancoias.preapproved.infrastructure.web.dto;

import com.bancoias.preapproved.domain.model.UsageRequestCommand;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UsageRequestDto(
        @NotBlank @Size(max = 50) String requestReference,
        @NotBlank @Size(max = 20) String preApprovedId,
        @NotBlank @Size(max = 20) String customerId,
        @NotNull @Digits(integer = 13, fraction = 2) BigDecimal amount
) {
    public UsageRequestCommand toCommand() {
        // Escala fija de 2 decimales, igual que NUMERIC(15,2) en la base de datos.
        // @Digits ya garantiza máximo 2 decimales, así que no hay redondeo.
        return new UsageRequestCommand(requestReference.trim(), preApprovedId.trim(), customerId.trim(),
                amount.setScale(2, java.math.RoundingMode.UNNECESSARY));
    }
}
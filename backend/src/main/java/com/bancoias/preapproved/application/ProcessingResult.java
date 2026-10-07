package com.bancoias.preapproved.application;

import com.bancoias.preapproved.domain.model.UsageRequest;

/**
 * @param request  la solicitud procesada (nueva o la original si es un reintento)
 * @param replayed true si la referencia ya existía y se devolvió el resultado original
 */
public record ProcessingResult(UsageRequest request, boolean replayed) {
}
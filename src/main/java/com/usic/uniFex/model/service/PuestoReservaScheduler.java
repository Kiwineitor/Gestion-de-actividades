package com.usic.uniFex.model.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PuestoReservaScheduler {

    private final PuestoReservaService reservaService;
    private final PuestoEventPublisher publisher;

    @Scheduled(fixedDelayString = "${unifex.reserva.barrido-ms:30000}")
    public void liberarVencidas() {
        try {
            reservaService.liberarVencidas().forEach(publisher::publicar);
        } catch (Exception e) {
            log.error("Fallo al liberar reservas vencidas", e);
        }
    }
}

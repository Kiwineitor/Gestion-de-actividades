package com.usic.uniFex.model.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.usic.uniFex.Config.WebSocketConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvisoPublicoService {

    public static final String TOPIC = WebSocketConfig.PREFIJO_PUBLICO + "cambios";

    private final SimpMessagingTemplate mensajeria;

    public void cambio(String tipo, Long id) {
        Map<String, Object> aviso = new LinkedHashMap<>();
        aviso.put("tipo", tipo);
        aviso.put("id", id);
        Runnable enviar = () -> {
            try {
                mensajeria.convertAndSend(TOPIC, aviso);
            } catch (RuntimeException e) {
                log.warn("No se pudo avisar el cambio público {} {}: {}", tipo, id, e.getMessage());
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            enviar.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                enviar.run();
            }
        });
    }
}

package com.usic.uniFex.model.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.usic.uniFex.model.dto.InteresadoStandDTO;
import com.usic.uniFex.model.entity.InteresadoStand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class InteresadoStandEventPublisher {

    public static final String TOPIC = "/topic/interesados";

    private final SimpMessagingTemplate messaging;

    public void publicar(InteresadoStand interesado) {
        try {
            messaging.convertAndSend(TOPIC, InteresadoStandDTO.de(interesado));
        } catch (Exception e) {
            log.error("No se pudo difundir el interesado {}", interesado.getId(), e);
        }
    }
}

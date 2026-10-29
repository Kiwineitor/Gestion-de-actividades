package com.usic.uniFex.model.service;

import java.util.Collection;
import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.dto.PuestoEstadoDTO;
import com.usic.uniFex.model.entity.Puesto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PuestoEventPublisher {

    public static final String TOPIC = "/topic/puestos";

    public static final String TOPIC_ASIGNACIONES = "/topic/asignaciones";

    private final SimpMessagingTemplate messaging;
    private final IPuestoDao puestoDao;

    @Transactional(readOnly = true)
    public void publicar(Long puestoId) {
        if (puestoId == null) return;
        publicarVarios(java.util.List.of(puestoId));
    }

    public void publicarAsignaciones(java.util.List<com.usic.uniFex.model.dto.AsignacionPuestoDTO> cambios) {
        if (cambios == null || cambios.isEmpty()) return;
        try {
            messaging.convertAndSend(TOPIC_ASIGNACIONES, cambios);
            log.info("Difundidos {} cambios de asignacion", cambios.size());
        } catch (Exception e) {
            log.error("No se pudieron difundir los cambios de asignacion", e);
        }
    }

    @Transactional(readOnly = true)
    public void publicarVarios(Collection<Long> puestoIds) {
        if (puestoIds == null || puestoIds.isEmpty()) return;
        try {
            List<Puesto> casetas = puestoDao.findWithCategoriaByIdIn(puestoIds);
            for (Puesto p : casetas) {
                messaging.convertAndSend(TOPIC, PuestoEstadoDTO.de(p));
            }
            log.info("Difundidas {} casetas de {} pedidas", casetas.size(), puestoIds.size());
        } catch (Exception e) {
            log.error("No se pudo difundir el estado de {} caseta(s)", puestoIds.size(), e);
        }
    }
}

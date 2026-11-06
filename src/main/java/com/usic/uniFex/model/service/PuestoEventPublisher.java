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
    private final com.usic.uniFex.model.dao.IEdicionDao ediciones;
    private final AvisoPublicoService avisoPublico;

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
            Long activa = ediciones.findFirstByActivaTrueOrderByAnioDesc().map(e -> e.getId()).orElse(null);
            for (Puesto p : casetas) {
                Long edicion = p.getCategoria() == null ? null : p.getCategoria().getIdEdicion();
                PuestoEstadoDTO dto = PuestoEstadoDTO.de(p);
                if (edicion != null) messaging.convertAndSend(TOPIC + "/ediciones/" + edicion, dto);
                if (java.util.Objects.equals(activa, edicion)) messaging.convertAndSend(TOPIC, dto);
            }
            log.info("Difundidas {} casetas de {} pedidas", casetas.size(), puestoIds.size());
            if (!casetas.isEmpty()) avisoPublico.cambio("feria", casetas.get(0).getId());
        } catch (Exception e) {
            log.error("No se pudo difundir el estado de {} caseta(s)", puestoIds.size(), e);
        }
    }
}

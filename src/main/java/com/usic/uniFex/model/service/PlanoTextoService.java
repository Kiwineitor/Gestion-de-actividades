package com.usic.uniFex.model.service;

import java.util.Date;
import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IEdicionDao;
import com.usic.uniFex.model.dao.IPlanoTextoDao;
import com.usic.uniFex.model.dto.PlanoTextoDTO;
import com.usic.uniFex.model.entity.PlanoTexto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanoTextoService {

    public static final String TOPIC = "/topic/plano-textos";

    private final IPlanoTextoDao dao;
    private final IEdicionDao edicionDao;
    private final SimpMessagingTemplate messaging;

    public record CambioTexto(String contenido, Double mapaX, Double mapaY, Double tamano,
                              String color, String colorBorde, Double grosorBorde, Double rotacion) {
    }

    @Transactional(readOnly = true)
    public List<PlanoTextoDTO> listar() {
        Long edicionId = edicionDao.findFirstByActivaTrueOrderByAnioDesc()
                .map(e -> e.getId()).orElse(null);
        return dao.vivosDe(edicionId).stream().map(PlanoTextoDTO::de).toList();
    }

    @Transactional
    public PlanoTextoDTO crear(CambioTexto req, Long usuarioId) {
        String contenido = limpio(req.contenido());
        if (contenido == null) throw new IllegalArgumentException("El texto no puede estar vacio");

        PlanoTexto t = new PlanoTexto();
        t.setContenido(contenido);
        t.setMapaX(req.mapaX() == null ? 0.5 : acotar(req.mapaX()));
        t.setMapaY(req.mapaY() == null ? 0.5 : acotar(req.mapaY()));
        t.setTamano(req.tamano() == null ? PlanoTexto.TAMANO_POR_DEFECTO : req.tamano());
        t.setColor(req.color() == null ? "#111827" : req.color());
        t.setColorBorde(req.colorBorde() == null ? "#ffffff" : req.colorBorde());
        t.setGrosorBorde(req.grosorBorde() == null ? PlanoTexto.GROSOR_BORDE_POR_DEFECTO : req.grosorBorde());
        t.setRotacion(req.rotacion() == null ? 0d : req.rotacion());
        t.setEdicion(edicionDao.findFirstByActivaTrueOrderByAnioDesc().orElse(null));
        t.setRegistro(new Date());
        t.setRegistroIdUsuario(usuarioId);
        t.setEstado(PlanoTexto.REGISTRO_ACTIVO);

        PlanoTextoDTO dto = PlanoTextoDTO.de(dao.save(t));
        difundir(dto);
        log.info("Rotulo {} creado en el plano por el usuario {}", dto.id(), usuarioId);
        return dto;
    }

    @Transactional
    public PlanoTextoDTO actualizar(Long id, CambioTexto req, Long usuarioId) {
        PlanoTexto t = dao.findById(id).orElse(null);
        if (t == null || PlanoTexto.REGISTRO_ANULADO.equals(t.getEstado())) return null;
        if (limpio(req.contenido()) != null) t.setContenido(limpio(req.contenido()));
        if (req.mapaX() != null) t.setMapaX(acotar(req.mapaX()));
        if (req.mapaY() != null) t.setMapaY(acotar(req.mapaY()));
        if (req.tamano() != null) t.setTamano(req.tamano());
        if (req.color() != null) t.setColor(req.color());
        if (req.colorBorde() != null) t.setColorBorde(req.colorBorde());
        if (req.grosorBorde() != null) t.setGrosorBorde(req.grosorBorde());
        if (req.rotacion() != null) t.setRotacion(req.rotacion());
        t.setModificacion(new Date());
        t.setModificacionIdUsuario(usuarioId);

        PlanoTextoDTO dto = PlanoTextoDTO.de(dao.save(t));
        difundir(dto);
        return dto;
    }

    @Transactional
    public boolean eliminar(Long id, Long usuarioId) {
        PlanoTexto t = dao.findById(id).orElse(null);
        if (t == null || PlanoTexto.REGISTRO_ANULADO.equals(t.getEstado())) return false;
        t.setEstado(PlanoTexto.REGISTRO_ANULADO);
        t.setModificacion(new Date());
        t.setModificacionIdUsuario(usuarioId);
        dao.save(t);
        difundir(PlanoTextoDTO.baja(id));
        log.info("Rotulo {} retirado del plano por el usuario {}", id, usuarioId);
        return true;
    }

    private void difundir(PlanoTextoDTO dto) {
        try {
            messaging.convertAndSend(TOPIC, dto);
        } catch (Exception e) {
            log.warn("No se pudo difundir el rotulo {}: {}", dto.id(), e.getMessage());
        }
    }

    private static double acotar(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static String limpio(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

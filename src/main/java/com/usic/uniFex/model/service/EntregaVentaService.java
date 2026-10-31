package com.usic.uniFex.model.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.dao.IResponsableDao;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.InscripcionPuesto;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.entity.Responsable;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EntregaVentaService {

    private final IInscripcionService inscripciones;
    private final IResponsableDao responsables;
    private final CredencialCodigoService codigos;

    @Transactional(readOnly = true)
    public Map<String, Object> deInscripcion(Long inscripcionId) {
        if (inscripcionId == null) return null;
        Inscripcion ins = inscripciones.findById(inscripcionId);
        if (ins == null) return null;
        BigDecimal total = BigDecimal.ZERO;
        List<String> espacios = new ArrayList<>();
        for (InscripcionPuesto ip : ins.getInscripcionPuestos()) {
            if ("X".equals(ip.getEstado())) continue;
            if (ip.getCosto() != null) total = total.add(ip.getCosto());
            Puesto p = ip.getPuesto();
            if (p != null) espacios.add((p.getCategoria() != null ? p.getCategoria().getNombre() + " · " : "") + p.getCodigo());
        }
        List<Map<String, Object>> personas = new ArrayList<>();
        if (ins.getEntidad() != null) {
            for (Responsable r : responsables.vivosDeEntidad(ins.getEntidad().getId())) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.getId());
                m.put("nombre", r.getPersona() != null ? r.getPersona().getNombreCompleto() : "");
                m.put("ci", r.getPersona() != null ? r.getPersona().getCi() : null);
                m.put("titular", r.isEsTitular());
                m.put("extra", r.isEsExtra());
                m.put("tieneFoto", r.getPersona() != null && r.getPersona().getFoto() != null && !r.getPersona().getFoto().isBlank());
                m.put("codigo", codigos.codigoDe(r.getId()));
                personas.add(m);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inscripcionId", ins.getId());
        m.put("anulada", "X".equals(ins.getEstado()) || "CANCELADA".equalsIgnoreCase(String.valueOf(ins.getInscripcionEstado())));
        m.put("entidad", ins.getEntidad() != null ? ins.getEntidad().getNombre() : null);
        m.put("representante", ins.getEntidad() != null ? ins.getEntidad().getRepresentanteLegal() : null);
        m.put("celular", ins.getEntidad() != null ? ins.getEntidad().getCelularRepresentante() : null);
        m.put("actividad", ins.getEdicion() != null ? ins.getEdicion().getNombre() : null);
        m.put("total", total);
        m.put("espacios", espacios);
        m.put("conComprobante", ins.getImgComprobante() != null && !ins.getImgComprobante().isBlank());
        m.put("personas", personas);
        return m;
    }
}

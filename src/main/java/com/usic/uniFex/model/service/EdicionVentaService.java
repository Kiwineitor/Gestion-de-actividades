package com.usic.uniFex.model.service;

import java.util.Date;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IInscripcionDao;
import com.usic.uniFex.model.dao.IPersonasDao;
import com.usic.uniFex.model.dao.IResponsableDao;
import com.usic.uniFex.model.entity.Entidad;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Responsable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EdicionVentaService {

    private final IInscripcionDao inscripcionDao;
    private final IResponsableDao responsableDao;
    private final IPersonasDao personaDao;

    public record Resultado(boolean ok, String mensaje) {
        static Resultado error(String m) { return new Resultado(false, m); }
    }

    public record DatosEntidad(String entidadNombre, String nit, String descripcion,
                               String representanteLegal, String ciRepresentante,
                               String celularRepresentante) {}

    public record DatosResponsable(String nombre, String paterno, String materno,
                                   String ci, String celular) {}

    @Transactional
    public Resultado editarEntidad(Long inscripcionId, DatosEntidad d, Long usuarioId) {
        Inscripcion i = inscripcionDao.findById(inscripcionId).orElse(null);
        if (i == null || i.getEntidad() == null) return Resultado.error("La venta no existe");
        if (d == null) return Resultado.error("No llegaron datos");

        Entidad e = i.getEntidad();
        String nombre = mayus(d.entidadNombre());
        if (vacio(nombre) && vacio(e.getNombre())) {
            return Resultado.error("El nombre de la entidad es obligatorio");
        }
        if (!vacio(nombre)) e.setNombre(nombre);
        if (d.nit() != null) e.setNit(d.nit().trim());
        if (d.descripcion() != null) e.setDescripcion(mayus(d.descripcion()));
        if (d.representanteLegal() != null) e.setRepresentanteLegal(mayus(d.representanteLegal()));
        if (d.ciRepresentante() != null) e.setCiRepresentante(d.ciRepresentante().trim());
        if (d.celularRepresentante() != null) e.setCelularRepresentante(d.celularRepresentante().trim());

        i.setModificacion(new Date());
        i.setModificacionIdUsuario(usuarioId);
        inscripcionDao.save(i);
        log.info("Datos de la entidad de la inscripcion {} corregidos por el usuario {}",
                inscripcionId, usuarioId);
        return new Resultado(true, "Datos de la entidad actualizados");
    }

    @Transactional
    public Resultado editarResponsable(Long inscripcionId, Long responsableId,
                                       DatosResponsable d, Long usuarioId) {
        Inscripcion i = inscripcionDao.findById(inscripcionId).orElse(null);
        if (i == null || i.getEntidad() == null) return Resultado.error("La venta no existe");
        if (d == null) return Resultado.error("No llegaron datos");

        Responsable r = responsableDao.findById(responsableId).orElse(null);
        if (r == null || r.getEntidad() == null
                || !r.getEntidad().getId().equals(i.getEntidad().getId())) {
            return Resultado.error("Ese responsable no es de esta venta");
        }
        Persona p = r.getPersona();
        if (p == null) return Resultado.error("El responsable no tiene datos de persona");

        String nombre = mayus(d.nombre());
        if (vacio(nombre) && vacio(p.getNombre())) {
            return Resultado.error("El nombre del responsable es obligatorio");
        }
        if (!vacio(nombre)) p.setNombre(nombre);
        if (d.paterno() != null) p.setPaterno(mayus(d.paterno()));
        if (d.materno() != null) p.setMaterno(mayus(d.materno()));
        if (d.ci() != null && !d.ci().isBlank()) p.setCi(d.ci().trim());
        if (d.celular() != null) p.setCelular(d.celular().trim());

        personaDao.save(p);
        i.setModificacion(new Date());
        i.setModificacionIdUsuario(usuarioId);
        inscripcionDao.save(i);
        log.info("Responsable {} de la inscripcion {} corregido por el usuario {}",
                responsableId, inscripcionId, usuarioId);
        return new Resultado(true, "Datos del responsable actualizados");
    }

    private static String mayus(String s) {
        return s == null ? null : s.trim().toUpperCase();
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}

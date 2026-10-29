package com.usic.uniFex.model.service;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.dao.IInscripcionDao;
import com.usic.uniFex.model.dao.IPersonasDao;
import com.usic.uniFex.model.dao.IResponsableDao;
import com.usic.uniFex.model.dto.ResponsableFotoDTO;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Responsable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResponsableFotoService {

    private final IResponsableDao responsableDao;
    private final IPersonasDao personaDao;
    private final IInscripcionDao inscripcionDao;
    private final FileStorageService almacen;

    public record Resultado(boolean ok, String mensaje, ResponsableFotoDTO responsable) {
    }

    @Transactional(readOnly = true)
    public List<ResponsableFotoDTO> listar(Long inscripcionId) {
        Inscripcion ins = inscripcionDao.findById(inscripcionId).orElse(null);
        if (ins == null || ins.getEntidad() == null) return List.of();
        return responsableDao.findVigentesDeEntidad(ins.getEntidad().getId()).stream()
                .map(ResponsableFotoDTO::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean fotosCompletas(Long inscripcionId) {
        List<ResponsableFotoDTO> rs = listar(inscripcionId);
        return !rs.isEmpty() && rs.stream().allMatch(ResponsableFotoDTO::tieneFoto);
    }

    @Transactional
    public Resultado guardar(Long inscripcionId, Long responsableId, MultipartFile archivo, Long usuarioId) {
        if (archivo == null || archivo.isEmpty()) {
            return new Resultado(false, "No llego ninguna imagen", null);
        }
        Inscripcion ins = inscripcionDao.findById(inscripcionId).orElse(null);
        if (ins == null || ins.getEntidad() == null) {
            return new Resultado(false, "La venta no existe", null);
        }
        Responsable r = responsableDao.findById(responsableId).orElse(null);
        if (r == null || r.getEntidad() == null
                || !r.getEntidad().getId().equals(ins.getEntidad().getId())) {
            return new Resultado(false, "Ese responsable no es de esta venta", null);
        }
        Persona p = r.getPersona();
        if (p == null) {
            return new Resultado(false, "El responsable no tiene persona asociada", null);
        }

        String ruta;
        try {
            ruta = almacen.save(archivo, FileStorageService.Bucket.RESPONSABLES,
                    p.getNombre() + " " + p.getPaterno());
        } catch (IOException e) {
            log.warn("No se pudo guardar la foto del responsable {}", responsableId, e);
            return new Resultado(false, "No se pudo guardar la imagen: " + e.getMessage(), null);
        }

        p.setFoto(ruta);
        p.setModificacion(new Date());
        p.setModificacionIdUsuario(usuarioId);
        personaDao.save(p);

        log.info("Foto del responsable {} (persona {}) actualizada por el usuario {}",
                responsableId, p.getId(), usuarioId);
        return new Resultado(true, "Foto guardada", ResponsableFotoDTO.de(r));
    }

    @Transactional
    public Resultado quitar(Long inscripcionId, Long responsableId, Long usuarioId) {
        Inscripcion ins = inscripcionDao.findById(inscripcionId).orElse(null);
        Responsable r = responsableDao.findById(responsableId).orElse(null);
        if (ins == null || r == null || r.getEntidad() == null || ins.getEntidad() == null
                || !r.getEntidad().getId().equals(ins.getEntidad().getId())) {
            return new Resultado(false, "Ese responsable no es de esta venta", null);
        }
        Persona p = r.getPersona();
        if (p == null) return new Resultado(false, "El responsable no tiene persona asociada", null);

        p.setFoto(null);
        p.setModificacion(new Date());
        p.setModificacionIdUsuario(usuarioId);
        personaDao.save(p);
        return new Resultado(true, "Foto quitada", ResponsableFotoDTO.de(r));
    }
}

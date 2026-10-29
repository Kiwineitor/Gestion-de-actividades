package com.usic.uniFex.model.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.dao.IPuestoFotoDao;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.entity.PuestoFoto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PuestoFotoService {

    public static final int MAX_POR_CASETA = 6;

    private final IPuestoFotoDao fotoDao;
    private final IPuestoDao puestoDao;
    private final FileStorageService storage;

    public record Resultado(boolean ok, String mensaje, List<Long> puestosAfectados) {
        static Resultado error(String mensaje) {
            return new Resultado(false, mensaje, List.of());
        }
    }

    @Transactional(readOnly = true)
    public List<PuestoFoto> fotosDe(Long puestoId) {
        return fotoDao.activasDe(puestoId);
    }

    @Transactional(readOnly = true)
    public List<Long> idsConFoto() {
        return fotoDao.idsDePuestosConFoto();
    }

    @Transactional
    public Resultado subir(List<Long> puestoIds, MultipartFile archivo, String descripcion, Long usuarioId) {
        if (puestoIds == null || puestoIds.isEmpty()) return Resultado.error("No se indico ninguna caseta");
        if (archivo == null || archivo.isEmpty()) return Resultado.error("No se recibio ningun archivo");

        List<Puesto> casetas = puestoDao.findAllById(puestoIds);
        if (casetas.isEmpty()) return Resultado.error("Las casetas no existen");

        String ruta;
        try {
            Puesto primera = casetas.get(0);
            String slug = (primera.getCategoria() != null ? primera.getCategoria().getNombre() + " " : "")
                    + primera.getCodigo();
            ruta = storage.save(archivo, FileStorageService.Bucket.PUESTOS, slug);
        } catch (IOException e) {
            log.warn("No se pudo guardar la foto de caseta: {}", e.getMessage());
            return Resultado.error("No se pudo guardar el archivo: " + e.getMessage());
        }

        Date ahora = new Date();
        List<Long> afectados = new ArrayList<>();
        int llenas = 0;
        for (Puesto p : casetas) {
            long ya = fotoDao.contarActivasDe(p.getId());
            if (ya >= MAX_POR_CASETA) { llenas++; continue; }

            PuestoFoto f = new PuestoFoto();
            f.setPuesto(p);
            f.setRuta(ruta);
            f.setDescripcion(descripcion);
            f.setOrden((int) ya);
            f.setEstado(PuestoFoto.ACTIVA);
            f.setRegistro(ahora);
            f.setRegistroIdUsuario(usuarioId);
            f.setModificacion(ahora);
            f.setModificacionIdUsuario(usuarioId);
            fotoDao.save(f);
            afectados.add(p.getId());
        }

        String mensaje = "Foto agregada a " + afectados.size() + " caseta(s)"
                + (llenas > 0 ? "; " + llenas + " ya tenian el maximo de " + MAX_POR_CASETA : "");
        log.info("Fotos de caseta: {} (usuario={})", mensaje, usuarioId);
        return new Resultado(!afectados.isEmpty(), mensaje, afectados);
    }

    @Transactional
    public boolean borrar(Long fotoId) {
        return fotoDao.marcarBorrada(fotoId) > 0;
    }
}

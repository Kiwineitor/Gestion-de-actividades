package com.usic.uniFex.model.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.dao.IEdicionDao;
import com.usic.uniFex.model.dto.PlanoDTO;
import com.usic.uniFex.model.entity.Edicion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanoService {

    private final IEdicionDao edicionDao;
    private final FileStorageService almacen;
    private final JdbcTemplate jdbc;
    private final PuestoEventPublisher publicador;
    private final PlanoTextoService textos;
    private final AvisoPublicoService avisoPublico;

    public record Resultado(boolean ok, String mensaje, PlanoDTO plano) {
    }

    @Transactional(readOnly = true)
    public PlanoDTO activo() {
        return edicionDao.findFirstByActivaTrueOrderByAnioDesc()
                .map(PlanoDTO::de)
                .orElseGet(PlanoDTO::respaldo);
    }

    @Transactional
    public Resultado reemplazar(MultipartFile archivo, Long usuarioId) {
        if (archivo == null || archivo.isEmpty()) {
            return new Resultado(false, "No llego ninguna imagen", null);
        }
        Edicion edicion = edicionDao.findFirstByActivaTrueOrderByAnioDesc().orElse(null);
        if (edicion == null) {
            return new Resultado(false, "No hay una edicion activa a la que asignarle el plano", null);
        }

        int ancho;
        int alto;
        try (InputStream in = archivo.getInputStream()) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) {
                return new Resultado(false,
                        "No se pudo leer la imagen. Usa PNG o JPG (el formato WEBP no se puede medir aqui).", null);
            }
            ancho = img.getWidth();
            alto = img.getHeight();
        } catch (IOException e) {
            log.warn("No se pudo medir el plano subido", e);
            return new Resultado(false, "No se pudo leer la imagen: " + e.getMessage(), null);
        }

        String ruta;
        try {
            ruta = almacen.save(archivo, FileStorageService.Bucket.PLANOS, "plano-" + edicion.getAnio());
        } catch (IOException e) {
            log.warn("No se pudo guardar el plano", e);
            return new Resultado(false, "No se pudo guardar el archivo: " + e.getMessage(), null);
        }

        edicion.setPlanoArchivo(ruta);
        edicion.setPlanoAncho(ancho);
        edicion.setPlanoAlto(alto);
        edicion.setPlanoVersion((edicion.getPlanoVersion() == null ? 0 : edicion.getPlanoVersion()) + 1);
        edicion.setPlanoSubidoEn(LocalDateTime.now());
        edicion.setPlanoSinFondo(false);
        edicionDao.save(edicion);
        avisoPublico.cambio("feria", edicion.getId());

        log.info("Plano de la edicion {} reemplazado por el usuario {}: {} ({}x{}), version {}",
                edicion.getId(), usuarioId, ruta, ancho, alto, edicion.getPlanoVersion());
        return new Resultado(true, "Plano actualizado (" + ancho + "x" + alto + ")", PlanoDTO.de(edicion));
    }

    @Transactional
    public Resultado cambiarForma(Integer ancho, Integer alto, boolean conservar, Long usuarioId) {
        Edicion edicion = edicionDao.findFirstByActivaTrueOrderByAnioDesc().orElse(null);
        if (edicion == null) return new Resultado(false, "No hay una edicion activa", null);
        if (edicion.getPlanoArchivo() != null && !Boolean.TRUE.equals(edicion.getPlanoSinFondo())) {
            return new Resultado(false, "La forma la da la imagen de fondo. Quita el fondo para elegir otra forma.", null);
        }
        if (ancho == null || alto == null || ancho < 100 || alto < 100 || ancho > 10000 || alto > 10000
                || alto > ancho * 5 || ancho > alto * 5) {
            return new Resultado(false, "Esa forma no es válida", null);
        }
        PlanoDTO actual = PlanoDTO.de(edicion);
        double a1 = (double) actual.alto() / actual.ancho();
        double a2 = (double) alto / ancho;
        if (conservar) {
            double s = Math.min(1, a2 / a1);
            double k = s * a1 / a2;
            List<Long> ids = jdbc.queryForList("""
                    UPDATE puesto p
                       SET mapa_x = 0.5 + (p.mapa_x - 0.5) * ?, mapa_y = 0.5 + (p.mapa_y - 0.5) * ?,
                           mapa_escala = COALESCE(p.mapa_escala, 1) * ?, version = p.version + 1
                      FROM categoria c
                     WHERE c.id = p.id_categoria AND c.id_edicion = ? AND p.mapa_x IS NOT NULL AND p.mapa_y IS NOT NULL
                    RETURNING p.id
                    """, Long.class, s, k, s, edicion.getId());
            jdbc.update("""
                    UPDATE plano_texto
                       SET mapa_x = 0.5 + (mapa_x - 0.5) * ?, mapa_y = 0.5 + (mapa_y - 0.5) * ?, tamano = tamano * ?,
                           ancho = ancho * ?, alto = alto * ?
                     WHERE id_edicion = ? AND ("_estado" IS NULL OR "_estado" <> 'X')
                    """, s, k, s, s, k, edicion.getId());
            publicador.publicarVarios(ids);
        }
        edicion.setPlanoArchivo(null);
        edicion.setPlanoSinFondo(true);
        edicion.setPlanoAncho(ancho);
        edicion.setPlanoAlto(alto);
        edicion.setPlanoVersion((edicion.getPlanoVersion() == null ? 0 : edicion.getPlanoVersion()) + 1);
        edicionDao.save(edicion);
        avisoPublico.cambio("feria", edicion.getId());
        if (conservar) textos.difundirTodos();
        log.info("Forma del plano de la edicion {} cambiada a {}x{} por el usuario {}", edicion.getId(), ancho, alto, usuarioId);
        return new Resultado(true, "Forma del plano actualizada", PlanoDTO.de(edicion));
    }

    @Transactional
    public Resultado quitarFondo(Long usuarioId) {
        Edicion edicion = edicionDao.findFirstByActivaTrueOrderByAnioDesc().orElse(null);
        if (edicion == null) {
            return new Resultado(false, "No hay una edicion activa", null);
        }
        if (edicion.getPlanoAncho() == null || edicion.getPlanoAlto() == null) {
            edicion.setPlanoAncho(PlanoDTO.LIENZO_ANCHO);
            edicion.setPlanoAlto(PlanoDTO.LIENZO_ALTO);
        }
        edicion.setPlanoArchivo(null);
        edicion.setPlanoSinFondo(true);
        edicion.setPlanoVersion((edicion.getPlanoVersion() == null ? 0 : edicion.getPlanoVersion()) + 1);
        edicionDao.save(edicion);
        avisoPublico.cambio("feria", edicion.getId());
        log.info("Fondo del plano de la edicion {} quitado por el usuario {}", edicion.getId(), usuarioId);
        return new Resultado(true, "Fondo quitado. Los puestos y textos siguen en su lugar.", PlanoDTO.de(edicion));
    }
}

package com.usic.uniFex.model.service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SistemaService {

    private static final Set<String> TIPOS_IMAGEN = Set.of("image/png", "image/jpeg", "image/webp");
    private static final String NOMBRE_POR_DEFECTO = "Gestión de Actividades";

    private final JdbcTemplate jdbc;
    private final FileStorageService archivos;
    private final AvisoPublicoService avisoPublico;

    public record Datos(String nombre, String lema, String contacto) {
    }

    public record Resultado(boolean ok, String mensaje, Map<String, Object> marca) {
        static Resultado error(String m) { return new Resultado(false, m, null); }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> marca() {
        Map<String, String> v = new LinkedHashMap<>();
        jdbc.query("SELECT clave, valor FROM parametro_sistema", rs -> {
            v.put(rs.getString("clave"), rs.getString("valor"));
        });
        Map<String, Object> m = new LinkedHashMap<>();
        String nombre = v.get("nombre");
        m.put("nombre", nombre == null || nombre.isBlank() ? NOMBRE_POR_DEFECTO : nombre);
        m.put("lema", v.get("lema"));
        m.put("contacto", v.get("contacto"));
        String logo = v.get("logo_archivo");
        m.put("logoUrl", logo == null ? null : "/files/" + logo);
        return m;
    }

    @Transactional
    public Resultado guardar(Datos d, Long usuarioId) {
        String nombre = limpio(d.nombre(), 80);
        if (nombre == null) return Resultado.error("El sistema necesita un nombre");
        escribir("nombre", nombre, usuarioId);
        escribir("lema", limpio(d.lema(), 160), usuarioId);
        escribir("contacto", limpio(d.contacto(), 120), usuarioId);
        avisoPublico.cambio("sistema", null);
        return new Resultado(true, "Datos del sistema guardados", marca());
    }

    @Transactional
    public Resultado subirLogo(MultipartFile archivo, Long usuarioId) throws IOException {
        if (archivo == null || archivo.isEmpty() || !TIPOS_IMAGEN.contains(String.valueOf(archivo.getContentType()))) {
            return Resultado.error("Sube una imagen PNG, JPG o WEBP");
        }
        if (archivo.getSize() > 2L * 1024 * 1024) return Resultado.error("El logo no puede pasar de 2 MB");
        String ruta = archivos.save(archivo, FileStorageService.Bucket.SISTEMA, "logo");
        escribir("logo_archivo", ruta, usuarioId);
        avisoPublico.cambio("sistema", null);
        return new Resultado(true, "Logo actualizado", marca());
    }

    @Transactional
    public Resultado quitarLogo(Long usuarioId) {
        escribir("logo_archivo", null, usuarioId);
        avisoPublico.cambio("sistema", null);
        return new Resultado(true, "Logo quitado", marca());
    }

    private void escribir(String clave, String valor, Long usuarioId) {
        jdbc.update("""
                INSERT INTO parametro_sistema (clave, valor, actualizado_en, actualizado_por)
                VALUES (?, ?, now(), ?)
                ON CONFLICT (clave) DO UPDATE
                   SET valor = EXCLUDED.valor, actualizado_en = now(), actualizado_por = EXCLUDED.actualizado_por
                """, clave, valor, usuarioId);
    }

    private static String limpio(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.length() > max ? t.substring(0, max) : t;
    }
}

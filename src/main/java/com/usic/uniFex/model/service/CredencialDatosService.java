package com.usic.uniFex.model.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.dao.IPersonasDao;
import com.usic.uniFex.model.dao.IResponsableDao;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Responsable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CredencialDatosService {

    private final IResponsableDao responsableDao;
    private final IPersonasDao personaDao;
    private final FileStorageService almacen;
    private final JdbcTemplate jdbc;

    public record Cambio(String nombre, String paterno, String materno, String ci, String cargo) {
    }

    public record Resultado(boolean ok, String mensaje, Map<String, Object> datos) {
    }

    @Transactional(readOnly = true)
    public Map<String, Object> datos(Long responsableId) {
        Responsable r = responsableDao.findById(responsableId).orElse(null);
        return r == null || r.getPersona() == null ? null : mapa(r);
    }

    @Transactional
    public Resultado actualizar(Long responsableId, Cambio c, Long usuarioId) {
        Responsable r = responsableDao.findById(responsableId).orElse(null);
        if (r == null || r.getPersona() == null) return new Resultado(false, "El responsable no existe", null);
        Persona p = r.getPersona();
        String nombre = limpio(c.nombre(), 60);
        if (nombre == null) return new Resultado(false, "El nombre no puede quedar vacío", null);
        String ci = limpio(c.ci(), 20);
        if (ci != null && !ci.matches("[0-9A-Za-z\\-. ]+")) {
            return new Resultado(false, "El C.I. solo admite números, letras y guiones", null);
        }

        List<Object[]> cambios = new ArrayList<>();
        anotar(cambios, "nombre", p.getNombre(), nombre);
        anotar(cambios, "paterno", p.getPaterno(), limpio(c.paterno(), 60));
        anotar(cambios, "materno", p.getMaterno(), limpio(c.materno(), 60));
        anotar(cambios, "ci", p.getCi(), ci);
        anotar(cambios, "cargo", r.getCargo(), limpio(c.cargo(), 60));
        if (cambios.isEmpty()) return new Resultado(true, "Sin cambios", mapa(r));

        p.setNombre(nombre);
        p.setPaterno(limpio(c.paterno(), 60));
        p.setMaterno(limpio(c.materno(), 60));
        p.setCi(ci);
        p.setModificacion(new Date());
        p.setModificacionIdUsuario(usuarioId);
        personaDao.save(p);
        r.setCargo(limpio(c.cargo(), 60));
        responsableDao.save(r);

        for (Object[] cambio : cambios) registrar(responsableId, p.getId(), (String) cambio[0],
                (String) cambio[1], (String) cambio[2], usuarioId);
        log.info("Datos de credencial del responsable {} corregidos por el usuario {}: {} campo(s)",
                responsableId, usuarioId, cambios.size());
        return new Resultado(true, "Datos actualizados", mapa(r));
    }

    @Transactional
    public Resultado cambiarFoto(Long responsableId, MultipartFile archivo, Long usuarioId) {
        if (archivo == null || archivo.isEmpty()) return new Resultado(false, "No llegó ninguna imagen", null);
        String tipo = archivo.getContentType() == null ? "" : archivo.getContentType();
        if (!tipo.startsWith("image/")) return new Resultado(false, "La foto debe ser una imagen", null);
        Responsable r = responsableDao.findById(responsableId).orElse(null);
        if (r == null || r.getPersona() == null) return new Resultado(false, "El responsable no existe", null);
        Persona p = r.getPersona();
        String ruta;
        try {
            ruta = almacen.save(archivo, FileStorageService.Bucket.RESPONSABLES, p.getNombre() + " " + p.getPaterno());
        } catch (IOException e) {
            return new Resultado(false, "No se pudo guardar la imagen: " + e.getMessage(), null);
        }
        String anterior = p.getFoto();
        p.setFoto(ruta);
        p.setModificacion(new Date());
        p.setModificacionIdUsuario(usuarioId);
        personaDao.save(p);
        registrar(responsableId, p.getId(), "foto", anterior, ruta, usuarioId);
        return new Resultado(true, "Foto actualizada", mapa(r));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> historial(Long responsableId) {
        return jdbc.query("""
                SELECT cc.campo, cc.valor_anterior, cc.valor_nuevo, cc."_fecha_registro",
                       COALESCE(u.username, '—') AS usuario
                  FROM credencial_cambio cc
                  LEFT JOIN usuario u ON u.id = cc."_registro_id_usuario"
                 WHERE cc.id_responsable = ?
                 ORDER BY cc."_fecha_registro" DESC, cc.id DESC
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("campo", rs.getString("campo"));
            m.put("anterior", rs.getString("valor_anterior"));
            m.put("nuevo", rs.getString("valor_nuevo"));
            m.put("fecha", String.valueOf(rs.getTimestamp("_fecha_registro").toLocalDateTime()));
            m.put("usuario", rs.getString("usuario"));
            return m;
        }, responsableId);
    }

    private void registrar(Long responsableId, Long personaId, String campo, String anterior,
                           String nuevo, Long usuarioId) {
        jdbc.update("""
                INSERT INTO credencial_cambio
                       (id_responsable, id_persona, campo, valor_anterior, valor_nuevo, "_registro_id_usuario")
                VALUES (?, ?, ?, ?, ?, ?)
                """, responsableId, personaId, campo, recortar(anterior), recortar(nuevo), usuarioId);
    }

    private static void anotar(List<Object[]> cambios, String campo, String antes, String despues) {
        String a = antes == null || antes.isBlank() ? null : antes.trim();
        if (!Objects.equals(a, despues)) cambios.add(new Object[] { campo, a, despues });
    }

    private static Map<String, Object> mapa(Responsable r) {
        Persona p = r.getPersona();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("responsableId", r.getId());
        m.put("nombre", p.getNombre());
        m.put("paterno", p.getPaterno());
        m.put("materno", p.getMaterno());
        m.put("ci", p.getCi());
        m.put("cargo", r.getCargo());
        m.put("fotoUrl", p.getFoto() == null || p.getFoto().isBlank() ? null : "/files/" + p.getFoto());
        return m;
    }

    private static String limpio(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static String recortar(String s) {
        return s == null ? null : s.length() > 300 ? s.substring(0, 300) : s;
    }
}

package com.usic.uniFex.model.service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PuntoVentaService {

    public static final Set<String> MEDIOS = Set.of("EFECTIVO", "QR", "TRANSFERENCIA");
    private static final Set<String> TIPOS_COMPROBANTE = Set.of("image/png", "image/jpeg", "image/webp", "application/pdf");

    private final JdbcTemplate jdbc;
    private final RegistroVentaService registro;
    private final FileStorageService archivos;
    private final EntregaVentaService entregas;

    public record Persona(String nombre, String ci, String celular) {
    }

    public record VentaEspacios(String nombre, String ci, String celular, String negocio, String nit, String rubro,
                                Long tipoEntidadId, String medio, List<Persona> acompanantes, List<Long> puestos) {
    }

    public record Resultado(boolean ok, String mensaje, Map<String, Object> datos) {
        static Resultado error(String m) { return new Resultado(false, m, null); }
    }

    @Transactional
    public Resultado venderEspacios(VentaEspacios v, MultipartFile comprobante, Long usuarioId) throws IOException {
        if (v == null || v.puestos() == null || v.puestos().isEmpty()) return Resultado.error("Elige al menos un espacio en el plano.");
        String nombre = limpio(v.nombre());
        if (nombre == null || nombre.length() < 3) return Resultado.error("Escribe el nombre completo del comprador.");
        String ci = limpio(v.ci());
        if (ci == null || ci.length() < 4) return Resultado.error("Escribe el C.I. del comprador.");
        String celular = v.celular() == null ? "" : v.celular().replaceAll("[^0-9+]", "");
        if (!celular.matches("\\+?\\d{7,15}")) return Resultado.error("Escribe un celular válido.");
        String medio = v.medio() == null ? "" : v.medio().trim().toUpperCase(Locale.ROOT);
        if (!MEDIOS.contains(medio)) return Resultado.error("Elige cómo pagó: efectivo, QR o transferencia.");
        boolean conArchivo = comprobante != null && !comprobante.isEmpty();
        if (conArchivo && !TIPOS_COMPROBANTE.contains(String.valueOf(comprobante.getContentType()))) {
            return Resultado.error("El comprobante debe ser una foto o un PDF.");
        }
        if (conArchivo && comprobante.getSize() > 8L * 1024 * 1024) return Resultado.error("El comprobante no puede pasar de 8 MB.");

        List<Map<String, Object>> ediciones = jdbc.queryForList("""
                SELECT DISTINCT e.id, e.comprador, e.finalizada, (e.activa OR e.publicacion_independiente) AS en_venta
                  FROM puesto p JOIN categoria c ON c.id = p.id_categoria JOIN edicion e ON e.id = c.id_edicion
                 WHERE p.id = ANY (?)
                """, (Object) v.puestos().toArray(new Long[0]));
        if (ediciones.size() != 1) return Resultado.error("Una venta solo puede tener espacios de una misma actividad.");
        Map<String, Object> e = ediciones.get(0);
        if (Boolean.TRUE.equals(e.get("finalizada")) || !Boolean.TRUE.equals(e.get("en_venta"))) {
            return Resultado.error("Esta actividad no está a la venta.");
        }
        boolean persona = "PERSONA".equals(e.get("comprador"));
        String negocio = persona ? nombre : limpio(v.negocio());
        if (negocio == null) return Resultado.error("Escribe el nombre del negocio.");

        Long tipo = v.tipoEntidadId();
        if (tipo == null) {
            List<Long> tipos = jdbc.queryForList(persona
                    ? "SELECT id FROM tipo_entidad WHERE upper(nombre) = 'PERSONA PARTICULAR' LIMIT 1"
                    : "SELECT id FROM tipo_entidad WHERE upper(nombre) LIKE '%EMPRENDEDOR%' OR upper(nombre) LIKE '%EMPRESA%' ORDER BY id LIMIT 1",
                    Long.class);
            if (tipos.isEmpty()) tipos = jdbc.queryForList("SELECT id FROM tipo_entidad ORDER BY id LIMIT 1", Long.class);
            tipo = tipos.isEmpty() ? null : tipos.get(0);
        }

        List<RegistroVentaService.DatosPersona> personas = new ArrayList<>();
        personas.add(new RegistroVentaService.DatosPersona(mayusculas(nombre), "", "", ci, null, celular));
        if (v.acompanantes() != null) {
            for (Persona p : v.acompanantes()) {
                String n = p == null ? null : limpio(p.nombre());
                if (n == null) continue;
                String c = limpio(p.ci());
                personas.add(new RegistroVentaService.DatosPersona(mayusculas(n), "", "", c == null ? "S/N" : c, null,
                        p.celular() == null || p.celular().isBlank() ? null : p.celular().trim()));
            }
        }

        RegistroVentaService.NuevaVenta venta = new RegistroVentaService.NuevaVenta(
                mayusculas(negocio), limpio(v.nit()), persona ? "PARTICULAR" : mayusculas(rubro(v.rubro())),
                persona ? "PARTICULAR" : mayusculas(rubro(v.rubro())), mayusculas(nombre), ci, celular, tipo,
                LocalDate.now(), LocalDate.now(), personas, null, null, "EFECTIVO".equals(medio), v.puestos());
        RegistroVentaService.Resultado r = registro.registrar(venta, usuarioId, AuditoriaService.ORIGEN_WEB);
        if (!r.ok()) return Resultado.error(r.mensaje());

        if (conArchivo) {
            String ruta = archivos.save(comprobante, FileStorageService.Bucket.COMPROBANTES, "punto-venta");
            jdbc.update("UPDATE inscripcion SET img_comprobante = ? WHERE id = ?", ruta, r.inscripcionId());
        }
        HistorialService.anotarResumen(mayusculas(negocio) + " · " + v.puestos().size() + " espacio(s) · Bs " + r.total() + " · " + medio);
        if ("EFECTIVO".equals(medio)) jdbc.update("UPDATE inscripcion SET cobro_efectivo = TRUE WHERE id = ?", r.inscripcionId());
        log.info("Venta {} en punto de venta por el usuario {} ({}, {} espacio(s))", r.inscripcionId(), usuarioId, medio, v.puestos().size());

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("inscripcionId", r.inscripcionId());
        datos.put("total", r.total());
        datos.put("medio", medio);
        Map<String, Object> entrega = entregas.deInscripcion(r.inscripcionId());
        if (entrega != null) {
            entrega = new LinkedHashMap<>(entrega);
            entrega.put("cobroEfectivo", "EFECTIVO".equals(medio));
            if (conArchivo) entrega.put("conComprobante", true);
        }
        datos.put("entrega", entrega);
        return new Resultado(true, "Venta registrada", datos);
    }

    private static String rubro(String r) {
        String t = limpio(r);
        return t == null ? "GENERAL" : t;
    }

    private static String limpio(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim().replaceAll("\\s+", " ");
    }

    private static String mayusculas(String s) {
        return s == null ? null : s.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}

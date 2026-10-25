package com.usic.uniFex.model.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usic.uniFex.model.dto.PlantillaCredencial;
import com.usic.uniFex.model.dto.PlantillaCredencial.Caja;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlantillaCredencialService {

    public static final List<String> CAMPOS =
            List.of("qr", "foto", "nombre", "empresa", "ci", "codigo", "zona", "tipo");

    private static final String COLUMNAS = """
            id, codigo, etiqueta, detalle, imagen, requiere_foto, centrado, mayusculas,
            foto_circular, multilinea, color_texto, tipo_texto, cajas, orden, predeterminada,
            uso, id_edicion, id_dependencia, imagen_reverso
            """;

    public static final String USO_EXPOSITOR = "EXPOSITOR";
    public static final String USO_APOYO = "APOYO";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ImagenPlantillaCredencial imagenes;
    private final FileStorageService archivos;

    public record Datos(String codigo, String etiqueta, String detalle, String imagen,
                        Boolean requiereFoto, Boolean centrado, Boolean mayusculas,
                        Boolean fotoCircular, Boolean multilinea, String colorTexto,
                        String tipoTexto, Map<String, Caja> cajas, Integer orden,
                        Boolean predeterminada, String uso, Long idEdicion, Long idDependencia,
                        String imagenReverso) {
    }

    public record Fila(Long id, PlantillaCredencial plantilla, int orden, boolean predeterminada,
                       String uso, Long idEdicion, Long idDependencia) {
        public boolean deApoyo() {
            return USO_APOYO.equals(uso);
        }
    }

    private volatile List<Fila> vigentes = List.of();

    public static class PlantillaInvalida extends RuntimeException {
        public PlantillaInvalida(String mensaje) {
            super(mensaje);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void iniciar() {
        try {
            Integer n = jdbc.queryForObject("SELECT count(*) FROM plantilla_credencial", Integer.class);
            if (n != null && n == 0) sembrar();
            recargar();
        } catch (RuntimeException e) {
            log.warn("Plantillas de credencial desde codigo: {}", e.getMessage());
        }
    }

    private void sembrar() {
        int orden = 0;
        for (PlantillaCredencial p : PlantillaCredencial.CATALOGO) {
            insertar(p, orden++, p.id().equals(PlantillaCredencial.POR_DEFECTO.id()), USO_EXPOSITOR, null, null, null);
        }
        log.info("Plantillas de credencial iniciales cargadas: {}", PlantillaCredencial.CATALOGO.size());
    }

    public synchronized void recargar() {
        Long edicion = edicionActiva();
        List<Fila> filas = listar().stream()
                .filter(f -> f.idEdicion() == null || f.idEdicion().equals(edicion))
                .toList();
        vigentes = filas;
        List<Fila> expositores = filas.stream().filter(f -> !f.deApoyo()).toList();
        String porDefecto = expositores.stream().filter(Fila::predeterminada)
                .map(f -> f.plantilla().id()).findFirst().orElse(null);
        PlantillaCredencial.registrar(expositores.stream().map(Fila::plantilla).toList(), porDefecto);
    }

    @Scheduled(initialDelay = 300_000, fixedDelay = 300_000)
    public void refrescar() {
        try {
            recargar();
        } catch (RuntimeException e) {
            log.debug("Plantillas de credencial sin refrescar: {}", e.getMessage());
        }
    }

    public PlantillaCredencial paraApoyo(Long idDependencia) {
        List<Fila> apoyo = vigentes.stream().filter(Fila::deApoyo).toList();
        return apoyo.stream()
                .filter(f -> idDependencia != null && idDependencia.equals(f.idDependencia()))
                .findFirst()
                .or(() -> apoyo.stream().filter(f -> f.idDependencia() == null).findFirst())
                .map(Fila::plantilla)
                .orElse(null);
    }

    private Long edicionActiva() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM edicion WHERE activa ORDER BY id DESC LIMIT 1", Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public List<Fila> listar() {
        return jdbc.query("SELECT " + COLUMNAS + " FROM plantilla_credencial WHERE \"_estado\" = 'A' "
                + "ORDER BY orden, id", (rs, i) -> {
            Map<String, Caja> c = leerCajas(rs.getString("cajas"));
            return new Fila(
                rs.getLong("id"),
                new PlantillaCredencial(
                        rs.getString("codigo"), rs.getString("etiqueta"), rs.getString("detalle"),
                        rs.getString("imagen"), rs.getBoolean("requiere_foto"), rs.getBoolean("centrado"),
                        rs.getBoolean("mayusculas"),
                        c.get("qr"), c.get("nombre"), c.get("empresa"), c.get("ci"),
                        c.get("codigo"), c.get("zona"), c.get("foto"), c.get("tipo"),
                        rs.getString("tipo_texto"), rs.getString("color_texto"),
                        rs.getBoolean("foto_circular"), rs.getBoolean("multilinea"),
                        rs.getString("imagen_reverso")),
                rs.getInt("orden"),
                rs.getBoolean("predeterminada"),
                rs.getString("uso"),
                rs.getObject("id_edicion", Long.class),
                rs.getObject("id_dependencia", Long.class));
        });
    }

    public Fila buscar(Long id) {
        return listar().stream().filter(f -> f.id().equals(id)).findFirst().orElse(null);
    }

    @Transactional
    public Fila crear(Datos d, Long usuarioId) {
        PlantillaCredencial p = aPlantilla(d, null);
        Integer existe = jdbc.queryForObject(
                "SELECT count(*) FROM plantilla_credencial WHERE codigo = ?", Integer.class, p.id());
        if (existe != null && existe > 0) throw new PlantillaInvalida("Ya existe una plantilla con el código " + p.id());
        String uso = usoValido(d.uso());
        boolean predeterminada = Boolean.TRUE.equals(d.predeterminada()) && USO_EXPOSITOR.equals(uso);
        if (predeterminada) quitarPredeterminada();
        Long id = insertar(p, d.orden() == null ? siguienteOrden() : d.orden(), predeterminada, uso,
                predeterminada ? null : d.idEdicion(), USO_APOYO.equals(uso) ? d.idDependencia() : null, usuarioId);
        recargar();
        return buscar(id);
    }

    @Transactional
    public Fila actualizar(Long id, Datos d, Long usuarioId) {
        Fila actual = buscar(id);
        if (actual == null) return null;
        PlantillaCredencial p = aPlantilla(d, actual.plantilla());
        String uso = d.uso() == null ? actual.uso() : usoValido(d.uso());
        boolean predeterminada = d.predeterminada() == null ? actual.predeterminada() : d.predeterminada();
        if (predeterminada && USO_APOYO.equals(uso)) {
            throw new PlantillaInvalida("Una plantilla de personal de apoyo no puede ser la predeterminada");
        }
        if (predeterminada && d.idEdicion() != null) {
            throw new PlantillaInvalida("La plantilla predeterminada debe estar disponible en todas las actividades");
        }
        if (actual.predeterminada() && !predeterminada) {
            throw new PlantillaInvalida("Elige otra plantilla como predeterminada antes de quitarle la marca a esta");
        }
        if (predeterminada && !actual.predeterminada()) quitarPredeterminada();
        jdbc.update("""
                UPDATE plantilla_credencial
                   SET etiqueta = ?, detalle = ?, imagen = ?, requiere_foto = ?, centrado = ?,
                       mayusculas = ?, foto_circular = ?, multilinea = ?, color_texto = ?,
                       tipo_texto = ?, cajas = ?, orden = ?, predeterminada = ?, uso = ?,
                       id_edicion = ?, id_dependencia = ?, imagen_reverso = ?,
                       "_fecha_modificacion" = now(), "_modificacion_id_usuario" = ?
                 WHERE id = ? AND "_estado" = 'A'
                """,
                p.etiqueta(), p.detalle(), p.imagen(), p.requiereFoto(), p.centrado(), p.mayusculas(),
                p.fotoCircular(), p.multilinea(), p.colorTexto(), p.tipoTexto(), cajasJson(p),
                d.orden() == null ? actual.orden() : d.orden(), predeterminada, uso,
                d.idEdicion(), USO_APOYO.equals(uso) ? d.idDependencia() : null, p.imagenReverso(),
                usuarioId, id);
        recargar();
        return buscar(id);
    }

    @Transactional
    public Fila duplicar(Long id, Long usuarioId) {
        Fila origen = buscar(id);
        if (origen == null) return null;
        PlantillaCredencial o = origen.plantilla();
        String codigo = codigoLibre(o.id());
        PlantillaCredencial copia = new PlantillaCredencial(codigo, recortar(o.etiqueta() + " (copia)", 80),
                o.detalle(), o.imagen(), o.requiereFoto(), o.centrado(), o.mayusculas(), o.qr(), o.nombre(),
                o.empresa(), o.ci(), o.codigo(), o.zona(), o.foto(), o.tipo(), o.tipoTexto(), o.colorTexto(),
                o.fotoCircular(), o.multilinea(), o.imagenReverso());
        Long nuevo = insertar(copia, siguienteOrden(), false, origen.uso(), origen.idEdicion(),
                origen.idDependencia(), usuarioId);
        recargar();
        return buscar(nuevo);
    }

    @Transactional
    public boolean eliminar(Long id, Long usuarioId) {
        Fila f = buscar(id);
        if (f == null) return false;
        if (f.predeterminada()) throw new PlantillaInvalida("No se puede quitar la plantilla predeterminada");
        if (!f.deApoyo() && listar().stream().filter(x -> !x.deApoyo()).count() <= 1) {
            throw new PlantillaInvalida("Debe quedar al menos una plantilla de expositores");
        }
        jdbc.update("UPDATE plantilla_credencial SET \"_estado\" = 'X', \"_fecha_modificacion\" = now(), "
                + "\"_modificacion_id_usuario\" = ? WHERE id = ?", usuarioId, id);
        recargar();
        return true;
    }

    public String subirFondo(MultipartFile archivo) throws IOException {
        if (archivo == null || archivo.isEmpty()) throw new PlantillaInvalida("No llegó ninguna imagen");
        String tipo = archivo.getContentType() == null ? "" : archivo.getContentType();
        if (!tipo.equals("image/png") && !tipo.equals("image/jpeg")) {
            throw new PlantillaInvalida("El fondo debe ser una imagen PNG o JPG");
        }
        if (ImageIO.read(new ByteArrayInputStream(archivo.getBytes())) == null) {
            throw new PlantillaInvalida("La imagen no se puede leer");
        }
        return ImagenPlantillaCredencial.PREFIJO_SUBIDA
                + archivos.save(archivo, FileStorageService.Bucket.CREDENCIALES, "plantilla");
    }

    public PlantillaCredencial aPlantilla(Datos d, PlantillaCredencial base) {
        String codigo = base != null ? base.id() : normalizarCodigo(d.codigo());
        String etiqueta = recortar(d.etiqueta(), 80);
        if (etiqueta == null) throw new PlantillaInvalida("La plantilla necesita un nombre");
        String imagen = d.imagen() != null && !d.imagen().isBlank() ? d.imagen().trim()
                : base != null ? base.imagen() : null;
        if (imagen == null) throw new PlantillaInvalida("La plantilla necesita una imagen de fondo");
        if (!imagen.startsWith("static/") && !imagen.startsWith(ImagenPlantillaCredencial.PREFIJO_SUBIDA)) {
            throw new PlantillaInvalida("Imagen de fondo no válida");
        }
        if (!imagenes.existe(imagen)) throw new PlantillaInvalida("No se encuentra la imagen de fondo");
        String reverso = d.imagenReverso() == null || d.imagenReverso().isBlank() ? null : d.imagenReverso().trim();
        boolean reversoValido = reverso == null
                || (reverso.startsWith("static/") || reverso.startsWith(ImagenPlantillaCredencial.PREFIJO_SUBIDA))
                && imagenes.existe(reverso);
        if (!reversoValido) throw new PlantillaInvalida("No se encuentra la imagen del reverso");
        Map<String, Caja> cajas = d.cajas() == null ? Map.of() : d.cajas();
        for (String clave : cajas.keySet()) {
            if (!CAMPOS.contains(clave)) throw new PlantillaInvalida("Campo desconocido: " + clave);
        }
        Caja qr = validar(cajas.get("qr"), "QR");
        if (qr == null) throw new PlantillaInvalida("La plantilla debe tener la caja del QR");
        String color = d.colorTexto() == null || d.colorTexto().isBlank() ? null : d.colorTexto().trim();
        if (color != null && !color.matches("#[0-9a-fA-F]{6}")) throw new PlantillaInvalida("Color de texto no válido");
        return new PlantillaCredencial(codigo, etiqueta, recortar(d.detalle(), 200), imagen,
                Boolean.TRUE.equals(d.requiereFoto()), Boolean.TRUE.equals(d.centrado()),
                d.mayusculas() == null || d.mayusculas(),
                qr, validar(cajas.get("nombre"), "nombre"), validar(cajas.get("empresa"), "empresa"),
                validar(cajas.get("ci"), "C.I."), validar(cajas.get("codigo"), "puesto"),
                validar(cajas.get("zona"), "zona"), validar(cajas.get("foto"), "foto"),
                validar(cajas.get("tipo"), "cargo"), recortar(d.tipoTexto(), 40), color,
                Boolean.TRUE.equals(d.fotoCircular()), Boolean.TRUE.equals(d.multilinea()), reverso);
    }

    private static String usoValido(String uso) {
        String u = uso == null || uso.isBlank() ? USO_EXPOSITOR : uso.trim().toUpperCase();
        if (!USO_EXPOSITOR.equals(u) && !USO_APOYO.equals(u)) throw new PlantillaInvalida("Uso de plantilla no válido");
        return u;
    }

    public Map<String, Object> aMapa(Fila f) {
        PlantillaCredencial p = f.plantilla();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.id());
        m.put("codigo", p.id());
        m.put("etiqueta", p.etiqueta());
        m.put("detalle", p.detalle());
        m.put("imagen", p.imagen());
        m.put("requiereFoto", p.requiereFoto());
        m.put("centrado", p.centrado());
        m.put("mayusculas", p.mayusculas());
        m.put("fotoCircular", p.fotoCircular());
        m.put("multilinea", p.multilinea());
        m.put("colorTexto", p.colorTexto());
        m.put("tipoTexto", p.tipoTexto());
        m.put("cajas", cajas(p));
        m.put("orden", f.orden());
        m.put("predeterminada", f.predeterminada());
        m.put("uso", f.uso());
        m.put("idEdicion", f.idEdicion());
        m.put("idDependencia", f.idDependencia());
        m.put("imagenReverso", p.imagenReverso());
        return m;
    }

    private Caja validar(Caja c, String nombre) {
        if (c == null) return null;
        boolean dentro = c.x() >= 0 && c.y() >= 0 && c.ancho() > 0.005 && c.alto() > 0.005
                && c.x() + c.ancho() <= 1.0001 && c.y() + c.alto() <= 1.0001;
        if (!dentro) throw new PlantillaInvalida("La caja de " + nombre + " queda fuera de la imagen");
        return c;
    }

    private Long insertar(PlantillaCredencial p, int orden, boolean predeterminada, String uso,
                          Long idEdicion, Long idDependencia, Long usuarioId) {
        return jdbc.queryForObject("""
                INSERT INTO plantilla_credencial
                       (codigo, etiqueta, detalle, imagen, requiere_foto, centrado, mayusculas,
                        foto_circular, multilinea, color_texto, tipo_texto, cajas, orden,
                        predeterminada, uso, id_edicion, id_dependencia, imagen_reverso,
                        "_registro_id_usuario")
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                p.id(), p.etiqueta(), p.detalle(), p.imagen(), p.requiereFoto(), p.centrado(),
                p.mayusculas(), p.fotoCircular(), p.multilinea(), p.colorTexto(), p.tipoTexto(),
                cajasJson(p), orden, predeterminada, uso, idEdicion, idDependencia, p.imagenReverso(),
                usuarioId);
    }

    private void quitarPredeterminada() {
        jdbc.update("UPDATE plantilla_credencial SET predeterminada = FALSE WHERE predeterminada");
    }

    private int siguienteOrden() {
        Integer n = jdbc.queryForObject("SELECT COALESCE(max(orden), -1) + 1 FROM plantilla_credencial", Integer.class);
        return n == null ? 0 : n;
    }

    private String codigoLibre(String base) {
        String raiz = recortar(base.replaceAll("_\\d+$", ""), 26);
        for (int i = 2; ; i++) {
            String candidato = raiz + "_" + i;
            Integer n = jdbc.queryForObject(
                    "SELECT count(*) FROM plantilla_credencial WHERE codigo = ?", Integer.class, candidato);
            if (n == null || n == 0) return candidato;
        }
    }

    private static String normalizarCodigo(String codigo) {
        String c = codigo == null ? "" : codigo.trim().toUpperCase()
                .replaceAll("[ÁÀÄ]", "A").replaceAll("[ÉÈË]", "E").replaceAll("[ÍÌÏ]", "I")
                .replaceAll("[ÓÒÖ]", "O").replaceAll("[ÚÙÜ]", "U").replace("Ñ", "N")
                .replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (c.length() < 2 || c.length() > 30) {
            throw new PlantillaInvalida("El código debe tener entre 2 y 30 letras o números");
        }
        return c;
    }

    private static String recortar(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private Map<String, Caja> cajas(PlantillaCredencial p) {
        Map<String, Caja> m = new LinkedHashMap<>();
        m.put("qr", p.qr());
        m.put("foto", p.foto());
        m.put("nombre", p.nombre());
        m.put("empresa", p.empresa());
        m.put("ci", p.ci());
        m.put("codigo", p.codigo());
        m.put("zona", p.zona());
        m.put("tipo", p.tipo());
        m.values().removeIf(java.util.Objects::isNull);
        return m;
    }

    private String cajasJson(PlantillaCredencial p) {
        try {
            return json.writeValueAsString(cajas(p));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Caja> leerCajas(String cajasJson) {
        try {
            return json.readValue(cajasJson, new TypeReference<Map<String, Caja>>() { });
        } catch (IOException e) {
            log.warn("Cajas ilegibles en plantilla de credencial: {}", e.getMessage());
            return Map.of();
        }
    }
}

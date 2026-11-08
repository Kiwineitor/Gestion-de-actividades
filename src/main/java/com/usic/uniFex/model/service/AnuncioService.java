package com.usic.uniFex.model.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.dao.IAnuncioDao;
import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.dto.AnuncioDTO;
import com.usic.uniFex.model.entity.Anuncio;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Usuario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnuncioService {

    public static final String TOPIC = "/topic/anuncios";

    private static final Set<String> NIVELES = Set.of(Anuncio.INFO, Anuncio.AVISO, Anuncio.URGENTE);
    private static final Set<String> DESTINOS = Set.of(Anuncio.INTERNO, Anuncio.PUBLICO, Anuncio.AMBOS);
    private static final Set<String> FORMATOS = Set.of(Anuncio.FORMATO_AVISO, Anuncio.FORMATO_DESTACADO,
            Anuncio.FORMATO_COMBO, Anuncio.FORMATO_EMERGENTE);
    private static final Set<String> BOTONES = Set.of(Anuncio.BOTON_NINGUNO, Anuncio.BOTON_ACTIVIDAD,
            Anuncio.BOTON_WHATSAPP, Anuncio.BOTON_ENLACE);
    private static final Set<String> TIPOS_IMAGEN = Set.of("image/png", "image/jpeg", "image/webp");
    private static final Pattern RUTA_IMAGEN = Pattern.compile("^comunicacion/\\d{4}/\\d{2}/[A-Za-z0-9._-]{5,200}$");
    private static final Map<String, String> NOMBRE_FORMATO = Map.of(
            Anuncio.FORMATO_AVISO, "Aviso", Anuncio.FORMATO_DESTACADO, "Destacado",
            Anuncio.FORMATO_COMBO, "Combo", Anuncio.FORMATO_EMERGENTE, "Emergente");

    private final IAnuncioDao dao;
    private final IUsuarioDao usuarioDao;
    private final SimpMessagingTemplate messaging;
    private final AvisoPublicoService avisoPublico;
    private final FileStorageService archivos;
    private final JdbcTemplate jdbc;

    public record NuevoAnuncio(String titulo, String mensaje, String nivel, Integer minutos,
                               String destino, LocalDateTime desde, LocalDateTime hasta, String enlace,
                               String formato, String imagen, Long edicionId, BigDecimal precio, BigDecimal precioAntes,
                               String incluye, String botonTexto, String botonAccion) {
    }

    private record ActividadPublica(String nombre, String contacto) {
    }

    @Transactional(readOnly = true)
    public List<AnuncioDTO> publicos() {
        Map<Long, ActividadPublica> visibles = actividadesVisibles();
        String contactoGeneral = contactoGeneral();
        return dao.publicos(LocalDateTime.now()).stream()
                .filter(a -> a.getEdicionId() == null || visibles.containsKey(a.getEdicionId()))
                .map(a -> {
                    ActividadPublica act = a.getEdicionId() == null ? null : visibles.get(a.getEdicionId());
                    String contacto = act != null && act.contacto() != null ? act.contacto() : contactoGeneral;
                    return AnuncioDTO.de(a, null, act == null ? null : act.nombre(), contacto);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AnuncioDTO> vigentes() {
        return dao.vigentes(LocalDateTime.now()).stream().map(a -> aDto(a, Map.of())).toList();
    }

    @Transactional(readOnly = true)
    public List<AnuncioDTO> historial() {
        Map<Long, String> nombres = new HashMap<>();
        jdbc.query("SELECT id, nombre FROM edicion", rs -> { nombres.put(rs.getLong(1), rs.getString(2)); });
        return dao.historial().stream().map(a -> aDto(a, nombres)).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> actividades() {
        return jdbc.queryForList("""
                SELECT id, nombre, (publica AND (activa OR publicacion_independiente)) AS visible
                  FROM edicion WHERE NOT COALESCE(finalizada, FALSE) OR publica
                 ORDER BY activa DESC, anio DESC, id DESC
                """);
    }

    @Transactional
    public AnuncioDTO publicar(NuevoAnuncio req, Long usuarioId) {
        Anuncio a = new Anuncio();
        aplicar(a, req);
        a.setRegistro(new Date());
        a.setRegistroIdUsuario(usuarioId);
        a.setEstado(Anuncio.PUBLICADO);
        AnuncioDTO dto = aDto(dao.save(a), Map.of());
        HistorialService.anotarResumen(NOMBRE_FORMATO.get(dto.formato()) + ": " + resumen(dto));
        difundir(dto);
        log.info("Anuncio {} ({}) publicado por el usuario {}", dto.id(), dto.formato(), usuarioId);
        return dto;
    }

    @Transactional
    public AnuncioDTO editar(Long id, NuevoAnuncio req, Long usuarioId) {
        Anuncio a = dao.findById(id).orElse(null);
        if (a == null || Anuncio.RETIRADO.equals(a.getEstado())) throw new IllegalArgumentException("La publicación no existe o ya fue retirada");
        Map<String, Object> antes = foto(a);
        String destinoAntes = a.getDestino();
        aplicar(a, req);
        a.setModificacion(new Date());
        a.setModificacionIdUsuario(usuarioId);
        AnuncioDTO dto = aDto(dao.save(a), Map.of());
        HistorialService.anotarResumen(NOMBRE_FORMATO.get(dto.formato()) + ": " + resumen(dto));
        HistorialService.anotarCambios(antes, foto(a));
        if (destinoAntes != null && !destinoAntes.equals(dto.destino()) && !Anuncio.PUBLICO.equals(destinoAntes)) {
            enviarInterno(AnuncioDTO.retirado(id));
        }
        difundir(dto);
        return dto;
    }

    @Transactional
    public boolean retirar(Long id, Long usuarioId) {
        Anuncio a = dao.findById(id).orElse(null);
        if (a == null || Anuncio.RETIRADO.equals(a.getEstado())) return false;
        a.setEstado(Anuncio.RETIRADO);
        a.setModificacion(new Date());
        a.setModificacionIdUsuario(usuarioId);
        dao.save(a);
        HistorialService.anotarResumen(NOMBRE_FORMATO.getOrDefault(a.getFormato(), "Aviso") + ": "
                + (a.getTitulo() != null ? a.getTitulo() : corto(a.getMensaje())));
        enviarInterno(AnuncioDTO.retirado(id));
        avisoPublico.cambio("anuncios", id);
        log.info("Anuncio {} retirado por el usuario {}", id, usuarioId);
        return true;
    }

    public String subirImagen(MultipartFile archivo) throws IOException {
        if (archivo == null || archivo.isEmpty() || !TIPOS_IMAGEN.contains(String.valueOf(archivo.getContentType()))) {
            throw new IllegalArgumentException("Sube una imagen PNG, JPG o WEBP");
        }
        if (archivo.getSize() > 6L * 1024 * 1024) throw new IllegalArgumentException("La imagen no puede pasar de 6 MB");
        return archivos.save(archivo, FileStorageService.Bucket.COMUNICACION, "anuncio");
    }

    public boolean contar(Long id, boolean clic) {
        String columna = clic ? "clics" : "vistas";
        return jdbc.update("UPDATE anuncio SET " + columna + " = " + columna + " + 1 WHERE id = ? AND \"_estado\" = 'A' "
                + "AND destino IN ('PUBLICO', 'AMBOS')", id) == 1;
    }

    private void aplicar(Anuncio a, NuevoAnuncio req) {
        if (req == null) throw new IllegalArgumentException("Faltan los datos de la publicación");
        String formato = req.formato() == null ? Anuncio.FORMATO_AVISO : req.formato().trim().toUpperCase();
        if (!FORMATOS.contains(formato)) throw new IllegalArgumentException("Elige qué tipo de publicación es");
        boolean aviso = Anuncio.FORMATO_AVISO.equals(formato);

        String titulo = limpio(req.titulo(), 120);
        String mensaje = limpio(req.mensaje(), 1000);
        if (aviso && mensaje == null) throw new IllegalArgumentException("El aviso necesita un mensaje");
        if (!aviso && titulo == null) throw new IllegalArgumentException("Escribe un título");

        String imagen = limpio(req.imagen(), 300);
        if (imagen != null && imagen.startsWith("/files/")) imagen = imagen.substring(7);
        if (imagen != null && !imagen.equals(a.getImagen()) && !RUTA_IMAGEN.matcher(imagen).matches()) {
            throw new IllegalArgumentException("La imagen no es válida; vuelve a subirla");
        }
        if (aviso) imagen = null;
        if (Anuncio.FORMATO_DESTACADO.equals(formato) && imagen == null && mensaje == null) {
            throw new IllegalArgumentException("Agrega una imagen o un texto para el destacado");
        }

        Long edicionId = aviso ? null : req.edicionId();
        if (edicionId != null) {
            Integer existe = jdbc.queryForObject("SELECT count(*) FROM edicion WHERE id = ?", Integer.class, edicionId);
            if (existe == null || existe == 0) throw new IllegalArgumentException("La actividad elegida no existe");
        }

        BigDecimal precio = aviso ? null : positivo(req.precio(), "El precio");
        BigDecimal precioAntes = aviso ? null : positivo(req.precioAntes(), "El precio anterior");
        if (Anuncio.FORMATO_COMBO.equals(formato) && precio == null) throw new IllegalArgumentException("Indica el precio del combo");
        if (precio == null) precioAntes = null;
        if (precioAntes != null && precioAntes.compareTo(precio) <= 0) {
            throw new IllegalArgumentException("El precio anterior debe ser mayor que el precio de la promoción");
        }

        String enlace = limpio(req.enlace(), 300);
        if (enlace != null && !enlace.matches("(?i)^https?://\\S{3,290}$")) {
            throw new IllegalArgumentException("El enlace debe empezar con http:// o https://");
        }
        String botonAccion = req.botonAccion() == null ? null : req.botonAccion().trim().toUpperCase();
        if (botonAccion == null || !BOTONES.contains(botonAccion)) {
            botonAccion = enlace != null ? Anuncio.BOTON_ENLACE : Anuncio.BOTON_NINGUNO;
        }
        if (Anuncio.BOTON_ACTIVIDAD.equals(botonAccion) && edicionId == null) {
            throw new IllegalArgumentException("Para el botón «Ir a la actividad» elige una actividad");
        }
        if (Anuncio.BOTON_ENLACE.equals(botonAccion) && enlace == null) {
            throw new IllegalArgumentException("Escribe el enlace del botón");
        }
        if (!Anuncio.BOTON_ENLACE.equals(botonAccion)) enlace = null;
        String botonTexto = Anuncio.BOTON_NINGUNO.equals(botonAccion) ? null : limpio(req.botonTexto(), 40);

        String destino = aviso ? (req.destino() != null && DESTINOS.contains(req.destino()) ? req.destino() : Anuncio.INTERNO) : Anuncio.PUBLICO;

        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime inicio = req.desde() != null && req.desde().isAfter(ahora) ? req.desde() : null;
        LocalDateTime fin = null;
        if (req.hasta() != null) {
            if (!req.hasta().isAfter(inicio != null ? inicio : ahora)) {
                throw new IllegalArgumentException("La fecha de fin debe ser posterior al inicio");
            }
            fin = req.hasta();
        } else if (req.minutos() != null && req.minutos() > 0) {
            fin = (inicio != null ? inicio : ahora).plusMinutes(req.minutos());
        }

        a.setFormato(formato);
        a.setTitulo(titulo);
        a.setMensaje(mensaje);
        a.setNivel(req.nivel() != null && NIVELES.contains(req.nivel()) ? req.nivel() : Anuncio.INFO);
        a.setDestino(destino);
        a.setVigenteDesde(inicio);
        a.setVigenteHasta(fin);
        a.setImagen(imagen);
        a.setEdicionId(edicionId);
        a.setPrecio(precio);
        a.setPrecioAntes(precioAntes);
        a.setIncluye(aviso ? null : limpio(req.incluye(), 1000));
        a.setBotonAccion(botonAccion);
        a.setBotonTexto(botonTexto);
        a.setEnlace(enlace);
    }

    private Map<String, Object> foto(Anuncio a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Título", a.getTitulo());
        m.put("Texto", a.getMensaje());
        m.put("Imagen", a.getImagen() == null ? "Sin imagen" : "Con imagen");
        m.put("Actividad", a.getEdicionId());
        m.put("Precio", a.getPrecio());
        m.put("Precio anterior", a.getPrecioAntes());
        m.put("Qué incluye", a.getIncluye());
        m.put("Botón", a.getBotonTexto());
        m.put("Acción del botón", a.getBotonAccion());
        m.put("Enlace", a.getEnlace());
        m.put("Desde", a.getVigenteDesde());
        m.put("Hasta", a.getVigenteHasta());
        m.put("Quién lo ve", a.getDestino());
        return m;
    }

    private void difundir(AnuncioDTO dto) {
        if (!Anuncio.PUBLICO.equals(dto.destino())) enviarInterno(dto);
        if (dto.destino() == null || !Anuncio.INTERNO.equals(dto.destino())) avisoPublico.cambio("anuncios", dto.id());
    }

    private void enviarInterno(AnuncioDTO dto) {
        try {
            messaging.convertAndSend(TOPIC, dto);
        } catch (Exception e) {
            log.warn("No se pudo difundir el anuncio {}: {}", dto.id(), e.getMessage());
        }
    }

    private Map<Long, ActividadPublica> actividadesVisibles() {
        Map<Long, ActividadPublica> m = new HashMap<>();
        jdbc.query("SELECT id, nombre, contacto FROM edicion WHERE publica AND (activa OR publicacion_independiente)",
                rs -> { m.put(rs.getLong(1), new ActividadPublica(rs.getString(2), limpio(rs.getString(3), 120))); });
        return m;
    }

    private String contactoGeneral() {
        try {
            List<String> v = jdbc.queryForList("SELECT valor FROM parametro_sistema WHERE clave = 'contacto'", String.class);
            return v.isEmpty() ? null : limpio(v.get(0), 120);
        } catch (Exception e) {
            return null;
        }
    }

    private AnuncioDTO aDto(Anuncio a, Map<Long, String> nombres) {
        String autor = null;
        if (a.getRegistroIdUsuario() != null) {
            Usuario u = usuarioDao.findById(a.getRegistroIdUsuario()).orElse(null);
            if (u != null) {
                Persona p = u.getPersona();
                String completo = p == null ? null : java.util.stream.Stream
                        .of(p.getNombre(), p.getPaterno())
                        .filter(x -> x != null && !x.isBlank())
                        .reduce((x, y) -> x + " " + y).orElse(null);
                autor = (completo == null || completo.isBlank()) ? u.getUsername() : completo;
            }
        }
        String edicion = a.getEdicionId() == null ? null : nombres.get(a.getEdicionId());
        if (a.getEdicionId() != null && edicion == null) {
            List<String> n = jdbc.queryForList("SELECT nombre FROM edicion WHERE id = ?", String.class, a.getEdicionId());
            edicion = n.isEmpty() ? null : n.get(0);
        }
        return AnuncioDTO.de(a, autor, edicion, null);
    }

    private static String resumen(AnuncioDTO d) {
        return d.titulo() != null ? d.titulo() : corto(d.mensaje());
    }

    private static String corto(String s) {
        if (s == null) return "";
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }

    private static BigDecimal positivo(BigDecimal v, String nombre) {
        if (v == null) return null;
        if (v.signum() < 0) throw new IllegalArgumentException(nombre + " no puede ser negativo");
        if (v.compareTo(new BigDecimal("9999999")) > 0) throw new IllegalArgumentException(nombre + " es demasiado alto");
        return v.signum() == 0 ? null : v;
    }

    private static String limpio(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }
}

package com.usic.uniFex.model.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.dao.ICategoriaDao;
import com.usic.uniFex.model.dao.IInteresadoStandDao;
import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.dto.InteresadoStandRequest;
import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.InteresadoStand;
import com.usic.uniFex.model.entity.Puesto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InteresadoStandService {

    private static final Pattern SOLO_LETRAS = Pattern.compile("[\\p{L} ]+");
    private static final Pattern SIN_NUMEROS = Pattern.compile("[^\\p{N}\\p{Cc}]+");
    private static final Pattern CELULAR = Pattern.compile("\\+?\\d{7,15}");
    private static final Locale ES = Locale.forLanguageTag("es-BO");
    private static final int MAXIMO_PENDIENTES = 3;
    private static final Set<String> TIPOS_COMPROBANTE = Set.of("image/png", "image/jpeg", "image/webp", "application/pdf");

    private final IInteresadoStandDao interesadoStandDao;
    private final ICategoriaDao categoriaDao;
    private final IPuestoDao puestoDao;
    private final FileStorageService archivos;
    private final JdbcTemplate jdbc;
    private final AvisoPublicoService avisoPublico;
    private final RegistroVentaService registroVenta;
    private final IInscripcionService inscripciones;
    private final EntregaVentaService entregas;
    private final ApartadoPublicoService apartados;

    public record Resultado(boolean ok, String mensaje, InteresadoStand interesado) {
        static Resultado error(String m) { return new Resultado(false, m, null); }
    }

    public record PersonaPuesto(String nombre, String ci, String celular) {
    }

    public record Aprobacion(Long puestoId, String ciRepresentante, Long tipoEntidadId, String nit,
                             List<PersonaPuesto> personas, Boolean pagadoEnOficina) {
    }

    @Transactional
    public Resultado aprobarComoVenta(Long id, Aprobacion a, Long usuarioId) {
        List<String> bloqueada = jdbc.queryForList(
                "SELECT estado FROM interesado_stand WHERE id = ? FOR UPDATE", String.class, id);
        if (bloqueada.isEmpty()) return Resultado.error("La solicitud ya no existe.");
        InteresadoStand i = interesadoStandDao.findById(id).orElse(null);
        if (i == null) return Resultado.error("La solicitud ya no existe.");
        if (i.getInscripcionId() != null) {
            return Resultado.error("Esta solicitud ya se registró como la venta #" + i.getInscripcionId() + ".");
        }
        if (InteresadoStand.ESTADO_DESCARTADO.equals(i.getEstado())) {
            return Resultado.error("La solicitud está rechazada. Vuélvela a revisión antes de aprobarla.");
        }
        if (a == null) return Resultado.error("Faltan los datos para registrar la venta.");
        String ci = i.getCi() != null && !i.getCi().isBlank() ? i.getCi().trim()
                : a.ciRepresentante() == null ? "" : a.ciRepresentante().trim();
        if (ci.isEmpty()) return Resultado.error("Falta el C.I. de quien solicitó el espacio.");
        if (ci.length() > 20) return Resultado.error("El C.I. es demasiado largo.");
        if (a.tipoEntidadId() == null) return Resultado.error("Elige el tipo de expositor.");

        Long puestoId = a.puestoId() != null ? a.puestoId() : (i.getPuesto() != null ? i.getPuesto().getId() : null);
        if (puestoId == null) return Resultado.error("Elige el espacio que se le asigna.");
        Puesto puesto = puestoDao.findById(puestoId).orElse(null);
        if (puesto == null || Puesto.REGISTRO_ANULADO.equals(puesto.getEstado()) || puesto.getCategoria() == null) {
            return Resultado.error("Ese espacio ya no existe.");
        }
        boolean cambiaPuesto = i.getPuesto() == null || !i.getPuesto().getId().equals(puestoId);
        if (cambiaPuesto && puestoSolicitadoPorOtra(puestoId, id)) {
            return Resultado.error("El espacio " + puesto.getCodigo() + " tiene otra solicitud vigente.");
        }

        String apartado = i.getReservaToken();
        if (i.getPuesto() != null && apartado != null) {
            if (cambiaPuesto) apartados.liberarDeSolicitud(i.getPuesto().getId(), apartado);
            else apartados.tomarParaVenta(puestoId, apartado, usuarioId);
        }

        List<RegistroVentaService.DatosPersona> personas = new ArrayList<>();
        personas.add(new RegistroVentaService.DatosPersona(i.getNombreCompleto(), "", "", ci, null, i.getCelular()));

        boolean oficina = InteresadoStand.PAGO_OFICINA.equals(i.getModoPago());
        RegistroVentaService.NuevaVenta venta = new RegistroVentaService.NuevaVenta(
                i.getEmpresa(), a.nit() == null || a.nit().isBlank() ? null : a.nit().trim(), i.getRubro(), i.getRubro(),
                i.getNombreCompleto(), ci, i.getCelular(), a.tipoEntidadId(), null, null, personas,
                null, null, oficina && Boolean.TRUE.equals(a.pagadoEnOficina()), List.of(puestoId));
        RegistroVentaService.Resultado r = registroVenta.registrar(venta, usuarioId, AuditoriaService.ORIGEN_WEB);
        if (!r.ok()) return Resultado.error(r.mensaje());

        if (i.getComprobanteArchivo() != null) {
            Inscripcion ins = inscripciones.findById(r.inscripcionId());
            ins.setImgComprobante(i.getComprobanteArchivo());
            inscripciones.save(ins);
        } else if (oficina && Boolean.TRUE.equals(a.pagadoEnOficina())) {
            jdbc.update("UPDATE inscripcion SET cobro_efectivo = TRUE WHERE id = ?", r.inscripcionId());
        }

        i.setPuesto(puesto);
        i.setCategoria(puesto.getCategoria());
        i.setCi(ci);
        i.setInscripcionId(r.inscripcionId());
        i.setEstado(InteresadoStand.ESTADO_ATENDIDO);
        InteresadoStand guardado = interesadoStandDao.saveAndFlush(i);
        HistorialService.anotarResumen(i.getEmpresa() + " · espacio " + puesto.getCodigo());
        avisoPublico.cambio("feria", puestoId);
        return new Resultado(true, "Venta registrada: el espacio " + puesto.getCodigo()
                + " quedó ocupado a nombre de " + i.getEmpresa() + ".", guardado);
    }

    public Map<String, Object> entrega(InteresadoStand i) {
        return i == null ? null : entregas.deInscripcion(i.getInscripcionId());
    }

    private boolean puestoSolicitadoPorOtra(Long puestoId, Long propia) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM interesado_stand
                 WHERE id_puesto = ? AND id <> ?
                   AND (estado = 'NUEVO' OR (estado = 'ATENDIDO' AND id_inscripcion IS NULL))""",
                Integer.class, puestoId, propia);
        return n != null && n > 0;
    }

    @Transactional
    public Resultado registrar(InteresadoStandRequest d, MultipartFile comprobante) throws IOException {
        String falta = validar(d);
        if (falta != null) return Resultado.error(falta);
        Categoria categoria = categoriaViva(d.categoriaId());
        if (categoria == null) return Resultado.error("La categoría elegida ya no existe.");

        String modo = d.modoPago() == null ? null : d.modoPago().trim().toUpperCase(Locale.ROOT);
        if (modo != null && !InteresadoStand.PAGO_COMPROBANTE.equals(modo) && !InteresadoStand.PAGO_OFICINA.equals(modo)) {
            return Resultado.error("Elige cómo vas a pagar.");
        }
        boolean conArchivo = comprobante != null && !comprobante.isEmpty();
        if (InteresadoStand.PAGO_COMPROBANTE.equals(modo) && !conArchivo) {
            return Resultado.error("Adjunta la foto o PDF de tu comprobante de pago.");
        }
        if (conArchivo && !TIPOS_COMPROBANTE.contains(String.valueOf(comprobante.getContentType()))) {
            return Resultado.error("El comprobante debe ser una foto (PNG, JPG o WEBP) o un PDF.");
        }
        if (conArchivo && comprobante.getSize() > 8L * 1024 * 1024) {
            return Resultado.error("El comprobante no puede pasar de 8 MB.");
        }

        Puesto puesto = null;
        if (d.puestoId() != null) {
            puesto = puestoDao.findById(d.puestoId()).orElse(null);
            if (puesto == null || Puesto.REGISTRO_ANULADO.equals(puesto.getEstado()) || puesto.getCategoria() == null
                    || !puesto.getCategoria().getId().equals(categoria.getId())) {
                return Resultado.error("Ese puesto ya no está disponible. Elige otro en el plano.");
            }
        }
        String token = d.reservaToken() == null || d.reservaToken().isBlank() ? null : d.reservaToken().trim();
        Integer pendientes = jdbc.queryForObject(
                "SELECT count(*) FROM interesado_stand WHERE celular = ? AND estado = ? AND id_puesto IS NOT NULL",
                Integer.class, d.celular().trim(), InteresadoStand.ESTADO_NUEVO);
        if (puesto != null && pendientes != null && pendientes >= MAXIMO_PENDIENTES) {
            return Resultado.error("Ya tienes " + MAXIMO_PENDIENTES + " solicitudes de puesto en revisión con este celular. "
                    + "Espera a que te contactemos para pedir más.");
        }

        if (puesto != null) {
            if (token == null) {
                token = UUID.randomUUID().toString().replace("-", "");
                if (puestoDao.apartarPublico(puesto.getId(), token, "solicitud", 5) == 0) {
                    return Resultado.error("Ese espacio acaba de ser tomado por otra persona. Elige otro en el plano.");
                }
            }
            if (!apartados.fijarParaSolicitud(puesto.getId(), token)) {
                return Resultado.error("Se acabó el tiempo para completar tu compra o el espacio ya no está apartado a tu nombre. "
                        + "Elígelo de nuevo en el plano.");
            }
        }

        InteresadoStand interesado = new InteresadoStand();
        aplicar(interesado, d, categoria);
        if (puesto != null) interesado.setReservaToken(token);
        if (d.ci() != null && !d.ci().isBlank()) interesado.setCi(d.ci().trim());
        interesado.setPuesto(puesto);
        interesado.setModoPago(modo);
        if (conArchivo) interesado.setComprobanteArchivo(archivos.save(comprobante, FileStorageService.Bucket.COMPROBANTES, "solicitud-puesto"));
        InteresadoStand guardado = interesadoStandDao.saveAndFlush(interesado);
        if (puesto != null) avisoPublico.cambio("feria", puesto.getId());
        return new Resultado(true, "Registro enviado.", guardado);
    }

    @Transactional
    public Resultado cambiarEstado(Long id, String estado, String motivo, Long usuarioId) {
        InteresadoStand i = interesadoStandDao.findById(id).orElse(null);
        if (i == null) return Resultado.error("El registro ya no existe.");
        String nuevo = estado == null ? "" : estado.trim().toUpperCase(Locale.ROOT);
        if (!Set.of(InteresadoStand.ESTADO_NUEVO, InteresadoStand.ESTADO_ATENDIDO, InteresadoStand.ESTADO_DESCARTADO).contains(nuevo)) {
            return Resultado.error("Estado no válido.");
        }
        String razon = motivo == null ? "" : motivo.trim();
        if (InteresadoStand.ESTADO_DESCARTADO.equals(nuevo) && razon.length() < 4) {
            return Resultado.error("Escribe el motivo del rechazo: el cliente y tu equipo lo verán en Anulaciones.");
        }
        if (razon.length() > 500) razon = razon.substring(0, 500);
        if (i.getInscripcionId() != null && !InteresadoStand.ESTADO_ATENDIDO.equals(nuevo)) {
            return Resultado.error("Esta solicitud ya es la venta #" + i.getInscripcionId()
                    + ". Si hay que deshacerla, anula la venta desde Inscripciones.");
        }
        boolean reabre = !InteresadoStand.ESTADO_DESCARTADO.equals(nuevo) && InteresadoStand.ESTADO_DESCARTADO.equals(i.getEstado());
        if (reabre && i.getPuesto() != null) {
            if (puestoSolicitado(i.getPuesto().getId())) return Resultado.error("Ese espacio ya tiene otra solicitud vigente.");
            String token = i.getReservaToken() == null ? "sol-" + i.getId() : i.getReservaToken();
            if (!apartados.reapartarParaSolicitud(i.getPuesto().getId(), token)) {
                return Resultado.error("Ese espacio ya fue tomado o vendido. No se puede volver a abrir esta solicitud con ese espacio.");
            }
            i.setReservaToken(token);
        }
        if (InteresadoStand.ESTADO_DESCARTADO.equals(nuevo) && i.getPuesto() != null && i.getReservaToken() != null) {
            apartados.liberarDeSolicitud(i.getPuesto().getId(), i.getReservaToken());
        }
        i.setEstado(nuevo);
        InteresadoStand guardado = interesadoStandDao.saveAndFlush(i);
        if (InteresadoStand.ESTADO_DESCARTADO.equals(nuevo)) {
            jdbc.update("UPDATE interesado_stand SET motivo_descarte = ?, descartado_por = ?, descartado_en = now() WHERE id = ?",
                    razon, usuarioId, id);
            HistorialService.anotarResumen(i.getEmpresa() != null ? i.getEmpresa() : i.getNombreCompleto());
            HistorialService.anotarCambio("Motivo del rechazo", null, razon);
        } else {
            jdbc.update("UPDATE interesado_stand SET motivo_descarte = NULL, descartado_por = NULL, descartado_en = NULL WHERE id = ?", id);
        }
        if (i.getPuesto() != null) avisoPublico.cambio("feria", i.getPuesto().getId());
        String mensaje = switch (nuevo) {
            case InteresadoStand.ESTADO_ATENDIDO -> i.getPuesto() != null
                    ? "Solicitud aprobada: el espacio " + i.getPuesto().getCodigo() + " queda reservado para este expositor."
                    : "Solicitud aprobada.";
            case InteresadoStand.ESTADO_DESCARTADO -> i.getPuesto() != null
                    ? "Solicitud rechazada: el espacio vuelve a estar libre para el público." : "Solicitud rechazada.";
            default -> "La solicitud volvió a revisión.";
        };
        return new Resultado(true, mensaje, guardado);
    }

    private boolean puestoSolicitado(Long puestoId) {
        return puestoSolicitadoPorOtra(puestoId, -1L);
    }

    @Transactional(readOnly = true)
    public List<InteresadoStand> listar() {
        return interesadoStandDao.findAllByOrderByFechaRegistroDesc();
    }

    @Transactional
    public Resultado editar(Long id, InteresadoStandRequest d) {
        InteresadoStand i = interesadoStandDao.findById(id).orElse(null);
        if (i == null) return Resultado.error("El registro ya no existe.");
        if (i.getInscripcionId() != null) return Resultado.error("Esta solicitud ya es una venta: ya no se modifica.");
        if (d == null || d.categoriaId() == null) return Resultado.error("Elige la categoría.");
        Categoria categoria = categoriaViva(d.categoriaId());
        if (categoria == null) return Resultado.error("La categoría elegida ya no existe.");
        i.setCategoria(categoria);
        return new Resultado(true, "Categoría actualizada. Los datos que envió el cliente no se modifican.", interesadoStandDao.save(i));
    }

    @Transactional
    public Resultado reemplazarComprobante(Long id, org.springframework.web.multipart.MultipartFile archivo, Long usuarioId) throws java.io.IOException {
        InteresadoStand i = interesadoStandDao.findById(id).orElse(null);
        if (i == null) return Resultado.error("El registro ya no existe.");
        if (i.getInscripcionId() != null) return Resultado.error("Esta solicitud ya es una venta: adjunta el comprobante desde su ficha.");
        if (InteresadoStand.ESTADO_DESCARTADO.equals(i.getEstado())) return Resultado.error("La solicitud está rechazada.");
        if (archivo == null || archivo.isEmpty()) return Resultado.error("Elige la imagen o PDF del comprobante.");
        if (archivo.getSize() > 8L * 1024 * 1024) return Resultado.error("El comprobante no puede pasar de 8 MB.");
        String anterior = i.getComprobanteArchivo();
        i.setComprobanteArchivo(archivos.save(archivo, FileStorageService.Bucket.COMPROBANTES, "solicitud-corregida"));
        InteresadoStand guardado = interesadoStandDao.saveAndFlush(i);
        jdbc.update("UPDATE interesado_stand SET comprobante_cambiado_por = ?, comprobante_cambiado_en = now() WHERE id = ?", usuarioId, id);
        HistorialService.anotarResumen(i.getEmpresa() != null ? i.getEmpresa() : i.getNombreCompleto());
        HistorialService.anotarCambio("Comprobante", anterior == null ? "Sin comprobante" : "Comprobante anterior", "Comprobante corregido");
        return new Resultado(true, "Comprobante reemplazado. Revísalo y aprueba la solicitud.", guardado);
    }

    private void aplicar(InteresadoStand i, InteresadoStandRequest d, Categoria categoria) {
        i.setNombreCompleto(mayusculas(d.nombreCompleto()));
        i.setCelular(d.celular().trim());
        if (d.celularExtra() != null) {
            String extra = d.celularExtra().replaceAll("\\s", "");
            i.setCelularExtra(extra.matches("\\+?\\d{7,15}") && !extra.equals(i.getCelular()) ? extra : null);
        }
        i.setEmpresa(mayusculas(d.empresa()));
        i.setRubro(mayusculas(d.rubro()));
        i.setCategoria(categoria);
    }

    private Categoria categoriaViva(Long id) {
        Categoria c = categoriaDao.findById(id).orElse(null);
        return (c == null || "X".equals(c.getEstado())) ? null : c;
    }

    private String validar(InteresadoStandRequest d) {
        String e = requerido(d.nombreCompleto(), "El nombre completo", 200);
        if (e == null && !SIN_NUMEROS.matcher(d.nombreCompleto().trim()).matches()) {
            e = "El nombre completo no puede tener números.";
        }
        if (e == null) e = requerido(d.celular(), "El número de celular", 30);
        if (e == null && !CELULAR.matcher(d.celular().trim()).matches()) {
            e = "El número de celular solo puede tener números (de 7 a 15), con un + opcional al inicio.";
        }
        if (e == null) e = requerido(d.empresa(), "El nombre de la empresa o emprendimiento", 200);
        if (e == null) e = soloLetras(d.empresa(), "El nombre de la empresa o emprendimiento");
        if (e == null) e = requerido(d.rubro(), "El rubro", 200);
        if (e == null) e = soloLetras(d.rubro(), "El rubro");
        if (e == null && d.categoriaId() == null) e = "Elige una categoría.";
        return e;
    }

    private String requerido(String valor, String campo, int max) {
        if (valor == null || valor.isBlank()) return campo + " es obligatorio.";
        if (valor.trim().length() > max) return campo + " es demasiado largo (máx. " + max + " caracteres).";
        return null;
    }

    private String soloLetras(String valor, String campo) {
        return SOLO_LETRAS.matcher(valor.trim()).matches()
                ? null
                : campo + " solo puede tener letras (sin números ni símbolos).";
    }

    private static String mayusculas(String s) {
        return s.trim().replaceAll("\\s{2,}", " ").toUpperCase(ES);
    }
}

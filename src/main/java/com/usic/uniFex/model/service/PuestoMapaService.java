package com.usic.uniFex.model.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.IService.ICategoriaService;
import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.entity.Puesto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PuestoMapaService {

    private final IPuestoDao puestoDao;
    private final ICategoriaService categoriaService;

    public record Posicion(Long id, Double x, Double y, Double escala, Integer rotacion) {
    }

    private static Integer giroValido(Integer grados) {
        if (grados == null) return null;
        int g = grados % 360;
        return g < 0 ? g + 360 : g;
    }

    @Transactional
    public List<Long> guardarPosiciones(List<Posicion> posiciones) {
        List<Long> cambiados = new ArrayList<>();
        for (Posicion p : posiciones) {
            if (p.id() == null) continue;
            if (puestoDao.actualizarPosicion(p.id(), p.x(), p.y(), p.escala(), giroValido(p.rotacion())) > 0) {
                cambiados.add(p.id());
            }
        }
        log.info("Editor: {} casetas reposicionadas de {} enviadas", cambiados.size(), posiciones.size());
        return cambiados;
    }

    @Transactional
    public boolean anular(Long puestoId, Long usuarioId) {
        boolean ok = puestoDao.anularSiLibreYSinVentas(puestoId, usuarioId) > 0;
        log.info("Anulacion puesto={} usuario={} -> {}", puestoId, usuarioId, ok ? "OK" : "RECHAZADA");
        return ok;
    }

    @Transactional
    public boolean bloquear(Long puestoId, Long usuarioId) {
        boolean ok = puestoDao.bloquearSiLibre(puestoId, usuarioId) > 0;
        log.info("Bloqueo puesto={} usuario={} -> {}", puestoId, usuarioId, ok ? "OK" : "RECHAZADA");
        return ok;
    }

    public record CambioFicha(String tamano, String observacion, String referencia) {
    }

    public record VentaDePuesto(Long inscripcionId, String expositor, String actividad,
                                String representante, String celular, boolean pagado,
                                boolean contado, java.time.LocalDateTime fecha,
                                String vendedor, java.math.BigDecimal costo) {
    }

    @Transactional
    public boolean actualizarFicha(Long puestoId, CambioFicha cambio, Long usuarioId) {
        Puesto p = puestoDao.findById(puestoId).orElse(null);
        if (p == null) return false;
        String tamano = recortar(cambio.tamano(), 40);
        return puestoDao.actualizarFicha(puestoId,
                tamano != null ? tamano : p.getTamano(),
                recortar(cambio.observacion(), 500),
                recortar(cambio.referencia(), 200),
                usuarioId) > 0;
    }

    @Transactional(readOnly = true)
    public List<Long> pagados() {
        return puestoDao.findPuestosPagados();
    }

    @Transactional
    public String cambiarCategoria(Long puestoId, Long categoriaId, Long usuarioId) {
        Puesto p = puestoDao.findById(puestoId).orElse(null);
        if (p == null || Puesto.REGISTRO_ANULADO.equals(p.getEstado())) return "El puesto no existe";
        Categoria destino = categoriaId == null ? null : categoriaService.findById(categoriaId);
        if (destino == null || Puesto.REGISTRO_ANULADO.equals(destino.getEstado())) return "La categoría no existe";
        if (p.getCategoria() != null && destino.getId().equals(p.getCategoria().getId())) return null;
        Set<String> usados = puestoDao.activosDeCategoria(destino.getId()).stream()
                .map(Puesto::getCodigo).filter(Objects::nonNull).map(String::trim).collect(Collectors.toSet());
        String codigo = p.getCodigo() == null ? "" : p.getCodigo().trim();
        if (codigo.isEmpty() || usados.contains(codigo)) {
            int mayor = usados.stream().mapToInt(c -> {
                try {
                    return Integer.parseInt(c);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }).max().orElse(0);
            codigo = String.valueOf(mayor + 1);
        }
        boolean ok = puestoDao.cambiarCategoriaSiLibre(puestoId, destino.getId(), codigo, usuarioId) > 0;
        log.info("Cambio de categoria puesto={} -> categoria={} usuario={} -> {}", puestoId, destino.getId(),
                usuarioId, ok ? "OK" : "RECHAZADO");
        return ok ? null : "Solo se cambia de categoría un puesto libre o bloqueado";
    }

    @Transactional(readOnly = true)
    public VentaDePuesto ventaVigente(Long puestoId) {
        List<Object[]> filas = puestoDao.findVentaVigenteDePuesto(puestoId);
        if (filas.isEmpty()) return null;
        Object[] f = filas.get(0);
        String vendedor = java.util.stream.Stream.of(f[8], f[9], f[10])
                .filter(Objects::nonNull).map(Object::toString).filter(s -> !s.isBlank())
                .collect(Collectors.joining(" "));
        Object fecha = f[7];
        return new VentaDePuesto(
                f[0] == null ? null : ((Number) f[0]).longValue(),
                (String) f[1], (String) f[2], (String) f[3], (String) f[4],
                f[5] != null && !f[5].toString().isBlank(),
                Boolean.TRUE.equals(f[6]),
                fecha instanceof java.sql.Timestamp ts ? ts.toLocalDateTime()
                        : fecha instanceof java.time.LocalDateTime ldt ? ldt : null,
                vendedor.isBlank() ? null : vendedor,
                f[11] == null ? null : new java.math.BigDecimal(f[11].toString()));
    }

    private static String recortar(String valor, int max) {
        if (valor == null || valor.isBlank()) return null;
        String limpio = valor.trim();
        return limpio.length() > max ? limpio.substring(0, max) : limpio;
    }

    @Transactional
    public boolean cambiarReferencia(Long puestoId, String referencia, Long usuarioId) {
        String limpia = (referencia == null || referencia.isBlank()) ? null : referencia.trim();
        return puestoDao.actualizarReferencia(puestoId, limpia, usuarioId) > 0;
    }

    @Transactional
    public boolean desbloquear(Long puestoId, Long usuarioId) {
        boolean ok = puestoDao.desbloquearSiBloqueada(puestoId, usuarioId) > 0;
        log.info("Desbloqueo puesto={} usuario={} -> {}", puestoId, usuarioId, ok ? "OK" : "RECHAZADA");
        return ok;
    }

    @Transactional
    public Puesto crear(Long categoriaId, String codigo, String tamano, Long usuarioId) {
        Categoria c = categoriaService.findById(categoriaId);
        if (c == null) return null;

        Date ahora = new Date();
        Puesto p = new Puesto();
        p.setCodigo(codigo);
        p.setTamano(tamano);
        p.setEstadoPuesto(Puesto.LIBRE);
        p.setCategoria(c);
        p.setMapaEscala(1.0);
        p.setEstado(Puesto.REGISTRO_ACTIVO);
        p.setRegistro(ahora);
        p.setModificacion(ahora);
        p.setRegistroIdUsuario(usuarioId);
        p.setModificacionIdUsuario(usuarioId);
        return puestoDao.save(p);
    }

    public record Codigo(Long id, String codigo) {
    }

    public record ResultadoRenumeracion(boolean ok, String mensaje, List<Long> cambiados) {
    }

    @Transactional
    public ResultadoRenumeracion renumerar(List<Codigo> cambios, Long usuarioId) {
        if (cambios == null || cambios.isEmpty()) {
            return new ResultadoRenumeracion(false, "No se indico ninguna caseta", List.of());
        }

        Map<Long, String> pedidos = new LinkedHashMap<>();
        for (Codigo c : cambios) {
            if (c == null || c.id() == null) {
                return new ResultadoRenumeracion(false, "Llego una caseta sin identificador", List.of());
            }
            String codigo = c.codigo() == null ? "" : c.codigo().trim();
            if (codigo.isEmpty()) {
                return new ResultadoRenumeracion(false, "Hay una caseta sin numero", List.of());
            }
            if (pedidos.put(c.id(), codigo) != null) {
                return new ResultadoRenumeracion(false, "La misma caseta viene dos veces en el lote", List.of());
            }
        }

        List<Puesto> delLote = puestoDao.findAllById(pedidos.keySet());
        if (delLote.size() != pedidos.size()) {
            return new ResultadoRenumeracion(false, "Alguna caseta ya no existe", List.of());
        }
        Map<Long, String> codigoActual = new HashMap<>();
        Map<Long, Long> categoriaDe = new HashMap<>();
        Map<Long, String> nombreCategoria = new HashMap<>();
        for (Puesto p : delLote) {
            if (Puesto.REGISTRO_ANULADO.equals(p.getEstado())) {
                return new ResultadoRenumeracion(false, "No se puede numerar una caseta anulada", List.of());
            }
            Long catId = p.getCategoria() != null ? p.getCategoria().getId() : null;
            codigoActual.put(p.getId(), p.getCodigo());
            categoriaDe.put(p.getId(), catId);
            if (catId != null) nombreCategoria.putIfAbsent(catId, p.getCategoria().getNombre());
        }

        for (Long catId : new LinkedHashSet<>(categoriaDe.values())) {
            List<Long> idsAqui = categoriaDe.entrySet().stream()
                    .filter(e -> Objects.equals(e.getValue(), catId))
                    .map(Map.Entry::getKey).toList();

            Set<String> ajenos = new HashSet<>();
            if (catId != null) {
                for (Puesto p : puestoDao.activosDeCategoria(catId)) {
                    if (!pedidos.containsKey(p.getId()) && p.getCodigo() != null) {
                        ajenos.add(p.getCodigo().trim());
                    }
                }
            }

            Set<String> enLote = new HashSet<>();
            String etiqueta = catId != null ? "\"" + nombreCategoria.get(catId) + "\"" : "sin categoria";
            for (Long id : idsAqui) {
                String codigo = pedidos.get(id);
                if (!enLote.add(codigo)) {
                    return new ResultadoRenumeracion(false,
                            "El numero " + codigo + " se repite dentro de la seleccion en " + etiqueta,
                            List.of());
                }
                if (ajenos.contains(codigo)) {
                    return new ResultadoRenumeracion(false,
                            "El numero " + codigo + " ya lo tiene otra caseta de " + etiqueta,
                            List.of());
                }
            }
        }

        List<Long> cambiados = new ArrayList<>();
        for (Map.Entry<Long, String> e : pedidos.entrySet()) {
            String actual = codigoActual.get(e.getKey());
            if (e.getValue().equals(actual == null ? null : actual.trim())) continue;
            if (puestoDao.actualizarCodigo(e.getKey(), e.getValue(), usuarioId) > 0) {
                cambiados.add(e.getKey());
            }
        }
        log.info("Renumeracion: {} casetas cambiadas de {} enviadas (usuario {})",
                cambiados.size(), pedidos.size(), usuarioId);
        return new ResultadoRenumeracion(true,
                "Numeradas " + cambiados.size() + " caseta(s)", cambiados);
    }

    public record PrecioCaseta(Long id, java.math.BigDecimal precio) {
    }

    @Transactional
    public ResultadoRenumeracion actualizarPrecios(List<PrecioCaseta> cambios, Long usuarioId) {
        if (cambios == null || cambios.isEmpty()) {
            return new ResultadoRenumeracion(false, "No se indico ninguna caseta", List.of());
        }

        Map<Long, java.math.BigDecimal> pedidos = new LinkedHashMap<>();
        for (PrecioCaseta c : cambios) {
            if (c == null || c.id() == null) {
                return new ResultadoRenumeracion(false, "Llego una caseta sin identificador", List.of());
            }
            if (c.precio() != null && c.precio().signum() < 0) {
                return new ResultadoRenumeracion(false, "Un precio no puede ser negativo", List.of());
            }
            if (pedidos.containsKey(c.id())) {
                return new ResultadoRenumeracion(false, "La misma caseta viene dos veces en el lote", List.of());
            }
            pedidos.put(c.id(), c.precio() == null
                    ? null
                    : c.precio().setScale(2, java.math.RoundingMode.HALF_UP));
        }

        List<Long> cambiados = new ArrayList<>();
        for (Map.Entry<Long, java.math.BigDecimal> e : pedidos.entrySet()) {
            if (puestoDao.actualizarPrecio(e.getKey(), e.getValue(), usuarioId) > 0) {
                cambiados.add(e.getKey());
            }
        }

        log.info("Precios propios: {} casetas cambiadas de {} enviadas (usuario {})",
                cambiados.size(), pedidos.size(), usuarioId);
        return new ResultadoRenumeracion(true,
                "Precio guardado en " + cambiados.size() + " caseta(s)", cambiados);
    }
}

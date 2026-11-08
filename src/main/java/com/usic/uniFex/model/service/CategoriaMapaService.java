package com.usic.uniFex.model.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.IService.ICategoriaService;
import com.usic.uniFex.model.IService.IPuestoService;
import com.usic.uniFex.model.dao.ICategoriaDao;
import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.entity.Puesto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoriaMapaService {

    private final ICategoriaService categoriaService;
    private final IPuestoService puestoService;
    private final IPuestoDao puestoDao;
    private final ICategoriaDao categoriaDao;
    private final com.usic.uniFex.model.dao.IEdicionDao edicionDao;

    public record NuevaCategoria(String nombre, Integer cantidad, String color, String forma,
                                 Double tamanoMapa, BigDecimal precioBase, String tamano,
                                 Double mapaProporcion, Integer personasPorPuesto, BigDecimal costoPersonaExtra, String descripcion) {
    }

    public record CambioCategoria(String nombre, String color, String forma, Double tamanoMapa,
                                  BigDecimal precioBase, Double mapaProporcion, String tamano,
                                  Integer personasPorPuesto, BigDecimal costoPersonaExtra, String descripcion) {
    }

    private static java.util.Map<String, Object> foto(Categoria c) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("Nombre", c.getNombre());
        m.put("Precio", c.getPrecioBase());
        m.put("Qué incluye", c.getDescripcion());
        m.put("Forma", c.getForma());
        m.put("Color", c.getColor());
        m.put("Personas incluidas", c.getPersonasPorPuesto());
        m.put("Costo por persona extra", c.getCostoPersonaExtra());
        return m;
    }

    private static String descripcionValida(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.strip();
        return t.length() > 255 ? t.substring(0, 255) : t;
    }

    private static Integer personasValidas(Integer n) {
        if (n == null) return null;
        return Math.max(1, Math.min(50, n));
    }

    private static BigDecimal costoValido(BigDecimal c) {
        if (c == null) return null;
        return c.signum() < 0 ? BigDecimal.ZERO : c;
    }

    public record ResultadoAjuste(int creadas, int anuladas, int noQuitadas, List<Long> afectados) {
    }

    @Transactional(readOnly = true)
    public List<Categoria> listar() {
        return listar(null);
    }

    @Transactional(readOnly = true)
    public List<Categoria> listar(Long edicion) {
        return edicion == null ? categoriaDao.deEdicionActiva() : categoriaDao.deEdicion(edicion);
    }

    @Transactional
    public Categoria crear(NuevaCategoria req, Long usuarioId, Long edicion) {
        Date now = new Date();
        Categoria c = new Categoria();
        c.setNombre(req.nombre());
        c.setIdEdicion(edicion == null
                ? edicionDao.findFirstByActivaTrueOrderByAnioDesc().map(e -> e.getId()).orElse(null) : edicion);
        c.setColor(req.color());
        c.setDescripcion(descripcionValida(req.descripcion()));
        c.setForma(formaValida(req.forma()));
        c.setTamanoMapa(req.tamanoMapa() != null ? req.tamanoMapa() : Categoria.TAMANO_MAPA_POR_DEFECTO);
        c.setMapaProporcion(proporcionValida(req.mapaProporcion()));
        c.setPrecioBase(req.precioBase() != null ? req.precioBase() : Categoria.PRECIO_POR_DEFECTO);
        Integer personas = personasValidas(req.personasPorPuesto());
        c.setPersonasPorPuesto(personas != null ? personas : 2);
        BigDecimal extra = costoValido(req.costoPersonaExtra());
        c.setCostoPersonaExtra(extra != null ? extra : new BigDecimal("15"));
        c.setEstado(Categoria.REGISTRO_ACTIVO);
        sellarRegistro(c, now, usuarioId);
        Categoria guardada = categoriaService.save(c);

        String tamano = (req.tamano() != null && !req.tamano().isBlank())
                ? req.tamano().trim() : Categoria.TAMANO_POR_DEFECTO;
        int n = (req.cantidad() == null) ? 0 : Math.max(0, req.cantidad());
        for (int i = 1; i <= n; i++) nuevaCaseta(guardada, String.valueOf(i), tamano, now, usuarioId);
        return guardada;
    }

    @Transactional
    public Categoria actualizar(Long id, CambioCategoria cambio) {
        Categoria c = categoriaService.findById(id);
        if (c == null) return null;
        java.util.Map<String, Object> antes = foto(c);
        if (cambio.nombre() != null) c.setNombre(cambio.nombre());
        if (cambio.color() != null) c.setColor(cambio.color());
        if (cambio.descripcion() != null) c.setDescripcion(descripcionValida(cambio.descripcion()));
        if (cambio.forma() != null) c.setForma(formaValida(cambio.forma()));
        if (cambio.tamanoMapa() != null) c.setTamanoMapa(cambio.tamanoMapa());
        if (cambio.mapaProporcion() != null) c.setMapaProporcion(proporcionValida(cambio.mapaProporcion()));
        if (cambio.tamano() != null && !cambio.tamano().isBlank()) {
            puestoDao.actualizarTamanoDeCategoria(id, cambio.tamano().trim());
        }
        if (cambio.precioBase() != null) c.setPrecioBase(cambio.precioBase());
        if (cambio.personasPorPuesto() != null) c.setPersonasPorPuesto(personasValidas(cambio.personasPorPuesto()));
        if (cambio.costoPersonaExtra() != null) c.setCostoPersonaExtra(costoValido(cambio.costoPersonaExtra()));
        c.setModificacion(new Date());
        Categoria guardada = categoriaService.save(c);
        HistorialService.anotarResumen(guardada.getNombre());
        HistorialService.anotarCambios(antes, foto(guardada));
        return guardada;
    }

    @Transactional
    public ResultadoAjuste ajustarCantidad(Long categoriaId, int cantidadDeseada, Long usuarioId) {
        Categoria c = categoriaService.findById(categoriaId);
        if (c == null) return null;

        List<Puesto> activas = puestoDao.activosDeCategoria(categoriaId);
        int actual = activas.size();
        List<Long> afectados = new ArrayList<>();

        if (cantidadDeseada > actual) {
            Date now = new Date();
            int siguiente = maxCodigoNumerico(activas) + 1;
            int aCrear = cantidadDeseada - actual;
            String tamano = activas.stream()
                    .map(Puesto::getTamano)
                    .filter(t -> t != null && !t.isBlank())
                    .findFirst()
                    .orElse(Categoria.TAMANO_POR_DEFECTO);
            for (int i = 0; i < aCrear; i++) {
                Puesto p = nuevaCaseta(c, String.valueOf(siguiente + i), tamano, now, usuarioId);
                afectados.add(p.getId());
            }
            return new ResultadoAjuste(aCrear, 0, 0, afectados);
        }

        if (cantidadDeseada < actual) {
            activas.sort(Comparator.comparingInt(CategoriaMapaService::codigoNumerico).reversed());
            int aQuitar = actual - cantidadDeseada;
            int anuladas = 0, noQuitadas = 0;
            for (Puesto p : activas) {
                if (anuladas >= aQuitar) break;
                if (puestoDao.anularSiLibreYSinVentas(p.getId(), usuarioId) > 0) {
                    anuladas++;
                    afectados.add(p.getId());
                } else {
                    noQuitadas++;
                }
            }
            return new ResultadoAjuste(0, anuladas, noQuitadas, afectados);
        }

        return new ResultadoAjuste(0, 0, 0, afectados);
    }

    @Transactional
    public List<Long> eliminar(Long categoriaId, Long usuarioId) {
        Categoria c = categoriaService.findById(categoriaId);
        if (c == null) return null;
        if (puestoDao.contarNoEliminablesDeCategoria(categoriaId) > 0) return null;

        List<Long> ids = puestoDao.idsActivosDeCategoria(categoriaId);
        puestoDao.anularCasetasDeCategoria(categoriaId, usuarioId);
        categoriaDao.anular(categoriaId);
        return ids;
    }

    private Puesto nuevaCaseta(Categoria c, String codigo, String tamano, Date now, Long usuarioId) {
        Puesto p = new Puesto();
        p.setCodigo(codigo);
        p.setTamano(tamano != null ? tamano : Categoria.TAMANO_POR_DEFECTO);
        p.setEstadoPuesto(Puesto.LIBRE);
        p.setCategoria(c);
        p.setMapaEscala(1.0);
        p.setEstado(Puesto.REGISTRO_ACTIVO);
        sellarRegistro(p, now, usuarioId);
        return puestoService.save(p);
    }

    private void sellarRegistro(com.usic.uniFex.Config.AuditoriaConfig e, Date now, Long usuarioId) {
        e.setRegistro(now);
        e.setModificacion(now);
        e.setRegistroIdUsuario(usuarioId);
        e.setModificacionIdUsuario(usuarioId);
    }

    private static int codigoNumerico(Puesto p) {
        try {
            return Integer.parseInt(p.getCodigo().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static int maxCodigoNumerico(List<Puesto> casetas) {
        return casetas.stream().mapToInt(CategoriaMapaService::codigoNumerico).max().orElse(0);
    }

    private static String formaValida(String forma) {
        String f = forma == null ? "" : forma.trim().toLowerCase();
        return Categoria.FORMAS.contains(f) ? f : "cuadrado";
    }

    private static double proporcionValida(Double proporcion) {
        if (proporcion == null || proporcion.isNaN() || proporcion <= 0) return 1.0;
        return Math.min(Categoria.PROPORCION_MAX, Math.max(Categoria.PROPORCION_MIN, proporcion));
    }
}

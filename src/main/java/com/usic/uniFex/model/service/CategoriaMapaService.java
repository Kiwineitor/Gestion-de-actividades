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
                                 Double mapaProporcion) {
    }

    public record CambioCategoria(String nombre, String color, String forma, Double tamanoMapa,
                                  BigDecimal precioBase, Double mapaProporcion, String tamano) {
    }

    public record ResultadoAjuste(int creadas, int anuladas, int noQuitadas, List<Long> afectados) {
    }

    @Transactional(readOnly = true)
    public List<Categoria> listar() {
        return categoriaDao.deEdicionActiva();
    }

    @Transactional
    public Categoria crear(NuevaCategoria req, Long usuarioId) {
        Date now = new Date();
        Categoria c = new Categoria();
        c.setNombre(req.nombre());
        c.setIdEdicion(edicionDao.findFirstByActivaTrueOrderByAnioDesc().map(com.usic.uniFex.model.entity.Edicion::getId).orElse(null));
        c.setColor(req.color());
        c.setForma(formaValida(req.forma()));
        c.setTamanoMapa(req.tamanoMapa() != null ? req.tamanoMapa() : Categoria.TAMANO_MAPA_POR_DEFECTO);
        c.setMapaProporcion(proporcionValida(req.mapaProporcion()));
        c.setPrecioBase(req.precioBase() != null ? req.precioBase() : Categoria.PRECIO_POR_DEFECTO);
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
        if (cambio.nombre() != null) c.setNombre(cambio.nombre());
        if (cambio.color() != null) c.setColor(cambio.color());
        if (cambio.forma() != null) c.setForma(formaValida(cambio.forma()));
        if (cambio.tamanoMapa() != null) c.setTamanoMapa(cambio.tamanoMapa());
        if (cambio.mapaProporcion() != null) c.setMapaProporcion(proporcionValida(cambio.mapaProporcion()));
        if (cambio.tamano() != null && !cambio.tamano().isBlank()) {
            puestoDao.actualizarTamanoDeCategoria(id, cambio.tamano().trim());
        }
        if (cambio.precioBase() != null) c.setPrecioBase(cambio.precioBase());
        c.setModificacion(new Date());
        return categoriaService.save(c);
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

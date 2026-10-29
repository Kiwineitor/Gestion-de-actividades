package com.usic.uniFex.model.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.ICategoriaOpcionDao;
import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.entity.CategoriaOpcion;
import com.usic.uniFex.model.IService.ICategoriaService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoriaOpcionService {

    private final ICategoriaOpcionDao dao;
    private final ICategoriaService categoriaService;

    public record CambioOpcion(String nombre, BigDecimal precio, Boolean predeterminada, Integer orden) {
    }

    @Transactional(readOnly = true)
    public List<CategoriaOpcion> listar(Long categoriaId) {
        return dao.vivasDe(categoriaId);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<CategoriaOpcion>> porCategoria() {
        return dao.todasVivas().stream()
                .collect(Collectors.groupingBy(o -> o.getCategoria().getId(),
                        java.util.LinkedHashMap::new, Collectors.toList()));
    }

    @Transactional
    public CategoriaOpcion asegurarPredeterminada(Long categoriaId, String nombre,
                                                  BigDecimal precio, Long usuarioId) {
        if (!dao.vivasDe(categoriaId).isEmpty()) return null;
        return crear(categoriaId, new CambioOpcion(nombre, precio, true, 0), usuarioId);
    }

    @Transactional
    public CategoriaOpcion crear(Long categoriaId, CambioOpcion req, Long usuarioId) {
        Categoria c = categoriaService.findById(categoriaId);
        if (c == null) throw new IllegalArgumentException("La categoria no existe");
        String nombre = limpio(req.nombre());
        if (nombre == null) throw new IllegalArgumentException("La opcion necesita un nombre");

        CategoriaOpcion o = new CategoriaOpcion();
        o.setCategoria(c);
        o.setNombre(nombre);
        o.setPrecio(req.precio() == null ? BigDecimal.ZERO : req.precio());
        o.setOrden(req.orden() == null ? siguienteOrden(categoriaId) : req.orden());
        o.setPredeterminada(false);
        o.setRegistro(new Date());
        o.setRegistroIdUsuario(usuarioId);
        o.setEstado(CategoriaOpcion.REGISTRO_ACTIVO);
        boolean primera = dao.vivasDe(categoriaId).isEmpty();
        o = dao.save(o);
        if (primera || Boolean.TRUE.equals(req.predeterminada())) marcarPredeterminada(categoriaId, o.getId());
        return o;
    }

    @Transactional
    public CategoriaOpcion actualizar(Long id, CambioOpcion req, Long usuarioId) {
        CategoriaOpcion o = dao.findById(id).orElse(null);
        if (o == null || CategoriaOpcion.REGISTRO_ANULADO.equals(o.getEstado())) return null;
        if (limpio(req.nombre()) != null) o.setNombre(limpio(req.nombre()));
        if (req.precio() != null) o.setPrecio(req.precio());
        if (req.orden() != null) o.setOrden(req.orden());
        o.setModificacion(new Date());
        o.setModificacionIdUsuario(usuarioId);
        o = dao.save(o);
        if (Boolean.TRUE.equals(req.predeterminada())) {
            marcarPredeterminada(o.getCategoria().getId(), o.getId());
        } else if (Boolean.TRUE.equals(o.getPredeterminada())) {
            sincronizarPrecioBase(o.getCategoria().getId(), o.getPrecio());
        }
        return o;
    }

    @Transactional
    public void marcarPredeterminada(Long categoriaId, Long opcionId) {
        for (CategoriaOpcion otra : dao.vivasDe(categoriaId)) {
            if (!otra.getId().equals(opcionId) && Boolean.TRUE.equals(otra.getPredeterminada())) {
                otra.setPredeterminada(false);
                dao.save(otra);
            }
        }
        dao.flush();
        CategoriaOpcion o = dao.findById(opcionId).orElseThrow();
        o.setPredeterminada(true);
        dao.save(o);
        sincronizarPrecioBase(categoriaId, o.getPrecio());
    }

    @Transactional
    public String eliminar(Long id, Long usuarioId) {
        CategoriaOpcion o = dao.findById(id).orElse(null);
        if (o == null || CategoriaOpcion.REGISTRO_ANULADO.equals(o.getEstado())) return null;
        Long categoriaId = o.getCategoria().getId();
        List<CategoriaOpcion> vivas = dao.vivasDe(categoriaId);
        if (vivas.size() <= 1) {
            return "Una categoria no puede quedarse sin ninguna opcion de precio";
        }
        if (Boolean.TRUE.equals(o.getPredeterminada())) {
            return "Esta es la opcion predeterminada: marca otra como predeterminada antes de quitarla";
        }
        o.setEstado(CategoriaOpcion.REGISTRO_ANULADO);
        o.setModificacion(new Date());
        o.setModificacionIdUsuario(usuarioId);
        dao.save(o);
        return "";
    }

    @Transactional(readOnly = true)
    public CategoriaOpcion resolverParaVenta(Long categoriaId, Long opcionPedida) {
        if (opcionPedida != null) {
            CategoriaOpcion o = dao.findById(opcionPedida).orElse(null);
            if (o != null && !CategoriaOpcion.REGISTRO_ANULADO.equals(o.getEstado())
                    && o.getCategoria() != null && categoriaId.equals(o.getCategoria().getId())) {
                return o;
            }
            throw new IllegalArgumentException("La opcion de precio elegida no es de esa categoria");
        }
        return dao.predeterminadaDe(categoriaId).orElse(null);
    }

    private void sincronizarPrecioBase(Long categoriaId, BigDecimal precio) {
        Categoria c = categoriaService.findById(categoriaId);
        if (c == null) return;
        c.setPrecioBase(precio == null ? BigDecimal.ZERO : precio);
        c.setModificacion(new Date());
        categoriaService.save(c);
    }

    private int siguienteOrden(Long categoriaId) {
        return dao.vivasDe(categoriaId).stream()
                .mapToInt(o -> o.getOrden() == null ? 0 : o.getOrden())
                .max().orElse(-1) + 1;
    }

    private static String limpio(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

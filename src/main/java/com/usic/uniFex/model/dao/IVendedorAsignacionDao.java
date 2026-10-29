package com.usic.uniFex.model.dao;

import java.util.List;

import com.usic.uniFex.model.entity.Puesto;

public interface IVendedorAsignacionDao {

    void asignarPuesto(Long usuarioId, Long puestoId, Long adminId);

    void quitarPuesto(Long usuarioId, Long puestoId);

    void reemplazarPuestos(Long usuarioId, List<Long> puestoIds, Long adminId);

    int agregarPuestosAVendedores(List<Long> usuarioIds, List<Long> puestoIds, Long adminId);

    List<Long> findPuestoIdsByVendedor(Long usuarioId);

    List<Puesto> findPuestosAsignadosByVendedor(Long usuarioId);

    List<Puesto> findPuestosVisiblesParaVendedor(Long usuarioId);

    boolean tieneAlgunaAsignacion(Long usuarioId);

    boolean vendedorTienePuesto(Long usuarioId, Long puestoId);

    java.util.Set<Long> puestosPermitidosDe(Long usuarioId, java.util.List<Long> puestoIds);

    List<Object[]> findCatalogoAsignable();

    List<Object[]> nombresDeCategoriaPorPuesto(List<Long> puestoIds);

    List<Object[]> contarPuestosPorCategoriaYVendedor();

    void borrarAsignacionesDe(Long usuarioId);

    List<Object[]> findAsignacionesConVendedor();
}

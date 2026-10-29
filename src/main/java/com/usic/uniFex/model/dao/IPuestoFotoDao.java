package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.PuestoFoto;

public interface IPuestoFotoDao extends JpaRepository<PuestoFoto, Long> {

    @Query("SELECT f FROM PuestoFoto f WHERE f.puesto.id = :puestoId "
         + "AND (f.estado IS NULL OR f.estado <> 'X') ORDER BY f.orden ASC, f.id ASC")
    List<PuestoFoto> activasDe(@Param("puestoId") Long puestoId);

    @Query("SELECT count(f) FROM PuestoFoto f WHERE f.puesto.id = :puestoId "
         + "AND (f.estado IS NULL OR f.estado <> 'X')")
    long contarActivasDe(@Param("puestoId") Long puestoId);

    @Query("SELECT DISTINCT f.puesto.id FROM PuestoFoto f WHERE f.estado IS NULL OR f.estado <> 'X'")
    List<Long> idsDePuestosConFoto();

    @Modifying(clearAutomatically = true)
    @Query("UPDATE PuestoFoto f SET f.estado = 'X' WHERE f.id = :id")
    int marcarBorrada(@Param("id") Long id);
}

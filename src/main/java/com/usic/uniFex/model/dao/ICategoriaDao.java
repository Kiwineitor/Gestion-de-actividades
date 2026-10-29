package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.Categoria;

public interface ICategoriaDao extends JpaRepository <Categoria, Long>{

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE categoria SET \"_estado\" = 'X' WHERE id = :id", nativeQuery = true)
    int anular(@Param("id") Long id);

    @Query("SELECT c FROM Categoria c WHERE (c.estado IS NULL OR c.estado <> 'X') "
           + "AND c.idEdicion = (SELECT e.id FROM Edicion e WHERE e.activa = true) ORDER BY lower(c.nombre)")
    List<Categoria> deEdicionActiva();

    @Query("SELECT c FROM Categoria c WHERE (c.estado IS NULL OR c.estado <> 'X') AND c.idEdicion = :idEdicion ORDER BY c.id")
    List<Categoria> deEdicion(@Param("idEdicion") Long idEdicion);
}

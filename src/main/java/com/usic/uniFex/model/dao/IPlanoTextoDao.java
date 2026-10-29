package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.PlanoTexto;

public interface IPlanoTextoDao extends JpaRepository<PlanoTexto, Long> {

    @Query("SELECT t FROM PlanoTexto t WHERE t.estado <> 'X' "
         + "AND (:edicionId IS NULL OR t.edicion IS NULL OR t.edicion.id = :edicionId) "
         + "ORDER BY t.id")
    List<PlanoTexto> vivosDe(@Param("edicionId") Long edicionId);
}

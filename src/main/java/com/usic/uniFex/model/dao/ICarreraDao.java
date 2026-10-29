package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.usic.uniFex.model.entity.Carrera;

public interface ICarreraDao extends JpaRepository<Carrera, Long> {

    @Query("select c from Carrera c join fetch c.area a "
            + "where (c.estado is null or c.estado <> 'X') and (a.estado is null or a.estado <> 'X') "
            + "order by a.orden, a.sigla, c.nombre")
    List<Carrera> listarVivasConArea();
}

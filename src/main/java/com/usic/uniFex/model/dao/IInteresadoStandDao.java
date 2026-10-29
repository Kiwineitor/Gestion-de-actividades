package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.InteresadoStand;

public interface IInteresadoStandDao extends JpaRepository<InteresadoStand, Long> {

    @EntityGraph(attributePaths = "categoria")
    List<InteresadoStand> findAllByOrderByFechaRegistroDesc();
}

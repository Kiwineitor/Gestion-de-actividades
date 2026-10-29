package com.usic.uniFex.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.Edicion;

public interface IEdicionDao extends JpaRepository<Edicion, Long> {

    List<Edicion> findAllByOrderByAnioDesc();

    Optional<Edicion> findFirstByActivaTrueOrderByAnioDesc();
}

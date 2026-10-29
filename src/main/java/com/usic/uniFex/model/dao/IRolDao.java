package com.usic.uniFex.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.Rol;

public interface IRolDao extends JpaRepository<Rol, Long> {
    Optional<Rol> findByNombre(String nombre);

    Optional<Rol> findFirstByNombreIgnoreCase(String nombre);

    @Query("select r from Rol r where r.estado is null or r.estado <> 'ELIMINADO' order by r.nombre")
    List<Rol> listarVivos();

    @Query("select count(r) from Rol r where upper(r.nombre) = upper(:nombre) "
            + "and (r.estado is null or r.estado <> 'ELIMINADO') and r.id <> :exceptoId")
    long contarPorNombre(@Param("nombre") String nombre, @Param("exceptoId") Long exceptoId);
}

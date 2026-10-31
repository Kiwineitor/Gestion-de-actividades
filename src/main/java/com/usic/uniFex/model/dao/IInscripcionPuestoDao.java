package com.usic.uniFex.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.InscripcionPuesto;

public interface IInscripcionPuestoDao extends JpaRepository<InscripcionPuesto, Long>{

    @Query("select count(ip) from InscripcionPuesto ip where ip.inscripcion.id = :inscripcionId "
         + "and (ip.estado is null or ip.estado <> 'X')")
    int contarActivosDeInscripcion(@Param("inscripcionId") Long inscripcionId);

    @Query("select coalesce(sum(coalesce(c.personasPorPuesto, 2)), 0) from InscripcionPuesto ip "
         + "join ip.puesto p left join p.categoria c where ip.inscripcion.id = :inscripcionId "
         + "and (ip.estado is null or ip.estado <> 'X')")
    long personasIncluidasDeInscripcion(@Param("inscripcionId") Long inscripcionId);

    @Query("select max(c.costoPersonaExtra) from InscripcionPuesto ip "
         + "join ip.puesto p join p.categoria c where ip.inscripcion.id = :inscripcionId "
         + "and (ip.estado is null or ip.estado <> 'X')")
    java.math.BigDecimal costoPersonaExtraDeInscripcion(@Param("inscripcionId") Long inscripcionId);
}

package com.usic.uniFex.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.SolicitudCancelacion;

public interface ISolicitudCancelacionDao extends JpaRepository<SolicitudCancelacion, Long> {

    @Query("select s from SolicitudCancelacion s where s.idInscripcion = :idInscripcion "
            + "and s.estadoSolicitud = 'PENDIENTE' and (s.estado is null or s.estado <> 'X')")
    Optional<SolicitudCancelacion> pendienteDeInscripcion(@Param("idInscripcion") Long idInscripcion);

    @EntityGraph(attributePaths = { "inscripcion", "inscripcion.entidad", "inscripcion.entidad.tipoEntidad" })
    @Query("select s from SolicitudCancelacion s where s.estadoSolicitud = 'PENDIENTE' "
            + "and (s.estado is null or s.estado <> 'X') order by s.fechaSolicitud asc")
    List<SolicitudCancelacion> pendientes();

    @EntityGraph(attributePaths = { "inscripcion", "inscripcion.entidad", "inscripcion.entidad.tipoEntidad" })
    @Query("select s from SolicitudCancelacion s where s.estadoSolicitud <> 'PENDIENTE' "
            + "and (s.estado is null or s.estado <> 'X') order by s.fechaResolucion desc")
    List<SolicitudCancelacion> resueltas();

    @Query("select s from SolicitudCancelacion s where s.idInscripcion = :idInscripcion "
            + "and s.estadoSolicitud = 'APROBADA' and (s.estado is null or s.estado <> 'X') "
            + "order by s.fechaResolucion desc")
    Optional<SolicitudCancelacion> aprobadaDeInscripcion(@Param("idInscripcion") Long idInscripcion);

    @Query("select s from SolicitudCancelacion s where s.registroIdUsuario = :usuarioId "
            + "and (s.estado is null or s.estado <> 'X') order by s.fechaSolicitud desc")
    List<SolicitudCancelacion> delVendedor(@Param("usuarioId") Long usuarioId);

    @Query("select s from SolicitudCancelacion s where s.idInscripcion = :idInscripcion "
            + "and (s.estado is null or s.estado <> 'X') order by s.fechaSolicitud desc")
    java.util.Optional<SolicitudCancelacion> ultimaDeInscripcion(@Param("idInscripcion") Long idInscripcion);

    @Modifying(clearAutomatically = true)
    @Query("update SolicitudCancelacion s set s.estadoSolicitud = 'APROBADA', s.respuesta = :respuesta, "
            + "s.fechaResolucion = current_timestamp, s.modificacionIdUsuario = :usuarioId "
            + "where s.id = :id and s.estadoSolicitud = 'PENDIENTE'")
    int aprobarSiPendiente(@Param("id") Long id, @Param("respuesta") String respuesta,
                           @Param("usuarioId") Long usuarioId);

    @Modifying(clearAutomatically = true)
    @Query("update SolicitudCancelacion s set s.estadoSolicitud = 'RECHAZADA', s.respuesta = :respuesta, "
            + "s.fechaResolucion = current_timestamp, s.modificacionIdUsuario = :usuarioId "
            + "where s.id = :id and s.estadoSolicitud = 'PENDIENTE'")
    int rechazarSiPendiente(@Param("id") Long id, @Param("respuesta") String respuesta,
                            @Param("usuarioId") Long usuarioId);
}

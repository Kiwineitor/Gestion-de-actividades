package com.usic.uniFex.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.dto.CredencialFilaView;
import com.usic.uniFex.model.entity.Responsable;

public interface ICredencialDao extends JpaRepository<Responsable, Long> {

String SELECT = """
            SELECT r.id                                   AS responsableId,
                   p.nombre                               AS nombre,
                   p.paterno                              AS paterno,
                   p.materno                              AS materno,
                   p.ci                                   AS ci,
                   p.foto                                 AS foto,
                   r.es_titular                           AS esTitular,
                   r.cargo                                AS cargo,
                   r.es_extra                             AS esExtra,
                   r.monto_extra                          AS montoExtra,
                   r.comprobante_extra                    AS comprobanteExtra,
                   e.nombre                               AS entidad,
                   e.descripcion                          AS rubro,
                   i.id                                   AS inscripcionId,
                   i.pago_contado                         AS pagoContado,
                   COALESCE(NULLIF(i.img_comprobante, ''), CASE WHEN i.cobro_efectivo THEN 'efectivo' END) AS comprobante,
                   COALESCE(SUM(ip.costo), 0)             AS totalVenta,
                   string_agg(DISTINCT c.nombre, ', ')    AS categorias,
                   string_agg(DISTINCT pu.codigo, ', ')   AS casetas,
                   MAX(c.id)                              AS categoriaId
             FROM responsable r
             INNER JOIN persona p    ON p.id = r.id_persona
             INNER JOIN entidad e    ON e.id = r.id_entidad
             INNER JOIN inscripcion i ON i.id_entidad = e.id
                                       AND (i."_estado" IS NULL OR i."_estado" <> 'X')
             LEFT  JOIN inscripcion_puesto ip ON ip.id_inscripcion = i.id
                                       AND (ip."_estado" IS NULL OR ip."_estado" <> 'X')
             LEFT  JOIN puesto pu    ON pu.id = ip.id_puesto
             LEFT  JOIN categoria c  ON c.id = pu.id_categoria
            WHERE (r."_estado" IS NULL OR r."_estado" <> 'X')
              AND (e."_estado" IS NULL OR e."_estado" <> 'X')
            """;

    String GROUP = """
             GROUP BY r.id, p.nombre, p.paterno, p.materno, p.ci, p.foto, r.es_titular, r.cargo,
                      r.es_extra, r.monto_extra, r.comprobante_extra,
                      e.nombre, e.descripcion, i.id, i.pago_contado, i.img_comprobante, i.cobro_efectivo,
                      c.id
             """;

    @Query(value = SELECT
            + " AND i.id_edicion = coalesce(cast(:edicion as bigint), (SELECT ed.id FROM edicion ed WHERE ed.activa LIMIT 1)) "
            + GROUP + " ORDER BY e.nombre, r.es_titular DESC, p.nombre",
            nativeQuery = true)
    List<CredencialFilaView> listarDeEdicion(@Param("edicion") Long edicion);

    @Query(value = SELECT
            + " AND i.id_edicion = coalesce(cast(:edicion as bigint), (SELECT ed.id FROM edicion ed WHERE ed.activa LIMIT 1)) "
            + " AND i.\"_registro_id_usuario\" = :usuarioId "
            + GROUP + " ORDER BY e.nombre, r.es_titular DESC, p.nombre",
            nativeQuery = true)
    List<CredencialFilaView> listarDeEdicionDe(@Param("usuarioId") Long usuarioId, @Param("edicion") Long edicion);

    @Query(value = SELECT + " AND r.id = :responsableId " + GROUP, nativeQuery = true)
    Optional<CredencialFilaView> buscarPorResponsable(@Param("responsableId") Long responsableId);

    @Query(value = "SELECT COUNT(*) > 0"
            + "   FROM responsable r"
            + "   INNER JOIN entidad e     ON e.id = r.id_entidad"
            + "   INNER JOIN inscripcion i ON i.id_entidad = e.id"
            + "                           AND (i.\"_estado\" IS NULL OR i.\"_estado\" <> 'X')"
            + "  WHERE r.id = :responsableId"
            + "    AND i.\"_registro_id_usuario\" = :usuarioId",
            nativeQuery = true)
    boolean esDeUsuario(@Param("responsableId") Long responsableId,
                        @Param("usuarioId") Long usuarioId);
}

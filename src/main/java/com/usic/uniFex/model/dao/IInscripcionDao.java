package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.dto.ResumenCategoriaView;
import com.usic.uniFex.model.dto.ResumenEntidadView;
import com.usic.uniFex.model.dto.ResumenGeneralView;
import com.usic.uniFex.model.entity.Inscripcion;

public interface IInscripcionDao extends JpaRepository <Inscripcion, Long> {

    @EntityGraph(attributePaths = { "entidad" })
    java.util.Optional<Inscripcion> findByNotaCodigo(String notaCodigo);
    @EntityGraph(attributePaths = {
        "entidad", "entidad.tipoEntidad",
        "inscripcionPuestos",
        "inscripcionPuestos.puesto",
        "inscripcionPuestos.puesto.categoria"
    })
    List<Inscripcion> findAll();

    @Query(value = """
            select
            u.id                         as usuarioId,
            concat_ws(' ', per.nombre, per.paterno, per.materno) as nombreCompleto,
            c.id                         as categoriaId,
            c.nombre                     as categoria,
            count(distinct i.id)         as cantidadInscripciones,
            count(ip.id)                 as cantidadPuestos,
            coalesce(sum(ip.costo), 0)   as totalBs
            from inscripcion i
            join inscripcion_puesto ip on ip.id_inscripcion = i.id
            join puesto p             on p.id = ip.id_puesto
            join categoria c          on c.id = p.id_categoria
            left join usuario u       on u.id = i._registro_id_usuario
            left join persona per     on per.id = u.persona_id
            where i."_estado" <> 'X'
              and i.id_edicion = (select ed.id from edicion ed where ed.activa limit 1)
            group by u.id, per.nombre, per.paterno, per.materno, c.id, c.nombre
            order by nombreCompleto asc nulls last, c.nombre asc
            """, nativeQuery = true)
    List<ResumenCategoriaView> resumenPorCategoria();

    @Query(value = """
            select
            u.id                         as usuarioId,
            concat_ws(' ', per.nombre, per.paterno, per.materno) as nombreCompleto,
            e.id                         as entidadId,
            e.nombre                     as entidad,
            count(distinct i.id)         as cantidadInscripciones,
            count(ip.id)                 as cantidadPuestos,
            coalesce(sum(ip.costo), 0)   as totalBs
            from inscripcion i
            join entidad e           on e.id = i.id_entidad
            left join usuario u      on u.id = i._registro_id_usuario
            left join persona per    on per.id = u.persona_id
            left join inscripcion_puesto ip on ip.id_inscripcion = i.id and ip.id_puesto is not null
            where i."_estado" <> 'X'
              and i.id_edicion = (select ed.id from edicion ed where ed.activa limit 1)
            group by u.id, per.nombre, per.paterno, per.materno, e.id, e.nombre
            order by nombreCompleto asc nulls last, e.nombre asc
            """, nativeQuery = true)
    List<ResumenEntidadView> resumenPorEntidad();

    @EntityGraph(attributePaths = {
        "entidad",
        "entidad.tipoEntidad",
        "inscripcionPuestos",
        "inscripcionPuestos.puesto",
        "inscripcionPuestos.puesto.categoria",
        "registroUsuario",
        "registroUsuario.persona",
        "canceladaPorUsuario",
        "canceladaPorUsuario.persona"
    })
    @Query("select i from Inscripcion i where i.estado is null or i.estado <> 'X'")
    List<Inscripcion> findAllConTodo();

    @EntityGraph(attributePaths = {
        "entidad",
        "entidad.tipoEntidad",
        "inscripcionPuestos",
        "inscripcionPuestos.puesto",
        "inscripcionPuestos.puesto.categoria",
        "registroUsuario",
        "registroUsuario.persona",
        "canceladaPorUsuario",
        "canceladaPorUsuario.persona"
    })
    @Query("select i from Inscripcion i where i.estado = 'X'")
    List<Inscripcion> findAllCanceladas();

    @EntityGraph(attributePaths = {
        "entidad",
        "entidad.tipoEntidad",
        "inscripcionPuestos",
        "inscripcionPuestos.puesto",
        "inscripcionPuestos.puesto.categoria",
        "registroUsuario",
        "registroUsuario.persona",
        "canceladaPorUsuario",
        "canceladaPorUsuario.persona",
        "edicion"
    })
    @Query("select i from Inscripcion i where i.id = :id")
    java.util.Optional<Inscripcion> findConTodoPorId(@Param("id") Long id);

    @Query(value = """
            select
            count(distinct i.id)         as inscripciones,
            count(ip.id)                 as puestos,
            coalesce(sum(ip.costo), 0)   as totalBs
            from inscripcion i
            join inscripcion_puesto ip on ip.id_inscripcion = i.id
            join puesto p              on p.id = ip.id_puesto
            where i."_estado" <> 'X'
              and i.id_edicion = (select ed.id from edicion ed where ed.activa limit 1)
            """, nativeQuery = true)
    ResumenGeneralView resumenGeneral();

    @Query("""
           select i from Inscripcion i
            where i.registroIdUsuario = :usuarioId
              and (i.estado is null or i.estado <> 'X')
              and (i.imgComprobante is null or i.imgComprobante = '')
            order by i.fechaCompra desc
           """)
    List<Inscripcion> pendientesDeComprobante(@Param("usuarioId") Long usuarioId);

@Query(value = """
            SELECT i.id                                                         AS id,
                   (i.img_comprobante IS NOT NULL AND i.img_comprobante <> '')  AS con_comprobante,
                   COALESCE(SUM(CASE WHEN r.id IS NOT NULL
                                        AND (p.foto IS NULL OR p.foto = '')
                                   THEN 1 ELSE 0 END), 0)                     AS sin_foto
             FROM inscripcion i
             LEFT JOIN responsable r ON r.id_entidad = i.id_entidad
                                        AND (r."_estado" IS NULL OR r."_estado" <> 'X')
             LEFT JOIN persona p     ON p.id = r.id_persona
            WHERE (:usuarioId IS NULL OR i."_registro_id_usuario" = :usuarioId)
              AND (i."_estado" IS NULL OR i."_estado" <> 'X')
            GROUP BY i.id, i.img_comprobante
           """, nativeQuery = true)
    List<Object[]> pendientesPorVenta(@Param("usuarioId") Long usuarioId);

}

package com.usic.uniFex.model.dao;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.Edicion;
import com.usic.uniFex.model.entity.Puesto;

public interface IPuestoDao extends JpaRepository <Puesto, Long>{
    @EntityGraph(attributePaths = {"categoria"})
    List<Puesto> findAll();

    @EntityGraph(attributePaths = {"categoria"})
    List<Puesto> findWithCategoriaByIdIn(Collection<Long> ids);

    @Query("SELECT p FROM Puesto p WHERE p.estadoPuesto = 'L' AND (p.estado IS NULL OR p.estado <> 'X') "
           + "AND p.categoria.idEdicion = (SELECT e.id FROM Edicion e WHERE e.activa = true) ORDER BY p.codigo ASC")
    List<Puesto> listarPuestos();

    @Query("SELECT p FROM Puesto p " +
       "WHERE p.estadoPuesto = :estadoPuesto AND p.categoria.id = :categoriaId " +
       "ORDER BY CAST(p.codigo AS integer) ASC")
List<Puesto> findLibresPorCategoriaOrdenados(@Param("estadoPuesto") String estadoPuesto,
                                             @Param("categoriaId") Long categoriaId);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET estado_puesto = 'T', " +
           "reservado_por_id_usuario = :usuarioId, " +
           "reserva_expira = now() + (:ttlSegundos * interval '1 second'), " +
           "version = version + 1 " +
           "WHERE id = :id AND estado_puesto = 'L' " +
           "AND id_categoria IN " + Edicion.SQL_CATEGORIAS_ACTIVAS, nativeQuery = true)
    int reservarSiLibre(@Param("id") Long id,
                        @Param("usuarioId") Long usuarioId,
                        @Param("ttlSegundos") long ttlSegundos);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET estado_puesto = 'T', " +
           "reservado_por_id_usuario = :usuarioId, " +
           "reserva_expira = now() + (:ttlSegundos * interval '1 second'), " +
           "version = version + 1 " +
           "WHERE id = :id AND (estado_puesto = 'L' " +
           "  OR (estado_puesto = 'T' AND reservado_por_id_usuario = :usuarioId)) " +
           "AND id_categoria IN " + Edicion.SQL_CATEGORIAS_ACTIVAS,
           nativeQuery = true)
    int reservarSiLibreOMia(@Param("id") Long id,
                            @Param("usuarioId") Long usuarioId,
                            @Param("ttlSegundos") long ttlSegundos);

    @EntityGraph(attributePaths = {"categoria"})
    @Query("SELECT p FROM Puesto p WHERE p.estadoPuesto = 'T' AND p.reservadoPorIdUsuario = :usuarioId "
         + "ORDER BY p.categoria.nombre ASC, p.id ASC")
    List<Puesto> reservadasPor(@Param("usuarioId") Long usuarioId);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET estado_puesto = 'L', " +
           "reservado_por_id_usuario = NULL, reserva_expira = NULL, version = version + 1 " +
           "WHERE id = :id AND estado_puesto = 'T' AND reservado_por_id_usuario = :usuarioId",
           nativeQuery = true)
    int liberarSiEnTramite(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET estado_puesto = 'O', " +
           "reserva_expira = NULL, version = version + 1 " +
           "WHERE id = :id AND estado_puesto = 'T' AND reservado_por_id_usuario = :usuarioId",
           nativeQuery = true)
    int confirmarSiEnTramite(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET estado_puesto = 'O', " +
           "reservado_por_id_usuario = NULL, reserva_expira = NULL, version = version + 1, " +
           "\"_fecha_modificacion\" = now(), \"_modificacion_id_usuario\" = :usuarioId " +
           "WHERE id = :id AND (estado_puesto = 'L' " +
           "  OR (estado_puesto = 'T' AND reservado_por_id_usuario = :usuarioId)) " +
           "AND id_categoria IN " + Edicion.SQL_CATEGORIAS_ACTIVAS,
           nativeQuery = true)
    int ocuparSiDisponible(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET estado_puesto = 'L', " +
           "reservado_por_id_usuario = NULL, reserva_expira = NULL, version = version + 1 " +
           "WHERE estado_puesto = 'T' AND reserva_expira < now()", nativeQuery = true)
    int liberarReservasVencidas();

    @Query(value = "SELECT id FROM puesto WHERE estado_puesto = 'T' AND reserva_expira < now()",
           nativeQuery = true)
    List<Long> idsReservasVencidas();

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET estado_puesto = 'L', " +
           "reservado_por_id_usuario = NULL, reserva_expira = NULL, version = version + 1, " +
           "\"_fecha_modificacion\" = now(), \"_modificacion_id_usuario\" = :usuarioId " +
           "WHERE id = :id AND estado_puesto = 'O'", nativeQuery = true)
    int liberarSiOcupado(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    @EntityGraph(attributePaths = {"categoria"})
    @Query("SELECT p FROM Puesto p WHERE (p.estado IS NULL OR p.estado <> 'X') "
           + "AND p.categoria.idEdicion = (SELECT e.id FROM Edicion e WHERE e.activa = true)")
    List<Puesto> listarActivos();

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET mapa_x = :x, mapa_y = :y, " +
           "mapa_escala = COALESCE(:escala, mapa_escala), " +
           "mapa_rotacion = COALESCE(:rotacion, mapa_rotacion) " +
           "WHERE id = :id AND (\"_estado\" IS NULL OR \"_estado\" <> 'X')", nativeQuery = true)
    int actualizarPosicion(@Param("id") Long id, @Param("x") Double x, @Param("y") Double y,
                           @Param("escala") Double escala, @Param("rotacion") Integer rotacion);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET \"_estado\" = 'X', estado_puesto = 'X', " +
           "mapa_x = NULL, mapa_y = NULL, version = version + 1, " +
           "\"_fecha_modificacion\" = now(), \"_modificacion_id_usuario\" = :usuarioId " +
           "WHERE id = :id AND estado_puesto = 'L' " +
           "  AND (\"_estado\" IS NULL OR \"_estado\" <> 'X') " +
           "  AND NOT EXISTS (SELECT 1 FROM inscripcion_puesto ip WHERE ip.id_puesto = puesto.id)",
           nativeQuery = true)
    int anularSiLibreYSinVentas(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET estado_puesto = 'X', version = version + 1, " +
           "\"_fecha_modificacion\" = now(), \"_modificacion_id_usuario\" = :usuarioId " +
           "WHERE id = :id AND estado_puesto = 'L' " +
           "  AND (\"_estado\" IS NULL OR \"_estado\" <> 'X')", nativeQuery = true)
    int bloquearSiLibre(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET estado_puesto = 'L', version = version + 1, " +
           "\"_fecha_modificacion\" = now(), \"_modificacion_id_usuario\" = :usuarioId " +
           "WHERE id = :id AND estado_puesto = 'X' " +
           "  AND (\"_estado\" IS NULL OR \"_estado\" <> 'X')", nativeQuery = true)
    int desbloquearSiBloqueada(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET referencia = :referencia, "
           + "\"_fecha_modificacion\" = now(), \"_modificacion_id_usuario\" = :usuarioId "
           + "WHERE id = :id AND (\"_estado\" IS NULL OR \"_estado\" <> 'X')", nativeQuery = true)
    int actualizarReferencia(@Param("id") Long id,
                             @Param("referencia") String referencia,
                             @Param("usuarioId") Long usuarioId);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET tamano = :tamano, observacion = :observacion, referencia = :referencia, "
           + "\"_fecha_modificacion\" = now(), \"_modificacion_id_usuario\" = :usuarioId "
           + "WHERE id = :id AND (\"_estado\" IS NULL OR \"_estado\" <> 'X')", nativeQuery = true)
    int actualizarFicha(@Param("id") Long id,
                        @Param("tamano") String tamano,
                        @Param("observacion") String observacion,
                        @Param("referencia") String referencia,
                        @Param("usuarioId") Long usuarioId);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET tamano = :tamano "
           + "WHERE id_categoria = :categoriaId AND estado_puesto IN ('L', 'X') "
           + "AND (\"_estado\" IS NULL OR \"_estado\" <> 'X')", nativeQuery = true)
    int actualizarTamanoDeCategoria(@Param("categoriaId") Long categoriaId,
                                    @Param("tamano") String tamano);

    @Query(value = """
            SELECT i.id, e.nombre, e.objeto, e.representante_legal, e.celular_representante,
                   i.img_comprobante, i.pago_contado, i.fecha_compra,
                   pe.nombre, pe.paterno, pe.materno, ip.costo
              FROM inscripcion_puesto ip
              INNER JOIN inscripcion i ON i.id = ip.id_inscripcion
              LEFT JOIN entidad e ON e.id = i.id_entidad
              LEFT JOIN usuario u ON u.id = i."_registro_id_usuario"
              LEFT JOIN persona pe ON pe.id = u.persona_id
             WHERE ip.id_puesto = :puestoId
               AND (i."_estado" IS NULL OR i."_estado" <> 'X')
               AND (ip."_estado" IS NULL OR ip."_estado" <> 'X')
               AND i.id_edicion = (SELECT ed.id FROM edicion ed WHERE ed.activa LIMIT 1)
             ORDER BY i.fecha_compra DESC NULLS LAST
             LIMIT 1
            """, nativeQuery = true)
    List<Object[]> findVentaVigenteDePuesto(@Param("puestoId") Long puestoId);

    @Query(value = """
            SELECT DISTINCT ip.id_puesto
              FROM inscripcion_puesto ip
              INNER JOIN inscripcion i ON i.id = ip.id_inscripcion
              INNER JOIN puesto pu ON pu.id = ip.id_puesto AND pu.estado_puesto = 'O'
             WHERE ip.id_puesto IS NOT NULL
               AND i.img_comprobante IS NOT NULL AND btrim(i.img_comprobante) <> ''
               AND (i."_estado" IS NULL OR i."_estado" <> 'X')
               AND (ip."_estado" IS NULL OR ip."_estado" <> 'X')
               AND i.id_edicion = (SELECT ed.id FROM edicion ed WHERE ed.activa LIMIT 1)
            """, nativeQuery = true)
    List<Long> findPuestosPagados();

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET id_categoria = :categoriaId, codigo = :codigo, version = version + 1, "
           + "\"_fecha_modificacion\" = now(), \"_modificacion_id_usuario\" = :usuarioId "
           + "WHERE id = :id AND estado_puesto IN ('L', 'X') "
           + "AND (\"_estado\" IS NULL OR \"_estado\" <> 'X')", nativeQuery = true)
    int cambiarCategoriaSiLibre(@Param("id") Long id,
                                @Param("categoriaId") Long categoriaId,
                                @Param("codigo") String codigo,
                                @Param("usuarioId") Long usuarioId);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET codigo = :codigo, "
           + "\"_fecha_modificacion\" = now(), \"_modificacion_id_usuario\" = :usuarioId "
           + "WHERE id = :id AND (\"_estado\" IS NULL OR \"_estado\" <> 'X')", nativeQuery = true)
    int actualizarCodigo(@Param("id") Long id,
                         @Param("codigo") String codigo,
                         @Param("usuarioId") Long usuarioId);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET precio = CAST(:precio AS numeric), "
           + "\"_fecha_modificacion\" = now(), \"_modificacion_id_usuario\" = :usuarioId "
           + "WHERE id = :id AND (\"_estado\" IS NULL OR \"_estado\" <> 'X')", nativeQuery = true)
    int actualizarPrecio(@Param("id") Long id,
                         @Param("precio") java.math.BigDecimal precio,
                         @Param("usuarioId") Long usuarioId);

    @Query(value = "SELECT p.id FROM puesto p WHERE p.id IN (:ids) "
           + "AND (p.estado_puesto = 'O' "
           + "     OR EXISTS (SELECT 1 FROM inscripcion_puesto ip WHERE ip.id_puesto = p.id))",
           nativeQuery = true)
    List<Long> idsConVentas(@Param("ids") Collection<Long> ids);

    @Query("SELECT p.id FROM Puesto p WHERE p.categoria.id = :categoriaId " +
           "AND (p.estado IS NULL OR p.estado <> 'X')")
    List<Long> idsActivosDeCategoria(@Param("categoriaId") Long categoriaId);

    @Query("SELECT p.tamano FROM Puesto p WHERE p.categoria.id = :categoriaId " +
           "AND (p.estado IS NULL OR p.estado <> 'X') AND p.tamano IS NOT NULL ORDER BY p.id")
    List<String> tamanosDeCategoria(@Param("categoriaId") Long categoriaId);

    @Query("SELECT p FROM Puesto p WHERE p.categoria.id = :categoriaId " +
           "AND (p.estado IS NULL OR p.estado <> 'X')")
    List<Puesto> activosDeCategoria(@Param("categoriaId") Long categoriaId);

    @Query(value = "SELECT count(*) FROM puesto p WHERE p.id_categoria = :categoriaId " +
           "AND (p.\"_estado\" IS NULL OR p.\"_estado\" <> 'X') " +
           "AND (p.estado_puesto <> 'L' " +
           "     OR EXISTS (SELECT 1 FROM inscripcion_puesto ip WHERE ip.id_puesto = p.id))",
           nativeQuery = true)
    long contarNoEliminablesDeCategoria(@Param("categoriaId") Long categoriaId);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET \"_estado\" = 'X', estado_puesto = 'X', " +
           "mapa_x = NULL, mapa_y = NULL, version = version + 1, " +
           "\"_fecha_modificacion\" = now(), \"_modificacion_id_usuario\" = :usuarioId " +
           "WHERE id_categoria = :categoriaId AND (\"_estado\" IS NULL OR \"_estado\" <> 'X')",
           nativeQuery = true)
    int anularCasetasDeCategoria(@Param("categoriaId") Long categoriaId, @Param("usuarioId") Long usuarioId);

    @Query(value = """
            SELECT p.id, u.id, pe.nombre, pe.paterno, pe.materno, pe.celular, u.username,
                   'T', NULL::timestamp
              FROM puesto p
              INNER JOIN usuario u ON u.id = p.reservado_por_id_usuario
              LEFT JOIN persona pe ON pe.id = u.persona_id
             WHERE p.estado_puesto = 'T'
            UNION ALL
            SELECT v.id_puesto, u.id, pe.nombre, pe.paterno, pe.materno, pe.celular, u.username,
                   'O', v.fecha_compra
              FROM (
                    SELECT DISTINCT ON (ip.id_puesto)
                           ip.id_puesto, i."_registro_id_usuario" AS id_usuario, i.fecha_compra
                      FROM inscripcion_puesto ip
                      INNER JOIN inscripcion i ON i.id = ip.id_inscripcion
                      INNER JOIN puesto pu ON pu.id = ip.id_puesto AND pu.estado_puesto = 'O'
                     WHERE ip.id_puesto IS NOT NULL
                       AND (i."_estado" IS NULL OR i."_estado" <> 'X')
                       AND (ip."_estado" IS NULL OR ip."_estado" <> 'X')
                       AND i.id_edicion = (SELECT ed.id FROM edicion ed WHERE ed.activa LIMIT 1)
                     ORDER BY ip.id_puesto, i.fecha_compra DESC NULLS LAST
                   ) v
              INNER JOIN usuario u ON u.id = v.id_usuario
              LEFT JOIN persona pe ON pe.id = u.persona_id
            """, nativeQuery = true)
    List<Object[]> findOcupacionConVendedor();

    @Query(value = """
            SELECT i."_registro_id_usuario", COUNT(*)
              FROM inscripcion_puesto ip
              INNER JOIN inscripcion i ON i.id = ip.id_inscripcion
              INNER JOIN puesto pu ON pu.id = ip.id_puesto AND pu.estado_puesto = 'O'
             WHERE ip.id_puesto IS NOT NULL
               AND i."_registro_id_usuario" IS NOT NULL
               AND (i."_estado" IS NULL OR i."_estado" <> 'X')
               AND (ip."_estado" IS NULL OR ip."_estado" <> 'X')
               AND i.id_edicion = (SELECT ed.id FROM edicion ed WHERE ed.activa LIMIT 1)
             GROUP BY i."_registro_id_usuario"
            """, nativeQuery = true)
    List<Object[]> contarVendidasPorVendedor();
}

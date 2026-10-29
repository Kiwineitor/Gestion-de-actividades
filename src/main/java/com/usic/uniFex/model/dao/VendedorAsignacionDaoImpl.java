package com.usic.uniFex.model.dao;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import org.springframework.stereotype.Repository;

import com.usic.uniFex.model.entity.Puesto;

@Repository
public class VendedorAsignacionDaoImpl implements IVendedorAsignacionDao {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void asignarPuesto(Long usuarioId, Long puestoId, Long adminId) {
        Query q = em.createNativeQuery(
                "INSERT INTO vendedor_puesto (id_usuario, id_puesto, _registro_id_usuario) VALUES (:usuarioId, :puestoId, :adminId) ON CONFLICT DO NOTHING");
        q.setParameter("usuarioId", usuarioId);
        q.setParameter("puestoId", puestoId);
        q.setParameter("adminId", adminId);
        q.executeUpdate();
    }

    @Override
    public void quitarPuesto(Long usuarioId, Long puestoId) {
        Query q = em.createNativeQuery(
                "DELETE FROM vendedor_puesto WHERE id_usuario = :usuarioId AND id_puesto = :puestoId");
        q.setParameter("usuarioId", usuarioId);
        q.setParameter("puestoId", puestoId);
        q.executeUpdate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Long> findPuestoIdsByVendedor(Long usuarioId) {
        return em.createNativeQuery("SELECT id_puesto FROM vendedor_puesto WHERE id_usuario = :usuarioId")
                .setParameter("usuarioId", usuarioId)
                .getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Puesto> findPuestosVisiblesParaVendedor(Long usuarioId) {
        return em.createNativeQuery("""
                        SELECT p.* FROM puesto p
                        INNER JOIN categoria c ON c.id = p.id_categoria AND c._estado <> 'X'
                        AND c.id_edicion = (select ed.id from edicion ed where ed.activa limit 1)
                              AND c.id_edicion = (select ed.id from edicion ed where ed.activa limit 1)
                        INNER JOIN vendedor_puesto vp ON vp.id_puesto = p.id AND vp.id_usuario = :usuarioId
                        WHERE p._estado <> 'X'
                          AND p.estado_puesto <> 'X'
                        ORDER BY c.nombre, length(p.codigo), p.codigo
                        """, Puesto.class)
                .setParameter("usuarioId", usuarioId)
                .getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> findCatalogoAsignable() {
        return em.createNativeQuery("""
                SELECT p.id, p.codigo, c.id, c.nombre, p.estado_puesto,
                       vp.id_usuario, u.username
                  FROM puesto p
                  INNER JOIN categoria c ON c.id = p.id_categoria AND c._estado <> 'X'
                        AND c.id_edicion = (select ed.id from edicion ed where ed.activa limit 1)
                  LEFT JOIN vendedor_puesto vp ON vp.id_puesto = p.id
                  LEFT JOIN usuario u ON u.id = vp.id_usuario
                 WHERE p._estado <> 'X' AND p.estado_puesto <> 'X'
                 ORDER BY c.nombre, length(p.codigo), p.codigo, u.username
                """).getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> nombresDeCategoriaPorPuesto(List<Long> puestoIds) {
        if (puestoIds == null || puestoIds.isEmpty()) return List.of();
        return em.createNativeQuery("""
                        SELECT p.id, c.nombre
                          FROM puesto p
                          INNER JOIN categoria c ON c.id = p.id_categoria
                         WHERE p.id IN (:ids)
                        """)
                .setParameter("ids", puestoIds)
                .getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> contarPuestosPorCategoriaYVendedor() {
        return em.createNativeQuery("""
                SELECT vp.id_usuario, c.id, c.nombre, COUNT(*)
                  FROM vendedor_puesto vp
                  INNER JOIN puesto p    ON p.id = vp.id_puesto
                                        AND p._estado <> 'X' AND p.estado_puesto <> 'X'
                  INNER JOIN categoria c ON c.id = p.id_categoria AND c._estado <> 'X'
                        AND c.id_edicion = (select ed.id from edicion ed where ed.activa limit 1)
                 GROUP BY vp.id_usuario, c.id, c.nombre
                 ORDER BY vp.id_usuario, c.nombre
                """).getResultList();
    }

    @Override
    public void borrarAsignacionesDe(Long usuarioId) {
        em.createNativeQuery("DELETE FROM vendedor_puesto WHERE id_usuario = :usuarioId")
                .setParameter("usuarioId", usuarioId).executeUpdate();
    }

    @Override
    public void reemplazarPuestos(Long usuarioId, List<Long> puestoIds, Long adminId) {
        if (puestoIds == null || puestoIds.isEmpty()) {
            em.createNativeQuery("DELETE FROM vendedor_puesto WHERE id_usuario = :usuarioId")
                    .setParameter("usuarioId", usuarioId)
                    .executeUpdate();
            return;
        }
        em.createNativeQuery("""
                DELETE FROM vendedor_puesto
                 WHERE id_usuario = :usuarioId AND id_puesto NOT IN (:ids)
                """)
                .setParameter("usuarioId", usuarioId)
                .setParameter("ids", puestoIds)
                .executeUpdate();

        em.createNativeQuery("""
                INSERT INTO vendedor_puesto (id_usuario, id_puesto, _registro_id_usuario)
                SELECT :usuarioId, p.id, :adminId
                  FROM puesto p
                 WHERE p.id IN (:ids)
                ON CONFLICT DO NOTHING
                """)
                .setParameter("usuarioId", usuarioId)
                .setParameter("adminId", adminId)
                .setParameter("ids", puestoIds)
                .executeUpdate();
    }

    @Override
    public int agregarPuestosAVendedores(List<Long> usuarioIds, List<Long> puestoIds, Long adminId) {
        if (usuarioIds == null || usuarioIds.isEmpty() || puestoIds == null || puestoIds.isEmpty()) return 0;
        return em.createNativeQuery("""
                INSERT INTO vendedor_puesto (id_usuario, id_puesto, _registro_id_usuario)
                SELECT u.id, p.id, :adminId
                  FROM usuario u
                  CROSS JOIN puesto p
                  INNER JOIN categoria c ON c.id = p.id_categoria AND c._estado <> 'X'
                        AND c.id_edicion = (select ed.id from edicion ed where ed.activa limit 1)
                 WHERE u.id IN (:usuarioIds)
                   AND p.id IN (:puestoIds)
                   AND p._estado <> 'X'
                   AND p.estado_puesto <> 'X'
                 ON CONFLICT DO NOTHING
                """)
                .setParameter("usuarioIds", usuarioIds)
                .setParameter("puestoIds", puestoIds)
                .setParameter("adminId", adminId)
                .executeUpdate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Puesto> findPuestosAsignadosByVendedor(Long usuarioId) {
        return em.createNativeQuery("""
                        SELECT p.* FROM puesto p
                        INNER JOIN vendedor_puesto vp ON vp.id_puesto = p.id
                        WHERE vp.id_usuario = :usuarioId AND p._estado <> 'X'
                        ORDER BY p.codigo
                        """, Puesto.class)
                .setParameter("usuarioId", usuarioId)
                .getResultList();
    }

    @Override
    public boolean tieneAlgunaAsignacion(Long usuarioId) {
        Query q = em.createNativeQuery(
                "SELECT 1 FROM vendedor_puesto WHERE id_usuario = :usuarioId LIMIT 1");
        q.setParameter("usuarioId", usuarioId);
        return !q.getResultList().isEmpty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> findAsignacionesConVendedor() {
        Query q = em.createNativeQuery("""
                SELECT vp.id_puesto, u.id, pe.nombre, pe.paterno, pe.materno, pe.celular, u.username
                FROM vendedor_puesto vp
                INNER JOIN usuario u ON u.id = vp.id_usuario
                LEFT JOIN persona pe ON pe.id = u.persona_id
                WHERE (u."_estado" IS NULL OR u."_estado" <> 'ELIMINADO')
                """);
        return q.getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public java.util.Set<Long> puestosPermitidosDe(Long usuarioId, List<Long> puestoIds) {
        if (usuarioId == null || puestoIds == null || puestoIds.isEmpty()) return java.util.Set.of();
        List<Object> filas = em.createNativeQuery(
                "SELECT id_puesto FROM vendedor_puesto WHERE id_usuario = :usuarioId AND id_puesto IN (:ids)")
                .setParameter("usuarioId", usuarioId)
                .setParameter("ids", puestoIds)
                .getResultList();
        java.util.Set<Long> permitidas = new java.util.HashSet<>();
        for (Object f : filas) {
            if (f instanceof Number n) permitidas.add(n.longValue());
        }
        return permitidas;
    }

    @Override
    public boolean vendedorTienePuesto(Long usuarioId, Long puestoId) {
        Query q = em.createNativeQuery(
                "SELECT 1 FROM vendedor_puesto WHERE id_usuario = :usuarioId AND id_puesto = :puestoId");
        q.setParameter("usuarioId", usuarioId);
        q.setParameter("puestoId", puestoId);
        return !q.getResultList().isEmpty();
    }
}

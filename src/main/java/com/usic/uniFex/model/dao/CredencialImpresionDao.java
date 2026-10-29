package com.usic.uniFex.model.dao;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CredencialImpresionDao {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void registrar(Long responsableId, Long inscripcionId, String plantilla,
                          String faltantes, Long usuarioId) {
        em.createNativeQuery("""
                INSERT INTO credencial_impresion
                       (id_responsable, id_inscripcion, plantilla, faltantes, impreso_por)
                VALUES (:resp, :ins, :plantilla, :faltantes, :usuario)
                """)
                .setParameter("resp", responsableId)
                .setParameter("ins", inscripcionId)
                .setParameter("plantilla", plantilla)
                .setParameter("faltantes", faltantes == null || faltantes.isBlank() ? null : faltantes)
                .setParameter("usuario", usuarioId)
                .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> resumen() {
        return em.createNativeQuery("""
                SELECT ci.id_responsable,
                       count(*)                                  AS veces,
                       max(ci.impreso_en)                        AS ultima,
                       bool_or(ci.faltantes IS NOT NULL)         AS algunaIncompleta
                  FROM credencial_impresion ci
                 GROUP BY ci.id_responsable
                """).getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> historial(Long responsableId) {
        Query q = em.createNativeQuery("""
                SELECT ci.impreso_en, ci.plantilla, ci.faltantes, COALESCE(u.username, '—')
                  FROM credencial_impresion ci
                  LEFT JOIN usuario u ON u.id = ci.impreso_por
                 WHERE ci.id_responsable = :resp
                 ORDER BY ci.impreso_en DESC
                """);
        q.setParameter("resp", responsableId);
        return q.getResultList();
    }
}

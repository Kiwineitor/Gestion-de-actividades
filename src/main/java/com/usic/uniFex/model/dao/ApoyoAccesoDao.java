package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ApoyoAccesoDao {

    public static final String ENTRADA = "E";
    public static final String SALIDA = "S";

    private final JdbcTemplate jdbc;

    public void registrar(Long apoyoId, String sentido, Long usuarioId, String origen) {
        jdbc.update("INSERT INTO apoyo_acceso (id_personal_apoyo, sentido, registrado_por, origen)"
                + " VALUES (?, ?, ?, ?)", apoyoId, sentido, usuarioId, origen);
    }

    public String ultimoSentido(Long apoyoId) {
        List<String> r = jdbc.queryForList(
                "SELECT sentido FROM apoyo_acceso WHERE id_personal_apoyo = ?"
                + " ORDER BY cuando DESC, id DESC LIMIT 1", String.class, apoyoId);
        return r.isEmpty() ? null : r.get(0);
    }

    public int[] conteo(Long apoyoId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(CASE WHEN sentido = 'E' THEN 1 ELSE 0 END), 0),"
                + "     COALESCE(SUM(CASE WHEN sentido = 'S' THEN 1 ELSE 0 END), 0)"
                + " FROM apoyo_acceso WHERE id_personal_apoyo = ?",
                (rs, n) -> new int[] { rs.getInt(1), rs.getInt(2) }, apoyoId);
    }

    public List<Object[]> historial(Long apoyoId, int limite) {
        return jdbc.query(
                "SELECT a.sentido, a.cuando, u.username, a.origen"
                + " FROM apoyo_acceso a"
                + " LEFT JOIN usuario u ON u.id = a.registrado_por"
                + " WHERE a.id_personal_apoyo = ?"
                + " ORDER BY a.cuando DESC, a.id DESC LIMIT ?",
                (rs, n) -> new Object[] { rs.getString(1), rs.getTimestamp(2),
                                          rs.getString(3), rs.getString(4) },
                apoyoId, limite);
    }
}

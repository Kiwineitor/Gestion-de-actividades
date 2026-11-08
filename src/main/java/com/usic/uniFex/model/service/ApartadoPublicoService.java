package com.usic.uniFex.model.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.usic.uniFex.model.dao.IPuestoDao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApartadoPublicoService {

    public static final int MAXIMO_POR_ORIGEN = 3;

    private final IPuestoDao puestoDao;
    private final JdbcTemplate jdbc;
    private final PuestoEventPublisher publisher;

    public record Apartado(boolean ok, String mensaje, String token, LocalDateTime expira, Integer minutos) {
        static Apartado error(String m) { return new Apartado(false, m, null, null, null); }
    }

    @Transactional
    public Apartado apartar(Long puestoId, String origen) {
        if (puestoId == null) return Apartado.error("Elige un espacio en el plano.");
        String clave = origen == null || origen.isBlank() ? "desconocido" : origen.trim();
        if (clave.length() > 60) clave = clave.substring(0, 60);
        if (puestoDao.apartadosVigentesDe(clave) >= MAXIMO_POR_ORIGEN) {
            return Apartado.error("Ya tienes " + MAXIMO_POR_ORIGEN + " espacios apartados. Termina esas compras o espera a que se liberen.");
        }
        List<Integer> minutos = jdbc.queryForList("""
                SELECT e.minutos_bloqueo FROM puesto p JOIN categoria c ON c.id = p.id_categoria
                  JOIN edicion e ON e.id = c.id_edicion WHERE p.id = ?
                """, Integer.class, puestoId);
        if (minutos.isEmpty()) return Apartado.error("Ese espacio ya no existe.");
        int tiempo = minutos.get(0) == null ? 10 : minutos.get(0);
        String token = UUID.randomUUID().toString().replace("-", "");
        if (puestoDao.apartarPublico(puestoId, token, clave, tiempo) == 0) {
            return Apartado.error("Ese espacio acaba de ser elegido por otra persona o la venta está cerrada. Elige otro.");
        }
        LocalDateTime expira = jdbc.queryForObject("SELECT reserva_expira FROM puesto WHERE id = ?", LocalDateTime.class, puestoId);
        difundir(puestoId);
        log.info("Espacio {} apartado desde la web por {} minutos", puestoId, tiempo);
        return new Apartado(true, "Espacio apartado", token, expira, tiempo);
    }

    @Transactional
    public boolean soltar(Long puestoId, String token) {
        if (puestoId == null || token == null || token.isBlank()) return false;
        boolean ok = puestoDao.soltarApartado(puestoId, token) > 0;
        if (ok) difundir(puestoId);
        return ok;
    }

    @Transactional
    public boolean fijarParaSolicitud(Long puestoId, String token) {
        boolean ok = token != null && puestoDao.fijarApartado(puestoId, token) > 0;
        if (ok) difundir(puestoId);
        return ok;
    }

    @Transactional
    public boolean tomarParaVenta(Long puestoId, String token, Long usuarioId) {
        return token != null && puestoDao.tomarApartado(puestoId, token, usuarioId) > 0;
    }

    @Transactional
    public boolean liberarDeSolicitud(Long puestoId, String token) {
        boolean ok = puestoId != null && token != null && puestoDao.liberarApartado(puestoId, token) > 0;
        if (ok) difundir(puestoId);
        return ok;
    }

    @Transactional
    public boolean reapartarParaSolicitud(Long puestoId, String token) {
        boolean ok = puestoId != null && token != null && puestoDao.reapartarParaSolicitud(puestoId, token) > 0;
        if (ok) difundir(puestoId);
        return ok;
    }

    private void difundir(Long puestoId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publisher.publicar(puestoId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.publicar(puestoId);
            }
        });
    }
}

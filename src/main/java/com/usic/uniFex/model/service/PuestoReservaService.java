package com.usic.uniFex.model.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.entity.Puesto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PuestoReservaService {

    private final IPuestoDao puestoDao;

    @Value("${unifex.reserva.ttl-segundos:300}")
    private long ttlSegundos;

    @Value("${unifex.reserva.carrito-ttl-segundos:43200}")
    private long carritoTtlSegundos;

    @Transactional
    public boolean reservar(Long puestoId, Long usuarioId) {
        boolean ok = puestoDao.reservarSiLibre(puestoId, usuarioId, ttlSegundos) > 0;
        log.info("Reserva puesto={} usuario={} -> {}", puestoId, usuarioId, ok ? "OK" : "NO DISPONIBLE");
        return ok;
    }

    public record ResultadoLote(List<Long> logradas, List<Long> rechazadas) {
        public boolean todoOk() {
            return rechazadas.isEmpty();
        }
    }

    @Transactional
    public ResultadoLote agregarAlCarrito(List<Long> puestoIds, Long usuarioId) {
        List<Long> ok = new ArrayList<>();
        List<Long> no = new ArrayList<>();
        for (Long id : puestoIds) {
            if (puestoDao.reservarSiLibreOMia(id, usuarioId, carritoTtlSegundos) > 0) ok.add(id);
            else no.add(id);
        }
        log.info("Carrito usuario={} agregadas={} rechazadas={}", usuarioId, ok.size(), no.size());
        return new ResultadoLote(ok, no);
    }

    @Transactional
    public ResultadoLote quitarDelCarrito(List<Long> puestoIds, Long usuarioId) {
        List<Long> ok = new ArrayList<>();
        List<Long> no = new ArrayList<>();
        for (Long id : puestoIds) {
            if (puestoDao.liberarSiEnTramite(id, usuarioId) > 0) ok.add(id);
            else no.add(id);
        }
        return new ResultadoLote(ok, no);
    }

    @Transactional(readOnly = true)
    public List<Puesto> carritoDe(Long usuarioId) {
        return puestoDao.reservadasPor(usuarioId);
    }

    @Transactional
    public boolean liberar(Long puestoId, Long usuarioId) {
        return puestoDao.liberarSiEnTramite(puestoId, usuarioId) > 0;
    }

    @Transactional
    public boolean confirmar(Long puestoId, Long usuarioId) {
        boolean ok = puestoDao.confirmarSiEnTramite(puestoId, usuarioId) > 0;
        log.info("Confirmacion puesto={} usuario={} -> {}", puestoId, usuarioId, ok ? "OK" : "RECHAZADA");
        return ok;
    }

    @Transactional
    public boolean ocupar(Long puestoId, Long usuarioId) {
        boolean ok = puestoDao.ocuparSiDisponible(puestoId, usuarioId) > 0;
        log.info("Ocupacion puesto={} usuario={} -> {}", puestoId, usuarioId, ok ? "OK" : "NO DISPONIBLE");
        return ok;
    }

    @Transactional
    public List<Long> liberarVencidas() {
        List<Long> ids = puestoDao.idsReservasVencidas();
        if (!ids.isEmpty()) {
            puestoDao.liberarReservasVencidas();
            log.info("Reservas vencidas liberadas: {}", ids.size());
        }
        return ids;
    }
}

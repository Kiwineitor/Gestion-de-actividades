package com.usic.uniFex.model.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.dto.OcupacionPuestoDTO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PuestoOcupacionService {

    private final IPuestoDao puestoDao;

    @Transactional(readOnly = true)
    public List<OcupacionPuestoDTO> ocupacionConVendedor() {
        return puestoDao.findOcupacionConVendedor().stream()
                .map(f -> new OcupacionPuestoDTO(
                        numero(f[0]),
                        numero(f[1]),
                        nombreDe(f[2], f[3], f[4], f[6]),
                        limpio(f[5]),
                        limpio(f[7]),
                        fecha(f[8])))
                .filter(o -> o.puestoId() != null && o.vendedorId() != null)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> vendidasPorVendedor() {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] f : puestoDao.contarVendidasPorVendedor()) {
            Long id = numero(f[0]);
            if (id != null) m.put(id, numero(f[1]) == null ? 0L : numero(f[1]));
        }
        return m;
    }

    private static Long numero(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        if (v instanceof BigDecimal b) return b.longValue();
        return null;
    }

    private static String limpio(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static LocalDateTime fecha(Object v) {
        if (v instanceof Timestamp t) return t.toLocalDateTime();
        if (v instanceof LocalDateTime d) return d;
        return null;
    }

    private static String nombreDe(Object nombre, Object paterno, Object materno, Object username) {
        String completo = String.join(" ", Stream.of(nombre, paterno, materno)
                .map(PuestoOcupacionService::limpio)
                .filter(x -> x != null)
                .toList());
        return completo.isBlank() ? limpio(username) : completo;
    }
}

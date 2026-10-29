package com.usic.uniFex.model.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class PresenciaService {

    public static final long VENTANA_VIVO_S = 75;
    private static final long VENTANA_OLVIDO_S = 15 * 60;

    public record Presencia(Long usuarioId, String usuario, String nombre, String rol,
                            String pantalla, String titulo, Instant visto, String origen,
                            Integer avance, String avancePaso, String avanceFaltan) {
    }

    private final Map<Long, Presencia> vistos = new ConcurrentHashMap<>();

    public void latir(Long usuarioId, String usuario, String nombre, String rol,
                      String pantalla, String titulo, String origen,
                      Integer avance, String avancePaso, String avanceFaltan) {
        if (usuarioId == null) return;
        vistos.put(usuarioId, new Presencia(usuarioId, usuario, nombre, rol,
                pantalla, titulo, Instant.now(), origen,
                acotado(avance), avancePaso, avanceFaltan));
    }

    private static Integer acotado(Integer v) {
        if (v == null) return null;
        return Math.max(0, Math.min(100, v));
    }

    public void olvidar(Long usuarioId) {
        if (usuarioId != null) vistos.remove(usuarioId);
    }

    public List<Presencia> listar() {
        Instant limite = Instant.now().minusSeconds(VENTANA_OLVIDO_S);
        vistos.values().removeIf(p -> p.visto().isBefore(limite));
        return vistos.values().stream()
                .sorted(Comparator.comparing(Presencia::visto).reversed())
                .toList();
    }

    public static boolean estaVivo(Presencia p) {
        return p.visto().isAfter(Instant.now().minusSeconds(VENTANA_VIVO_S));
    }
}

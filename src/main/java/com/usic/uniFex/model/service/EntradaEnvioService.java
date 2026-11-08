package com.usic.uniFex.model.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntradaEnvioService {

    private final JdbcTemplate jdbc;
    private final EntradaService entradas;
    private final WhatsAppService whatsApp;

    @Value("${unifex.publico.base-url:}")
    private String baseUrl;

    public record Resultado(boolean ok, String mensaje) {
    }

    public Resultado enviar(Long compraId, boolean alExtra) {
        if (!whatsApp.habilitado()) return new Resultado(false, "WhatsApp no está configurado: usa «Enviar por WhatsApp» o descarga las entradas");
        List<Map<String, Object>> f = jdbc.queryForList("""
                SELECT c.codigo, c.nombre, c.celular, c.celular_extra, c.estado, e.nombre AS actividad,
                       (SELECT count(*) FROM entrada_pase p WHERE p.id_compra = c.id) AS cantidad
                  FROM entrada_compra c JOIN edicion e ON e.id = c.id_edicion WHERE c.id = ?
                """, compraId);
        if (f.isEmpty()) return new Resultado(false, "La compra no existe");
        Map<String, Object> c = f.getFirst();
        if (!"APROBADA".equals(c.get("estado"))) return new Resultado(false, "Solo se envían compras aprobadas");
        String destino = numero((String) (alExtra ? c.get("celular_extra") : c.get("celular")));
        if (destino == null) return new Resultado(false, alExtra ? "La compra no tiene otro WhatsApp" : "La compra no tiene un celular válido");
        String codigo = (String) c.get("codigo");
        long cantidad = ((Number) c.get("cantidad")).longValue();
        String nombre = String.valueOf(c.get("nombre")).split(" ")[0];
        StringBuilder texto = new StringBuilder()
                .append("Hola ").append(nombre).append(",\n\n")
                .append("Tu compra para *").append(c.get("actividad")).append("* fue *aprobada*.\n")
                .append("Te enviamos ").append(cantidad == 1 ? "tu entrada" : "tus " + cantidad + " entradas")
                .append(" y el recibo en este chat.\n\n")
                .append("Muestra el QR de cada entrada en el ingreso: cada QR vale por una persona y no se puede volver a usar para entrar mientras esté dentro.");
        if (baseUrl != null && !baseUrl.isBlank()) {
            texto.append("\n\nTambién puedes verlas aquí: ").append(baseUrl.replaceAll("/+$", "")).append("/?compra=").append(codigo);
        }
        texto.append("\n\n¡Te esperamos!");
        boolean ok = whatsApp.enviarTexto(destino, texto.toString());
        byte[] recibo = entradas.recibo(codigo);
        if (recibo != null) ok &= whatsApp.enviarDocumento(destino, recibo, "recibo-" + codigo + ".pdf", "Recibo " + codigo);
        List<byte[]> pases = entradas.pngPases(codigo);
        for (int i = 0; i < pases.size(); i++) {
            ok &= whatsApp.enviarImagen(destino, pases.get(i), "entrada-" + codigo + "-" + (i + 1) + ".png",
                    "Entrada " + (i + 1) + " de " + pases.size());
        }
        HistorialService.anotarResumen(codigo + " · " + destino);
        log.info("Entradas {} enviadas por WhatsApp a {} ({})", codigo, destino, ok ? "completo" : "con fallas");
        return ok ? new Resultado(true, "Entradas y recibo enviados por WhatsApp al " + destino)
                : new Resultado(false, "Se envió con fallas: revisa la instancia de WhatsApp");
    }

    static String numero(String celular) {
        if (celular == null) return null;
        String d = celular.replaceAll("[^0-9]", "");
        if (d.startsWith("0")) d = d.substring(1);
        if (d.length() == 8) d = "591" + d;
        return d.length() < 8 ? null : d;
    }
}

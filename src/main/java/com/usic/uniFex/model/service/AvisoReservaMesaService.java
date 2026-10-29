package com.usic.uniFex.model.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.usic.uniFex.Config.ContextoRegistro;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvisoReservaMesaService {

    public enum Tipo { BLOQUEADA, CONFIRMADA, RECHAZADA, NO_PRESENTADO }

    private final JdbcTemplate jdbc;
    private final WhatsAppService whatsApp;

    @Value("${app.base-url:}")
    private String baseUrl;

    public void avisar(Long reservaId, Tipo tipo) {
        if (reservaId == null || tipo == null) return;
        Runnable tarea = ContextoRegistro.conservar(() -> enviar(reservaId, tipo));
        Runnable envio = () -> CompletableFuture.runAsync(tarea);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            envio.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                envio.run();
            }
        });
    }

    private void enviar(Long reservaId, Tipo tipo) {
        try {
            if (!whatsApp.habilitado()) return;
            List<Map<String, Object>> filas = jdbc.queryForList("""
                    SELECT r.codigo, r.nombre, r.celular, r.monto, r.motivo, r.bloqueo_expira,
                           m.codigo AS mesa, e.nombre AS evento, e.local_nombre,
                           to_char(e.fecha_inicio, 'DD/MM/YYYY HH24:MI') AS fecha,
                           to_char(e.hora_limite, 'HH24:MI') AS limite
                      FROM mesa_reserva r
                      JOIN mesa m ON m.id = r.id_mesa
                      JOIN mesa_evento e ON e.id = r.id_evento
                     WHERE r.id = ?
                    """, reservaId);
            if (filas.isEmpty()) return;
            Map<String, Object> r = filas.get(0);
            String numero = numero((String) r.get("celular"));
            if (numero == null) return;
            whatsApp.enviarTexto(numero, mensaje(r, tipo));
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar por WhatsApp la reserva de mesa {} ({}): {}", reservaId, tipo, e.getMessage());
        }
    }

    private String mensaje(Map<String, Object> r, Tipo tipo) {
        String saludo = "Hola " + r.get("nombre") + ".";
        String evento = r.get("evento") + (r.get("local_nombre") == null ? "" : " — " + r.get("local_nombre"));
        String mesa = "Mesa " + r.get("mesa") + " · " + evento + (r.get("fecha") == null ? "" : " · " + r.get("fecha"));
        String enlace = enlace((String) r.get("codigo"));
        return switch (tipo) {
            case BLOQUEADA -> String.join("\n",
                    saludo,
                    "Apartamos tu mesa: " + mesa + ".",
                    "Total: Bs " + monto(r.get("monto")) + ". Paga y adjunta tu comprobante antes de que venza el tiempo.",
                    "Tu código: " + r.get("codigo"),
                    enlace);
            case CONFIRMADA -> String.join("\n",
                    saludo,
                    "Tu pago fue validado. ¡Reserva confirmada! " + mesa + ".",
                    r.get("limite") == null ? "Presenta tu QR y tu carnet en el ingreso."
                            : "Presenta tu QR y tu carnet en el ingreso antes de las " + r.get("limite") + ".",
                    "Código: " + r.get("codigo"),
                    enlace);
            case RECHAZADA -> String.join("\n",
                    saludo,
                    "No pudimos validar el pago de tu reserva (" + mesa + ").",
                    "Motivo: " + r.get("motivo"),
                    "La mesa volvió a quedar libre. Si crees que es un error, comunícate con el local.",
                    enlace);
            case NO_PRESENTADO -> String.join("\n",
                    saludo,
                    "Tu reserva (" + mesa + ") fue liberada porque no se registró tu ingreso antes de la hora límite.",
                    enlace);
        };
    }

    private String enlace(String codigo) {
        if (baseUrl == null || baseUrl.isBlank()) return "";
        String raiz = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return "Consulta tu reserva: " + raiz + "?reserva=" + codigo;
    }

    private static String monto(Object v) {
        return v instanceof BigDecimal b ? b.stripTrailingZeros().toPlainString() : String.valueOf(v);
    }

    static String numero(String celular) {
        if (celular == null) return null;
        String n = celular.replaceAll("[^0-9]", "");
        if (n.startsWith("0")) n = n.substring(1);
        if (n.length() == 8) n = "591" + n;
        return n.length() < 8 ? null : n;
    }
}

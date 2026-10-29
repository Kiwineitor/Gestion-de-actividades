package com.usic.uniFex.Config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

public class HandshakeWebSocketLog implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HandshakeWebSocketLog.class);

    private static final long SILENCIO_MS = 60_000;

    private record Contador(AtomicInteger intentos, java.util.concurrent.atomic.AtomicLong ultimoAviso) {
    }

    private final Map<String, Contador> porOrigen = new ConcurrentHashMap<>();

    @Override
    public boolean beforeHandshake(ServerHttpRequest peticion, ServerHttpResponse respuesta,
                                   WebSocketHandler handler, Map<String, Object> atributos) {
        String upgrade = peticion.getHeaders().getUpgrade();
        if (upgrade != null && upgrade.equalsIgnoreCase("websocket")) return true;

        String quien = origen(peticion);
        Contador c = porOrigen.computeIfAbsent(quien,
                k -> new Contador(new AtomicInteger(), new java.util.concurrent.atomic.AtomicLong()));
        int van = c.intentos().incrementAndGet();
        long ahora = System.currentTimeMillis();
        long ultimo = c.ultimoAviso().get();
        if (ahora - ultimo >= SILENCIO_MS && c.ultimoAviso().compareAndSet(ultimo, ahora)) {
            log.warn("Peticion a /ws SIN cabecera Upgrade desde {} · {} intento(s) desde el ultimo aviso"
                    + " · Upgrade={} Connection={} Origin={} User-Agent={}"
                    + " · ese cliente NO tiene tiempo real (mapa congelado)",
                    quien, van,
                    peticion.getHeaders().getFirst("Upgrade"),
                    peticion.getHeaders().getFirst("Connection"),
                    peticion.getHeaders().getOrigin(),
                    peticion.getHeaders().getFirst("User-Agent"));
            c.intentos().set(0);
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest peticion, ServerHttpResponse respuesta,
                               WebSocketHandler handler, Exception excepcion) {
    }

    private static String origen(ServerHttpRequest peticion) {
        String reenviado = peticion.getHeaders().getFirst("X-Forwarded-For");
        if (reenviado != null && !reenviado.isBlank()) return reenviado.split(",")[0].trim();
        if (peticion instanceof ServletServerHttpRequest s) {
            return s.getServletRequest().getRemoteAddr();
        }
        return peticion.getRemoteAddress() == null ? "?" : String.valueOf(peticion.getRemoteAddress());
    }
}

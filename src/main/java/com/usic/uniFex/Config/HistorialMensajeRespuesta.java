package com.usic.uniFex.Config;

import java.util.Map;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.usic.uniFex.model.service.HistorialService;

import lombok.RequiredArgsConstructor;

@ControllerAdvice
@RequiredArgsConstructor
public class HistorialMensajeRespuesta implements ResponseBodyAdvice<Object> {

    private final ObjectMapper json;

    @Override
    public boolean supports(MethodParameter tipo, Class<? extends HttpMessageConverter<?>> conversor) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object cuerpo, MethodParameter tipo, MediaType medio,
                                  Class<? extends HttpMessageConverter<?>> conversor,
                                  ServerHttpRequest peticion, ServerHttpResponse respuesta) {
        if (cuerpo == null || !(peticion instanceof ServletServerHttpRequest p) || !(respuesta instanceof ServletServerHttpResponse r)) return cuerpo;
        if (r.getServletResponse().getStatus() < 400 || !p.getServletRequest().getRequestURI().contains("/api/app/")) return cuerpo;
        String mensaje = mensajeDe(cuerpo);
        if (mensaje != null) p.getServletRequest().setAttribute(HistorialService.ATRIBUTO_ERROR, mensaje);
        return cuerpo;
    }

    private String mensajeDe(Object cuerpo) {
        if (cuerpo instanceof CharSequence s) return s.toString();
        Map<?, ?> m;
        if (cuerpo instanceof Map<?, ?> mapa) m = mapa;
        else {
            try { m = json.convertValue(cuerpo, Map.class); } catch (Exception e) { return null; }
        }
        for (String clave : new String[]{"mensaje", "error", "message"}) {
            Object v = m.get(clave);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        return null;
    }
}

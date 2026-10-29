package com.usic.uniFex.Config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice(annotations = RestController.class)
@Slf4j
public class ManejadorErroresApi {

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            MethodArgumentNotValidException.class,
    })
    public ResponseEntity<Map<String, Object>> peticionInvalida(Exception e, HttpServletRequest peticion) {
        log.warn("Peticion invalida en {} {}: {}", peticion.getMethod(), peticion.getRequestURI(), e.getMessage());
        return cuerpo(HttpStatus.BAD_REQUEST, "Peticion invalida", e, peticion);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> fallo(Exception e, HttpServletRequest peticion) {
        if (e instanceof org.springframework.security.access.AccessDeniedException denegado) throw denegado;
        log.error("Fallo no controlado en {} {}", peticion.getMethod(), peticion.getRequestURI(), e);
        peticion.setAttribute(RegistroErroresFilter.REGISTRADO, true);
        return cuerpo(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno procesando la peticion", e, peticion);
    }

    private ResponseEntity<Map<String, Object>> cuerpo(HttpStatus estado, String mensaje,
                                                       Exception e, HttpServletRequest peticion) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("mensaje", mensaje);
        m.put("detalle", e.getClass().getSimpleName() + ": " + e.getMessage());
        m.put("ruta", peticion.getRequestURI());
        return ResponseEntity.status(estado).body(m);
    }
}

package com.usic.uniFex.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class SelloUsuario {

    private static volatile byte[] clave;

    @Value("${unifex.seguridad.sello-secreto:${unifex.jwt.secret}}")
    private String secreto;

    @PostConstruct
    void iniciar() {
        try {
            clave = MessageDigest.getInstance("SHA-256").digest(("sello-usuario:" + secreto).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo preparar el sello de usuarios", e);
        }
    }

    public static String calcular(String username, String password, Long rolId) {
        byte[] k = clave;
        if (k == null || username == null || password == null) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(k, "HmacSHA256"));
            String datos = username.trim().toLowerCase() + "\u0000" + password + "\u0000" + (rolId == null ? "" : rolId);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(datos.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular el sello", e);
        }
    }

    public static boolean coincide(String guardado, String username, String password, Long rolId) {
        String esperado = calcular(username, password, rolId);
        if (esperado == null || guardado == null) return false;
        return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8), guardado.getBytes(StandardCharsets.UTF_8));
    }
}

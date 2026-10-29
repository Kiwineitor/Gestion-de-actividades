package com.usic.uniFex.model.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ApoyoCodigoService {

    @Value("${unifex.jwt.secret}")
    private String secreto;

    private static final String PREFIJO = "FXA";
    private static final int LARGO_FIRMA = 12;

    public String codigoDe(Long apoyoId) {
        if (apoyoId == null) return null;
        String id = Long.toString(apoyoId, 36).toUpperCase();
        return PREFIJO + "-" + id + "-" + firmar(id);
    }

    public Long apoyoDe(String codigo) {
        if (codigo == null) return null;
        String[] partes = codigo.trim().toUpperCase().split("-", 3);
        if (partes.length != 3 || !PREFIJO.equals(partes[0])) return null;

        String id = partes[1];
        if (!MessageDigest.isEqual(
                firmar(id).getBytes(StandardCharsets.UTF_8),
                partes[2].getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        try {
            return Long.parseLong(id, 36);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String urlPublica(String base, String codigo) {
        String raiz = (base == null || base.isBlank()) ? "" : base.replaceAll("/+$", "");
        return raiz + "/credencial/" + codigo;
    }

    private String firmar(String id) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal(("APOYO|" + id).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h)
                    .substring(0, LARGO_FIRMA).toUpperCase();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el codigo de apoyo", e);
        }
    }
}

package com.usic.uniFex.model.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IInscripcionDao;
import com.usic.uniFex.model.entity.Inscripcion;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotaVentaCodigoService {

    private final IInscripcionDao inscripcionDao;

    @Value("${unifex.jwt.secret}")
    private String secreto;

    private static final String PREFIJO = "FEX";

    public record Verificacion(
            boolean existe,
            boolean vigente,
            Long inscripcionId,
            String entidad,
            String emitida,
            String estado) {

        public static Verificacion inexistente() {
            return new Verificacion(false, false, null, null, null, "NO ENCONTRADA");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String obtenerOEmitir(Long inscripcionId) {
        Inscripcion ins = inscripcionDao.findById(inscripcionId).orElse(null);
        if (ins == null) return null;
        if (ins.getNotaCodigo() != null) return ins.getNotaCodigo();

        LocalDateTime emitida = LocalDateTime.now();
        String codigo = construir(ins.getId(), emitida);
        ins.setNotaCodigo(codigo);
        ins.setNotaEmitidaEn(emitida);
        inscripcionDao.save(ins);
        return codigo;
    }

    @Transactional(readOnly = true)
    public Verificacion verificar(String codigo) {
        if (codigo == null || codigo.isBlank()) return Verificacion.inexistente();
        Inscripcion ins = inscripcionDao.findByNotaCodigo(codigo.trim().toUpperCase()).orElse(null);
        if (ins == null) return Verificacion.inexistente();

        boolean anulada = "X".equalsIgnoreCase(ins.getEstado());
        return new Verificacion(
                true,
                !anulada,
                ins.getId(),
                ins.getEntidad() != null ? ins.getEntidad().getNombre() : null,
                ins.getNotaEmitidaEn() != null ? ins.getNotaEmitidaEn().toString() : null,
                anulada ? "ANULADA" : "VIGENTE");
    }

    private String construir(Long id, LocalDateTime emitida) {
        String base = id + "|" + emitida;
        return PREFIJO + "-" + id + "-" + hmacHex(base).substring(0, 12).toUpperCase();
    }

    private String hmacHex(String mensaje) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el codigo de la nota", e);
        }
    }
}

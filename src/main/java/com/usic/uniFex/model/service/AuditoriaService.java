package com.usic.uniFex.model.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IAuditoriaDao;
import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.entity.Auditoria;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditoriaService {

    public static final String TABLA_INSCRIPCION = "inscripcion";

    public static final String ORIGEN_WEB = "WEB";
    public static final String ORIGEN_APK = "APK";

    public static final String ACCION_REGISTRO = "REGISTRO";
    public static final String ACCION_COMPROBANTE = "COMPROBANTE";
    public static final String ACCION_CANCELACION = "CANCELACION";

    public static final String ACCION_SOLICITUD_CANCELACION = "SOLICITUD_CANCELACION";
    public static final String ACCION_APROBACION_CANCELACION = "APROBACION_CANCELACION";
    public static final String ACCION_RECHAZO_CANCELACION = "RECHAZO_CANCELACION";

    private final IAuditoriaDao auditoriaDao;
    private final IUsuarioDao usuarioDao;

    @Transactional
    public void registrar(String tabla, Long idRegistro, String accion, String detalle,
                          Long usuarioId, String origen) {
        Auditoria a = new Auditoria();
        a.setTabla(tabla);
        a.setIdRegistro(idRegistro);
        a.setAccion(accion);
        a.setDetalle(detalle);
        a.setIdUsuario(usuarioId);
        a.setUsuarioNombre(nombreDe(usuarioId));
        a.setOrigen(normalizarOrigen(origen));
        a.setFecha(LocalDateTime.now());
        auditoriaDao.save(a);
        log.info("Auditoria tabla={} registro={} accion={} usuario={} origen={}",
                tabla, idRegistro, accion, usuarioId, a.getOrigen());
    }

    private String nombreDe(Long usuarioId) {
        if (usuarioId == null) return null;
        return usuarioDao.findById(usuarioId).map(u -> {
            var p = u.getPersona();
            return (p != null && p.getNombreCompleto() != null && !p.getNombreCompleto().isBlank())
                    ? p.getNombreCompleto()
                    : u.getUsername();
        }).orElse(null);
    }

    private static String normalizarOrigen(String origen) {
        return ORIGEN_APK.equalsIgnoreCase(origen == null ? "" : origen.trim()) ? ORIGEN_APK : ORIGEN_WEB;
    }
}

package com.usic.uniFex.model.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.usic.uniFex.model.dao.IInscripcionDao;
import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.dao.ISolicitudCancelacionDao;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.InscripcionPuesto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CancelarInscripcionService {

    public static final String ANULADA = "X";

    private final IInscripcionDao inscripcionDao;
    private final IPuestoDao puestoDao;
    private final ISolicitudCancelacionDao solicitudDao;
    private final AuditoriaService auditoria;
    private final PuestoEventPublisher publisher;

    public record Peticion(String motivo) {
    }

    public record Resultado(boolean ok, String mensaje, List<Long> puestosLiberados) {
        static Resultado error(String mensaje) {
            return new Resultado(false, mensaje, List.of());
        }
    }

    @Transactional
    public Resultado cancelar(Long inscripcionId, String motivo, Long usuarioId, String origen,
                              boolean esAdministracion) {
        Inscripcion i = inscripcionDao.findConTodoPorId(inscripcionId).orElse(null);
        if (i == null) {
            return Resultado.error("La venta no existe");
        }
        if (ANULADA.equals(i.getEstado())) {
            return Resultado.error("La venta ya esta cancelada");
        }

        String motivoReal;
        if (esAdministracion) {
            motivoReal = motivo;
            if (motivoReal == null || motivoReal.isBlank()) {
                return Resultado.error("El motivo de la cancelacion es obligatorio");
            }
            motivoReal = motivoReal.trim();
        } else {
            var aprobada = solicitudDao.aprobadaDeInscripcion(inscripcionId);
            if (aprobada.isEmpty()) {
                return Resultado.error("La cancelacion requiere una solicitud aprobada por administracion");
            }
            motivoReal = aprobada.get().getMotivo();
        }

        List<Long> liberados = new ArrayList<>();
        for (InscripcionPuesto ip : i.getInscripcionPuestos()) {
            Long puestoId = ip.getPuesto() != null ? ip.getPuesto().getId() : null;
            if (puestoId != null && puestoDao.liberarSiOcupado(puestoId, usuarioId) > 0) {
                liberados.add(puestoId);
            }
        }

        Date ahora = new Date();
        i.setEstado(ANULADA);
        i.setMotivoCancelacion(motivoReal);
        i.setFechaCancelacion(java.time.LocalDateTime.now());
        i.setCanceladaPorIdUsuario(usuarioId);
        i.setOrigenCancelacion(origen);
        i.setModificacion(ahora);
        i.setModificacionIdUsuario(usuarioId);
        inscripcionDao.save(i);

        auditoria.registrar(AuditoriaService.TABLA_INSCRIPCION, inscripcionId,
                AuditoriaService.ACCION_CANCELACION, motivoReal, usuarioId, origen);

        difundirTrasCommit(liberados);
        log.info("Inscripcion {} cancelada por usuario={} origen={} casetas liberadas={}",
                inscripcionId, usuarioId, origen, liberados.size());

        return new Resultado(true,
                "Venta cancelada" + (liberados.isEmpty() ? "" : " y " + liberados.size() + " caseta(s) liberada(s)"),
                liberados);
    }

    private void difundirTrasCommit(List<Long> liberados) {
        if (liberados.isEmpty()) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publisher.publicarVarios(liberados);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.publicarVarios(liberados);
            }
        });
    }
}

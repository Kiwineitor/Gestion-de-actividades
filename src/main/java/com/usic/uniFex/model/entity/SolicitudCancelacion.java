package com.usic.uniFex.model.entity;

import java.time.LocalDateTime;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "solicitud_cancelacion")
@Setter
@Getter
public class SolicitudCancelacion extends AuditoriaConfig {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADA = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_inscripcion", nullable = false)
    private Long idInscripcion;

    @Column(nullable = false)
    private String motivo;

    private String respuesta;

    @Column(name = "estado_solicitud", nullable = false, length = 20)
    private String estadoSolicitud = PENDIENTE;

    @Column(name = "fecha_solicitud")
    private LocalDateTime fechaSolicitud;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_inscripcion", insertable = false, updatable = false)
    private Inscripcion inscripcion;
}

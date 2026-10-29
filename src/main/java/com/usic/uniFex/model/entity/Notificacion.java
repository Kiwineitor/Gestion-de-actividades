package com.usic.uniFex.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "notificacion")
@Setter
@Getter
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_usuario_destino", nullable = false)
    private Usuario usuarioDestino;

    @Column(name = "tipo", length = 50, nullable = false)
    private String tipo;

    @Column(name = "asunto", length = 200, nullable = false)
    private String asunto;

    @Column(name = "cuerpo", columnDefinition = "TEXT")
    private String cuerpo;

    @ManyToOne
    @JoinColumn(name = "id_inscripcion")
    private Inscripcion inscripcion;

    @ManyToOne
    @JoinColumn(name = "id_puesto")
    private Puesto puesto;

    @Column(name = "leida", nullable = false)
    private Boolean leida = false;

    @Column(name = "leida_en")
    private LocalDateTime leidaEn;

    @ManyToOne
    @JoinColumn(name = "id_notificacion_padre")
    private Notificacion notificacionPadre;

    @Column(name = "estado_hilo", length = 20)
    private String estadoHilo = "ABIERTA";

    @Column(name = "respuesta", columnDefinition = "TEXT")
    private String respuesta;

    @ManyToOne
    @JoinColumn(name = "respondida_por_id_usuario")
    private Usuario respondidaPor;

    @Column(name = "respondida_en")
    private LocalDateTime respondidaEn;

    @Column(name = "_fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "_registro_id_usuario")
    private Usuario registradoPor;
}
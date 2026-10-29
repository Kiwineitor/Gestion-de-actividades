package com.usic.uniFex.model.entity;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "responsable")
@Setter @Getter
public class Responsable extends AuditoriaConfig{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona")
    private Persona persona;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entidad")
    private Entidad entidad;

    @Column(length = 60)
    private String cargo;

    @Column(name = "es_titular", nullable = false)
    private boolean esTitular;

    @Column(name = "es_extra", nullable = false)
    private boolean esExtra;

    @Column(name = "monto_extra")
    private java.math.BigDecimal montoExtra;

    @Column(name = "comprobante_extra")
    private String comprobanteExtra;
}
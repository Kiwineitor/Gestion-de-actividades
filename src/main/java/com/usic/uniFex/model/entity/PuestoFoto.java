package com.usic.uniFex.model.entity;

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
@Table(name = "puesto_foto")
@Setter
@Getter
public class PuestoFoto extends AuditoriaConfig {

    public static final String ACTIVA = "A";
    public static final String BORRADA = "X";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_puesto")
    private Puesto puesto;

    @Column(nullable = false, length = 300)
    private String ruta;

    @Column(length = 200)
    private String descripcion;

    @Column(nullable = false)
    private Integer orden = 0;
}

package com.usic.uniFex.model.entity;

import java.time.LocalDateTime;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "puesto")
@Setter
@Getter
public class Puesto extends AuditoriaConfig{
    public static final String LIBRE      = "L";
    public static final String EN_TRAMITE = "T";
    public static final String OCUPADO    = "O";
    public static final String BLOQUEADO  = "X";

    public static final String REGISTRO_ACTIVO  = "A";
    public static final String REGISTRO_ANULADO = "X";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codigo;
    private String tamano;
    private String estadoPuesto;

    private Long reservadoPorIdUsuario;

    private LocalDateTime reservaExpira;

    @Version
    private Long version;

    @Column(name = "mapa_x")
    private Double mapaX;
    @Column(name = "mapa_y")
    private Double mapaY;

    @Column(name = "mapa_escala")
    private Double mapaEscala;

    @Column(name = "mapa_rotacion")
    private Integer mapaRotacion;

    @Column(length = 200)
    private String referencia;

    @Column(length = 500)
    private String observacion;

    @Column(precision = 10, scale = 2)
    private java.math.BigDecimal precio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria")
    private Categoria categoria;
}
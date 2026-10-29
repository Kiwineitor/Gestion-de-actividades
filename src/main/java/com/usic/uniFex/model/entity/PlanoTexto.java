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
@Table(name = "plano_texto")
@Setter @Getter
public class PlanoTexto extends AuditoriaConfig {

    public static final String REGISTRO_ACTIVO  = "A";
    public static final String REGISTRO_ANULADO = "X";

    public static final double TAMANO_POR_DEFECTO = 0.02;
    public static final double GROSOR_BORDE_POR_DEFECTO = 0.15;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_edicion")
    private Edicion edicion;

    private String contenido;

    @Column(name = "mapa_x")
    private Double mapaX;

    @Column(name = "mapa_y")
    private Double mapaY;

    private Double tamano;

    private String color;

    @Column(name = "color_borde")
    private String colorBorde;

    @Column(name = "grosor_borde")
    private Double grosorBorde;

    private Double rotacion;
}

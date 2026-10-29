package com.usic.uniFex.model.entity;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "categoria")
@Setter
@Getter
public class Categoria extends AuditoriaConfig{

    public static final String REGISTRO_ACTIVO  = "A";
    public static final String REGISTRO_ANULADO = "X";

    public static final double TAMANO_MAPA_POR_DEFECTO = 0.012;

    public static final String TAMANO_POR_DEFECTO = "3x3";

    public static final java.math.BigDecimal PRECIO_POR_DEFECTO = java.math.BigDecimal.ZERO;

    public static final java.util.Set<String> FORMAS = java.util.Set.of(
            "cuadrado", "rectangulo", "circulo", "triangulo", "hexagono");

    public static final double PROPORCION_MIN = 0.2;
    public static final double PROPORCION_MAX = 5.0;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String descripcion;
    private String color;

    private String forma;

    @Column(name = "tamano_mapa")
    private Double tamanoMapa;

    @Column(name = "mapa_proporcion")
    private Double mapaProporcion;

    @Column(name = "id_edicion")
    private Long idEdicion;

    @Column(name = "precio_base")
    private java.math.BigDecimal precioBase;
}
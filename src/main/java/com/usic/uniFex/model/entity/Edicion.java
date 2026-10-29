package com.usic.uniFex.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "edicion")
@Setter
@Getter
public class Edicion {

    public static final String SQL_ID_ACTIVA = "(SELECT ed.id FROM edicion ed WHERE ed.activa LIMIT 1)";
    public static final String SQL_CATEGORIAS_ACTIVAS =
            "(SELECT cat.id FROM categoria cat WHERE cat.id_edicion = " + SQL_ID_ACTIVA + ")";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 40)
    private String tipo;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false)
    private Integer anio;

    @Column(nullable = false)
    private Boolean activa;

    @Column(name = "plano_archivo", length = 300)
    private String planoArchivo;

    @Column(name = "plano_ancho")
    private Integer planoAncho;

    @Column(name = "plano_alto")
    private Integer planoAlto;

    @Column(name = "plano_version", nullable = false)
    private Integer planoVersion = 0;

    @Column(name = "plano_subido_en")
    private java.time.LocalDateTime planoSubidoEn;
}

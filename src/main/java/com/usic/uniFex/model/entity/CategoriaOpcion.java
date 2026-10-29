package com.usic.uniFex.model.entity;

import java.math.BigDecimal;

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
@Table(name = "categoria_opcion")
@Setter @Getter
public class CategoriaOpcion extends AuditoriaConfig {

    public static final String REGISTRO_ACTIVO  = "A";
    public static final String REGISTRO_ANULADO = "X";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria")
    private Categoria categoria;

    private String nombre;

    private BigDecimal precio;

    private Boolean predeterminada;

    @Column(name = "orden")
    private Integer orden;
}

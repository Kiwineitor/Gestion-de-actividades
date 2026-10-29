package com.usic.uniFex.model.entity;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "area")
@Setter @Getter
public class Area extends AuditoriaConfig {

    public static final String REGISTRO_ACTIVO  = "A";
    public static final String REGISTRO_ANULADO = "X";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sigla;

    private String nombre;

    private Integer orden;
}

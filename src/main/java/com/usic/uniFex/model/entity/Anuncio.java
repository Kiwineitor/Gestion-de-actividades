package com.usic.uniFex.model.entity;

import java.time.LocalDateTime;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "anuncio")
@Setter @Getter
public class Anuncio extends AuditoriaConfig {

    public static final String PUBLICADO = "A";
    public static final String RETIRADO  = "X";

    public static final String INFO    = "info";
    public static final String AVISO   = "aviso";
    public static final String URGENTE = "urgente";

    public static final String INTERNO = "INTERNO";
    public static final String PUBLICO = "PUBLICO";
    public static final String AMBOS   = "AMBOS";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titulo;

    private String mensaje;

    private String nivel;

    @Column(name = "vigente_hasta")
    private LocalDateTime vigenteHasta;

    @Column(name = "vigente_desde")
    private LocalDateTime vigenteDesde;

    private String destino;

    private String enlace;
}

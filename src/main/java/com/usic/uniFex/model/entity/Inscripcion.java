package com.usic.uniFex.model.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "inscripcion")
@Setter
@Getter
public class Inscripcion extends AuditoriaConfig{
    public static final int MAX_NUM_COMPROBANTE = 100;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String entidadBancaria;
    @Column(name = "num_comprobante", length = MAX_NUM_COMPROBANTE)
    @jakarta.validation.constraints.Size(max = MAX_NUM_COMPROBANTE,
            message = "El numero de comprobante admite hasta 100 caracteres")
    private String numComprobante;
    private String imgComprobante;
    private boolean pagoContado;
    private Date fechaInicio;
    private Date fechaFin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entidad")
    private Entidad entidad;

    private LocalDateTime fechaCompra;
    private String inscripcionEstado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_edicion")
    private Edicion edicion;

    @OneToMany(mappedBy = "inscripcion", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<InscripcionPuesto> inscripcionPuestos = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "_registro_idUsuario", insertable = false, updatable = false)
    private Usuario registroUsuario;

    private String motivoCancelacion;

    private LocalDateTime fechaCancelacion;

    @Column(name = "origen_cancelacion")
    private String origenCancelacion;

    @Column(name = "cancelada_por_id_usuario")
    private Long canceladaPorIdUsuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelada_por_id_usuario", insertable = false, updatable = false)
    private Usuario canceladaPorUsuario;

    @Column(name = "nota_codigo", length = 40)
    private String notaCodigo;

    @Column(name = "nota_emitida_en")
    private LocalDateTime notaEmitidaEn;

}

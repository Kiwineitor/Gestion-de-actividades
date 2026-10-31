package com.usic.uniFex.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "interesado_stand")
@Getter
@Setter
public class InteresadoStand {

    public static final String ESTADO_NUEVO = "NUEVO";
    public static final String ESTADO_ATENDIDO = "ATENDIDO";
    public static final String ESTADO_DESCARTADO = "DESCARTADO";
    public static final String PAGO_COMPROBANTE = "COMPROBANTE";
    public static final String PAGO_OFICINA = "OFICINA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_completo", nullable = false, length = 200)
    private String nombreCompleto;

    @Column(name = "celular", nullable = false, length = 30)
    private String celular;

    @Column(name = "empresa", nullable = false, length = 200)
    private String empresa;

    @Column(name = "rubro", nullable = false, length = 200)
    private String rubro;

    @ManyToOne
    @JoinColumn(name = "id_categoria", nullable = false)
    private Categoria categoria;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = ESTADO_NUEVO;

    @ManyToOne
    @JoinColumn(name = "id_puesto")
    private Puesto puesto;

    @Column(name = "modo_pago", length = 20)
    private String modoPago;

    @Column(name = "comprobante_archivo", length = 300)
    private String comprobanteArchivo;

    @Column(name = "ci", length = 20)
    private String ci;

    @Column(name = "id_inscripcion")
    private Long inscripcionId;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();
}

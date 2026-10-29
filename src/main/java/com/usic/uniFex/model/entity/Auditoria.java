package com.usic.uniFex.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "auditoria")
@Setter
@Getter
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tabla;

    @Column(name = "id_registro")
    private Long idRegistro;

    private String accion;

    private String detalle;

    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "usuario_nombre")
    private String usuarioNombre;

    private String origen;

    private LocalDateTime fecha;
}

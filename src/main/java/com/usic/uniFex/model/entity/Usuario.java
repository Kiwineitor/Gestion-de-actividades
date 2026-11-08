package com.usic.uniFex.model.entity;

import java.util.Date;

import com.usic.uniFex.Config.AuditoriaConfig;
import com.usic.uniFex.security.SelloUsuario;

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
@Table(name = "usuario")
@Setter @Getter
public class Usuario extends AuditoriaConfig{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, unique=true, length=50)
    private String username;

    @Column(nullable=false, length=120)
    private String password;

    @ManyToOne(optional=false)
    @JoinColumn(name="persona_id")
    private Persona persona;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "rol_id", nullable = false)
    private Rol rol;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_area_seguimiento")
    private Area areaSeguimiento;

    @Column(name = "debe_cambiar_clave", nullable = false)
    private boolean debeCambiarClave;

    @Column(name = "clave_cambiada_en")
    private Date claveCambiadaEn;

    @Column(name = "sello", length = 100)
    private String sello;

    @Column(name = "sesion_version", nullable = false)
    private int sesionVersion;

    @Column(name = "terminos_version", length = 20)
    private String terminosVersion;

    @Column(name = "terminos_aceptados_en")
    private Date terminosAceptadosEn;

    @Column(name = "codigo_recuperacion", length = 100)
    private String codigoRecuperacion;

    @Column(name = "codigo_recuperacion_en")
    private Date codigoRecuperacionEn;

    public void setUsername(String username) {
        this.username = username;
        resellar();
    }

    public void setPassword(String password) {
        this.password = password;
        resellar();
    }

    public void setRol(Rol rol) {
        this.rol = rol;
        resellar();
    }

    public void resellar() {
        this.sello = SelloUsuario.calcular(username, password, rol == null ? null : rol.getId());
    }

    public void cerrarSesiones() {
        this.sesionVersion++;
    }
}

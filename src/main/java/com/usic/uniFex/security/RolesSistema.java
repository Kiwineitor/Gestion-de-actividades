package com.usic.uniFex.security;

import java.util.Arrays;
import java.util.Optional;

public enum RolesSistema {

    SUPER_USUARIO("SUPER USUARIO",
            "Control total: gestiona usuarios, roles, el plano de la feria y toda la administracion."),

    ADMINISTRADOR("ADMINISTRADOR",
            "Administracion de la feria: usuarios, plano, inscripciones y reportes globales."),

    ADMINISTRATIVO("ADMINISTRATIVO",
            "Vendedor: reserva, vende y consulta sus propias ventas. No redisena el plano."),

    CONTROL("CONTROL",
            "Control de acceso en puerta: verifica credenciales y registra el ingreso de responsables."),

    ASESORIA("ASESORIA",
            "Consulta: ve listados y reportes de la feria, sin modificar nada."),

    VERIFICADOR("VERIFICADOR",
            "Verifica que la inscripcion este completa (comprobante y fotos) antes de imprimir credenciales.");

    private final String nombre;
    private final String descripcion;

    RolesSistema(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public static Optional<RolesSistema> porNombre(String nombre) {
        String buscado = normalizar(nombre);
        return Arrays.stream(values())
                .filter(r -> r.nombre.equals(buscado))
                .findFirst();
    }

    public static boolean esDelSistema(String nombre) {
        return porNombre(nombre).isPresent();
    }

    public static String normalizar(String nombre) {
        return nombre == null ? "" : nombre.trim().replaceAll("\\s{2,}", " ").toUpperCase(java.util.Locale.ROOT);
    }
}

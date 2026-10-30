package com.usic.uniFex.security;

import java.util.Arrays;
import java.util.Optional;

public enum RolesSistema {

    SUPER_USUARIO("SUPER USUARIO",
            "Superadministrador: control total del sistema, la marca, los roles y todas las actividades."),

    ADMINISTRADOR("ADMINISTRADOR",
            "Administrador: gestiona actividades, planos, precios, solicitudes, credenciales y usuarios."),

    ADMINISTRATIVO("ADMINISTRATIVO",
            "Operador de ventas: reserva, vende y consulta sus propias ventas."),

    CONTROL("CONTROL",
            "Control de acceso: verifica credenciales y registra ingresos."),

    ASESORIA("ASESORIA",
            "Supervisor: consulta listados y reportes sin modificar nada."),

    VERIFICADOR("VERIFICADOR",
            "Verificador: revisa que cada registro esté completo antes de imprimir credenciales.");

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

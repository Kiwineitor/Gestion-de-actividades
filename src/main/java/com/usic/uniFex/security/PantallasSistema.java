package com.usic.uniFex.security;

import java.util.Arrays;
import java.util.List;

public enum PantallasSistema {

    INICIO("inicio", "Inicio", "Mi trabajo"),
    MAPA("mapa", "Mapa de ventas", "Mi trabajo"),
    VENTA("venta", "Registrar venta", "Mi trabajo"),
    MIS_VENTAS("mis-ventas", "Mis ventas", "Mi trabajo"),
    CATALOGO("catalogo", "Catalogo", "Actividad"),
    TABLERO("tablero", "Tablero", "Actividad"),
    EDITOR("editor", "Editor del plano", "Plano y precios"),
    CATEGORIAS("categorias", "Categorías", "Plano y precios"),
    PUESTOS("puestos", "Puestos", "Plano y precios"),
    SEGUIMIENTO("seguimiento", "Seguimiento en vivo", "Administracion"),
    ANUNCIOS("anuncios", "Comunicados", "Administracion"),
    RESPONSABLES_EXTRA("responsables-extra", "Responsables extra", "Acreditacion"),
    VENDEDORES("vendedores", "Vendedores", "Administracion"),
    INSCRIPCIONES("inscripciones", "Inscripciones", "Actividad"),
    INTERESADOS("interesados", "Interesados en exponer", "Actividad"),
    DIRECCION("direccion", "Tablero de dirección", "Administracion"),
    REPORTES("reportes", "Reportes", "Administracion"),
    CONTROL_VENTAS("control-ventas", "Control de ventas", "Administracion"),
    REPORTE_RESPONSABLES("reporte-responsables", "Reporte de Responsables", "Acreditacion"),
    CREDENCIALES("credenciales", "Credenciales", "Acreditacion"),
    CREDENCIALES_APOYO("credenciales-apoyo", "Credenciales personal apoyo", "Acreditacion"),
    IMPRESION_MASIVA("impresion-masiva", "Impresión Masiva", "Acreditacion"),
    ESCANER("escaner", "Escanear credencial", "Acreditacion"),
    CONTROL_ACCESO("control-acceso", "Control de acceso", "Acreditacion"),
    USUARIOS("usuarios", "Usuarios", "Administracion"),
    ROLES("roles", "Roles", "Administracion"),
    MANTENIMIENTO("mantenimiento", "Mantenimiento", "Sistema"),
    PERMISOS("permisos", "Permisos", "Administracion"),
    PERSONAS("personas", "Personas", "Administracion"),
    PERSONAL_APOYO("personal-apoyo", "Personal de apoyo", "Administracion"),
    NOTIFICACIONES("notificaciones", "Notificaciones", "Mi trabajo"),
    MESAS("mesas", "Reservas de mesas", "Actividad");

    private final String clave;
    private final String titulo;
    private final String grupo;

    PantallasSistema(String clave, String titulo, String grupo) {
        this.clave = clave;
        this.titulo = titulo;
        this.grupo = grupo;
    }

    public String getClave() {
        return clave;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getGrupo() {
        return grupo;
    }

    public static List<PantallasSistema> todas() {
        return Arrays.asList(values());
    }

    public static boolean existe(String clave) {
        return Arrays.stream(values()).anyMatch(p -> p.clave.equals(clave));
    }
}

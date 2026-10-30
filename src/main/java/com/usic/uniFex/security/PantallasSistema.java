package com.usic.uniFex.security;

import java.util.Arrays;
import java.util.List;

public enum PantallasSistema {

    INICIO("inicio", "Inicio", "Mi trabajo"),
    NOTIFICACIONES("notificaciones", "Notificaciones", "Mi trabajo"),
    ACTIVIDADES("actividades", "Centro de actividades", "Actividades"),
    MESAS("mesas", "Reservas de mesas: solicitudes y pagos", "Actividades"),
    MESAS_DISENO("mesas-diseno", "Eventos con mesas: datos, plano y precios", "Actividades"),
    INTERESADOS("interesados", "Solicitudes de participación", "Actividades"),
    TABLERO("tablero", "Tablero", "Actividades"),
    CATALOGO("catalogo", "Catálogo", "Actividades"),
    MAPA("mapa", "Mapa de ventas", "Ventas"),
    VENTA("venta", "Registrar venta", "Ventas"),
    MIS_VENTAS("mis-ventas", "Mis ventas", "Ventas"),
    INSCRIPCIONES("inscripciones", "Inscripciones", "Ventas"),
    CONTROL_VENTAS("control-ventas", "Control de ventas", "Ventas"),
    VENDEDORES("vendedores", "Vendedores", "Ventas"),
    EDITOR("editor", "Editor del plano", "Planos y precios"),
    CATEGORIAS("categorias", "Categorías y precios", "Planos y precios"),
    PUESTOS("puestos", "Puestos", "Planos y precios"),
    CREDENCIALES("credenciales", "Credenciales", "Credenciales y acceso"),
    CREDENCIALES_DISENO("credenciales-diseno", "Diseño de credenciales", "Credenciales y acceso"),
    CREDENCIALES_APOYO("credenciales-apoyo", "Credenciales de personal de apoyo", "Credenciales y acceso"),
    IMPRESION_MASIVA("impresion-masiva", "Impresión masiva", "Credenciales y acceso"),
    RESPONSABLES_EXTRA("responsables-extra", "Responsables extra", "Credenciales y acceso"),
    ESCANER("escaner", "Escanear credencial", "Credenciales y acceso"),
    CONTROL_ACCESO("control-acceso", "Control de acceso", "Credenciales y acceso"),
    DIRECCION("direccion", "Tablero de dirección", "Reportes"),
    REPORTES("reportes", "Reportes", "Reportes"),
    REPORTE_RESPONSABLES("reporte-responsables", "Reporte de responsables", "Reportes"),
    SEGUIMIENTO("seguimiento", "Seguimiento en vivo", "Reportes"),
    USUARIOS("usuarios", "Usuarios", "Personas y permisos"),
    ROLES("roles", "Roles", "Personas y permisos"),
    PERMISOS("permisos", "Permisos", "Personas y permisos"),
    PERSONAS("personas", "Personas", "Personas y permisos"),
    PERSONAL_APOYO("personal-apoyo", "Personal de apoyo", "Personas y permisos"),
    ANUNCIOS("anuncios", "Comunicados", "Comunicación"),
    MANTENIMIENTO("mantenimiento", "Mantenimiento", "Sistema");

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

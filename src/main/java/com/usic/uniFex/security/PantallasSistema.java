package com.usic.uniFex.security;

import java.util.Arrays;
import java.util.List;

public enum PantallasSistema {

    INICIO("inicio", "Inicio", "Mi trabajo"),
    NOTIFICACIONES("notificaciones", "Notificaciones (campanita)", "Mi trabajo"),
    ACTIVIDADES("actividades", "Mis actividades: crear y configurar", "Actividades"),
    EDITOR("editor", "Plano de las actividades", "Actividades"),
    CATEGORIAS("categorias", "Categorías, precios y personas incluidas", "Actividades"),
    PUESTOS("puestos", "Espacios con precio propio", "Actividades"),
    MESAS("mesas", "Pagos por validar: compras de entradas", "Solicitudes e inscripciones"),
    INTERESADOS("interesados", "Pagos por validar: solicitudes de espacios (aprobar = registrar venta)", "Solicitudes e inscripciones"),
    CONTROL_VENTAS("control-ventas", "Inscripciones: rendición de cuentas de ferias", "Solicitudes e inscripciones"),
    RESPONSABLES_EXTRA("responsables-extra", "Inscripciones: personas extra", "Solicitudes e inscripciones"),
    INSCRIPCIONES("inscripciones", "Inscripciones: listado general y anulaciones", "Solicitudes e inscripciones"),
    CREDENCIALES("credenciales", "Credenciales: emitir y reenviar las de cada venta", "Credenciales e ingreso"),
    CREDENCIALES_DISENO("credenciales-diseno", "Credenciales: diseño", "Credenciales e ingreso"),
    PERSONAL_APOYO("personal-apoyo", "Credenciales: registro del personal de apoyo", "Credenciales e ingreso"),
    CREDENCIALES_APOYO("credenciales-apoyo", "Credenciales: imprimir las del personal de apoyo", "Credenciales e ingreso"),
    IMPRESION_MASIVA("impresion-masiva", "Credenciales: impresión masiva", "Credenciales e ingreso"),
    ESCANER("escaner", "Ingreso: escanear credenciales", "Credenciales e ingreso"),
    MESAS_INGRESO("mesas-ingreso", "Ingreso: escanear entradas", "Credenciales e ingreso"),
    CONTROL_ACCESO("control-acceso", "Ingreso: movimientos y quién está dentro", "Credenciales e ingreso"),
    MAPA("mapa", "Punto de venta: vender espacios y entradas", "Ventas"),
    MIS_VENTAS("mis-ventas", "Mis ventas: lo que registré o aprobé", "Ventas"),
    CATALOGO("catalogo", "Catálogo de precios", "Ventas"),
    DIRECCION("direccion", "Reportes: resumen por actividad", "Reportes"),
    REPORTES("reportes", "Reportes: descargas", "Reportes"),
    REPORTE_RESPONSABLES("reporte-responsables", "Reportes: responsables", "Reportes"),
    SEGUIMIENTO("seguimiento", "Seguimiento en vivo", "Reportes"),
    USUARIOS("usuarios", "Usuarios y sus datos personales", "Usuarios y permisos"),
    ROLES("roles", "Roles", "Usuarios y permisos"),
    PERMISOS("permisos", "Permisos", "Usuarios y permisos"),
    HISTORIAL("historial", "Historial de acciones", "Usuarios y permisos"),
    ANUNCIOS("anuncios", "Anuncios y promociones", "Comunicación"),
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

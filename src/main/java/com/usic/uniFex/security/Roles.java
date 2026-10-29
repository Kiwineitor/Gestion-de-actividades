package com.usic.uniFex.security;

public final class Roles {

    private Roles() {
    }

    public static final java.util.List<String> AUTORIDADES_ADMINISTRA =
            java.util.List.of("ROLE_SUPER_USUARIO", "ROLE_ADMINISTRADOR");

    public static final String SOLO_SUPER_USUARIO = "hasRole('SUPER_USUARIO')";

    public static final String EDITA_PLANO = "hasAnyRole('SUPER_USUARIO','ADMINISTRADOR')";

    public static final String GESTIONA_USUARIOS = "hasAnyRole('SUPER_USUARIO','ADMINISTRADOR')";

    public static final String ADMINISTRA = "hasAnyRole('SUPER_USUARIO','ADMINISTRADOR')";

    public static final String VERIFICA_CREDENCIALES =
            "hasAnyRole('SUPER_USUARIO','ADMINISTRADOR','VERIFICADOR')";

    public static final String VE_INSCRIPCIONES =
            "hasAnyRole('SUPER_USUARIO','ADMINISTRADOR','VERIFICADOR','ASESORIA')";

    public static final String VE_INSCRIPCIONES_O_PANTALLA =
            VE_INSCRIPCIONES + " or @acceso.puede('inscripciones')";

    public static final java.util.List<String> AUTORIDADES_VEN_TODAS_LAS_INSCRIPCIONES = java.util.List.of(
            "ROLE_SUPER_USUARIO", "ROLE_ADMINISTRADOR", "ROLE_VERIFICADOR", "ROLE_ASESORIA");

    public static final String VE_REPORTES =
            "hasAnyRole('SUPER_USUARIO','ADMINISTRADOR','ASESORIA')";

    public static final String GESTIONA_MESAS = ADMINISTRA + " or @acceso.puede('mesas')";

    public static final String VE_CONTROL_VENTAS =
            VE_REPORTES + " or @acceso.puede('control-ventas')";

    public static final java.util.List<String> AUTORIDADES_VEN_REPORTES = java.util.List.of(
            "ROLE_SUPER_USUARIO", "ROLE_ADMINISTRADOR", "ROLE_ASESORIA");

    public static final String CONTROLA_ACCESO =
            "hasAnyRole('SUPER_USUARIO','ADMINISTRADOR','CONTROL')";

    public static final String CONTROLA_ACCESO_O_ESCANER =
            CONTROLA_ACCESO + " or @acceso.puede('escaner')";

    public static final String USA_CREDENCIALES =
            "hasAnyRole('SUPER_USUARIO','ADMINISTRADOR','VERIFICADOR','ADMINISTRATIVO')";

    public static final String USA_CREDENCIALES_O_PANTALLA =
            USA_CREDENCIALES + " or @acceso.puede('credenciales')";

    public static final String USA_PERSONAL_APOYO = "isAuthenticated()";
}

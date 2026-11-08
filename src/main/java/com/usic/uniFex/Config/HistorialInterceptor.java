package com.usic.uniFex.Config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.usic.uniFex.model.service.HistorialService;
import com.usic.uniFex.security.JwtUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class HistorialInterceptor implements HandlerInterceptor {

    private static final Set<String> METODOS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private static final List<Pattern> IGNORADAS = List.of(
            Pattern.compile("^/api/app/presencia.*"),
            Pattern.compile("^/api/app/errores/cliente$"),
            Pattern.compile("^/api/app/notificaciones/(\\d+/leer|leer-todas)$"),
            Pattern.compile("^/api/app/plantillas-credencial/(vista-previa|fondo)$"),
            Pattern.compile("^/api/app/anuncios/imagen$"),
            Pattern.compile("^/api/app/puestos/(\\d+/(reservar|liberar|confirmar)|carrito)$"),
            Pattern.compile("^/api/app/accesos$"),
            Pattern.compile("^/api/app/entradas/ingreso$"));

    private record Regla(String metodo, Pattern ruta, String modulo, String texto) {
    }

    private static Regla r(String metodo, String ruta, String modulo, String texto) {
        return new Regla(metodo, Pattern.compile("^" + ruta.replace("{id}", "(\\d+)").replace("{n}", "\\d+") + "$"), modulo, texto);
    }

    private static final List<Regla> REGLAS = List.of(
            r("POST", "/api/app/ediciones/asistente", "Actividades", "Creó una actividad"),
            r("POST", "/api/app/ediciones", "Actividades", "Creó una actividad"),
            r("PATCH", "/api/app/ediciones/{id}/configuracion", "Actividades", "Modificó la configuración de la actividad #{id}"),
            r("PATCH", "/api/app/ediciones/{id}", "Actividades", "Modificó los datos de la actividad #{id}"),
            r("DELETE", "/api/app/ediciones/{id}", "Actividades", "Eliminó la actividad #{id}"),
            r("POST", "/api/app/ediciones/{id}/publicar", "Actividades", "Cambió la publicación de la actividad #{id}"),
            r("POST", "/api/app/ediciones/{id}/habilitar", "Actividades", "Cambió la publicación de la actividad #{id}"),
            r("POST", "/api/app/ediciones/{id}/finalizar", "Actividades", "Finalizó o reabrió la actividad #{id}"),
            r("POST", "/api/app/ediciones/{id}/estado", "Actividades", "Pospuso, canceló o normalizó la actividad #{id}"),
            r("PUT", "/api/app/ediciones/{id}/qr-estilo", "Actividades", "Cambió el diseño del QR de la actividad #{id}"),
            r("POST", "/api/app/ediciones/{id}/duplicar", "Actividades", "Duplicó la actividad #{id}"),
            r("POST", "/api/app/ediciones/papelera/{n}/restaurar", "Actividades", "Restauró una actividad de la papelera"),
            r("POST", "/api/app/ediciones/{id}/activar", "Actividades", "Puso en curso la actividad #{id}"),
            r("POST", "/api/app/ediciones/{id}/portada", "Actividades", "Cambió la portada de la actividad #{id}"),
            r("DELETE", "/api/app/ediciones/{id}/portada", "Actividades", "Quitó la portada de la actividad #{id}"),
            r("POST", "/api/app/ediciones/{id}/qr-pago", "Actividades", "Cambió el QR de pago de la actividad #{id}"),
            r("DELETE", "/api/app/ediciones/{id}/qr-pago", "Actividades", "Quitó el QR de pago de la actividad #{id}"),
            r("POST", "/api/app/ediciones/{id}/credencial/[A-Za-z]+", "Credenciales", "Preparó un diseño de credencial para la actividad #{id}"),
            r("POST", "/api/app/categorias", "Plano y precios", "Creó una categoría"),
            r("PATCH", "/api/app/categorias/{id}", "Plano y precios", "Modificó la categoría #{id}"),
            r("PATCH", "/api/app/categorias/{id}/cantidad", "Plano y precios", "Cambió la cantidad de espacios de la categoría #{id}"),
            r("DELETE", "/api/app/categorias/{id}", "Plano y precios", "Eliminó la categoría #{id}"),
            r("POST", "/api/app/categorias/{id}/opciones", "Plano y precios", "Agregó una opción de precio a la categoría #{id}"),
            r("PATCH", "/api/app/categorias/{id}/opciones/{n}", "Plano y precios", "Modificó una opción de precio de la categoría #{id}"),
            r("DELETE", "/api/app/categorias/{id}/opciones/{n}", "Plano y precios", "Quitó una opción de precio de la categoría #{id}"),
            r("POST", "/api/app/puestos", "Plano y precios", "Agregó un espacio al plano"),
            r("DELETE", "/api/app/puestos/{id}", "Plano y precios", "Eliminó el espacio #{id}"),
            r("POST", "/api/app/puestos/posiciones", "Plano y precios", "Movió espacios en el plano"),
            r("PATCH", "/api/app/puestos/precios", "Plano y precios", "Cambió precios de espacios"),
            r("PATCH", "/api/app/puestos/codigos", "Plano y precios", "Cambió números de espacios"),
            r("POST", "/api/app/puestos/{id}/bloquear", "Plano y precios", "Bloqueó el espacio #{id}"),
            r("POST", "/api/app/puestos/{id}/desbloquear", "Plano y precios", "Desbloqueó el espacio #{id}"),
            r("PATCH", "/api/app/puestos/{id}/.*", "Plano y precios", "Modificó el espacio #{id}"),
            r("POST", "/api/app/plano", "Plano y precios", "Cambió el fondo del plano"),
            r("DELETE", "/api/app/plano", "Plano y precios", "Quitó el fondo del plano"),
            r("PATCH", "/api/app/plano/forma", "Plano y precios", "Cambió la forma del plano"),
            r("POST", "/api/app/plano-textos", "Plano y precios", "Agregó un texto o zona al plano"),
            r("PATCH", "/api/app/plano-textos/{id}", "Plano y precios", "Modificó un texto del plano"),
            r("DELETE", "/api/app/plano-textos/{id}", "Plano y precios", "Quitó un texto del plano"),
            r("POST", "/api/app/entradas/{id}/aprobar", "Ventas y cobros", "Aprobó la compra de entradas #{id}"),
            r("POST", "/api/app/entradas/{id}/rechazar", "Ventas y cobros", "Rechazó la compra de entradas #{id}"),
            r("POST", "/api/app/entradas/{id}/whatsapp", "Ventas y cobros", "Envió por WhatsApp las entradas de la compra #{id}"),
            r("PUT", "/api/app/ediciones/{id}/entrada-diseno", "Actividades", "Cambió el diseño de la entrada de la actividad #{id}"),
            r("POST", "/api/app/ediciones/{id}/entrada-diseno/fondo", "Actividades", "Subió el fondo de la entrada de la actividad #{id}"),
            r("POST", "/api/app/entradas/{id}/anular", "Ventas y cobros", "Anuló la compra de entradas #{id}"),
            r("POST", "/api/app/entradas/oficina/{id}", "Punto de venta", "Vendió entradas de la actividad #{id}"),
            r("POST", "/api/app/interesados-stand/{id}/aprobar-venta", "Ventas y cobros", "Aprobó la solicitud de espacio #{id} y registró la venta"),
            r("PATCH", "/api/app/interesados-stand/{id}/estado", "Ventas y cobros", "Cambió el estado de la solicitud de espacio #{id}"),
            r("PATCH", "/api/app/interesados-stand/{id}", "Ventas y cobros", "Cambió la categoría de la solicitud #{id}"),
            r("POST", "/api/app/interesados-stand/{id}/comprobante", "Ventas y cobros", "Reemplazó el comprobante de la solicitud #{id}"),
            r("POST", "/api/app/llegadas/{id}", "Recepción e ingreso", "Confirmó la llegada de la venta #{id}"),
            r("DELETE", "/api/app/llegadas/{id}", "Recepción e ingreso", "Quitó la llegada de la venta #{id}"),
            r("POST", "/api/app/punto-venta/espacios", "Punto de venta", "Vendió espacios en el punto de venta"),
            r("POST", "/api/app/inscripciones", "Punto de venta", "Registró una venta"),
            r("POST", "/api/app/inscripciones/{id}/cancelar", "Ventas y cobros", "Anuló la venta #{id}"),
            r("POST", "/api/app/inscripciones/{id}/solicitar-cancelacion", "Ventas y cobros", "Pidió anular la venta #{id}"),
            r("POST", "/api/app/inscripciones/{id}/comprobante", "Ventas y cobros", "Adjuntó el comprobante de la venta #{id}"),
            r("PATCH", "/api/app/inscripciones/{id}/entidad", "Ventas y cobros", "Corrigió los datos del comprador de la venta #{id}"),
            r("POST", "/api/app/inscripciones/{id}/responsables", "Ventas y cobros", "Agregó una persona a la venta #{id}"),
            r("PATCH", "/api/app/inscripciones/{id}/responsables/{n}", "Ventas y cobros", "Corrigió una persona de la venta #{id}"),
            r("(POST|DELETE)", "/api/app/inscripciones/{id}/responsables/{n}/foto", "Ventas y cobros", "Cambió la foto de una persona de la venta #{id}"),
            r("POST", "/api/app/solicitudes-cancelacion/{id}/aprobar", "Ventas y cobros", "Aprobó la anulación pedida #{id}"),
            r("POST", "/api/app/solicitudes-cancelacion/{id}/rechazar", "Ventas y cobros", "Rechazó la anulación pedida #{id}"),
            r("POST", "/api/app/credenciales/whatsapp", "Credenciales", "Envió credenciales por WhatsApp"),
            r("POST", "/api/app/credenciales/pdf(-2up)?", "Credenciales", "Generó un PDF de credenciales"),
            r("POST", "/api/app/credenciales/{id}/foto", "Credenciales", "Cambió la foto de la credencial #{id}"),
            r("POST", "/api/app/impresion-masiva/pdf", "Credenciales", "Imprimió credenciales en lote"),
            r("POST", "/api/app/plantillas-credencial", "Credenciales", "Creó un diseño de credencial"),
            r("PATCH", "/api/app/plantillas-credencial/{id}", "Credenciales", "Modificó el diseño de credencial #{id}"),
            r("POST", "/api/app/plantillas-credencial/{id}/duplicar", "Credenciales", "Duplicó el diseño de credencial #{id}"),
            r("DELETE", "/api/app/plantillas-credencial/{id}", "Credenciales", "Eliminó el diseño de credencial #{id}"),
            r("POST", "/api/app/personal-apoyo.*", "Credenciales", "Registró o imprimió personal de apoyo"),
            r("(PATCH|DELETE)", "/api/app/personal-apoyo.*", "Credenciales", "Modificó el personal de apoyo"),
            r("POST", "/api/app/usuarios", "Usuarios y permisos", "Creó un usuario"),
            r("PATCH", "/api/app/usuarios/{id}", "Usuarios y permisos", "Modificó el usuario #{id}"),
            r("PATCH", "/api/app/usuarios/{id}/password", "Usuarios y permisos", "Cambió la contraseña del usuario #{id}"),
            r("PATCH", "/api/app/usuarios/{id}/estado", "Usuarios y permisos", "Activó o desactivó el usuario #{id}"),
            r("DELETE", "/api/app/usuarios/{id}", "Usuarios y permisos", "Eliminó el usuario #{id}"),
            r("POST", "/api/app/roles", "Usuarios y permisos", "Creó un rol"),
            r("PATCH", "/api/app/roles/{id}", "Usuarios y permisos", "Modificó el rol #{id}"),
            r("DELETE", "/api/app/roles/{id}", "Usuarios y permisos", "Eliminó el rol #{id}"),
            r("PUT", "/api/app/permisos/usuario/{id}", "Usuarios y permisos", "Cambió los permisos del usuario #{id}"),
            r("PUT", "/api/app/permisos/{id}", "Usuarios y permisos", "Cambió los permisos del rol #{id}"),
            r("POST", "/api/app/personas", "Usuarios y permisos", "Registró una persona"),
            r("(PATCH|DELETE)", "/api/app/personas/{id}", "Usuarios y permisos", "Modificó la persona #{id}"),
            r("(POST|PUT|DELETE)", "/api/app/vendedores/.*", "Usuarios y permisos", "Cambió los espacios asignados a un vendedor"),
            r("POST", "/api/app/anuncios", "Comunicación", "Publicó en la página o para el equipo"),
            r("PUT", "/api/app/anuncios/{id}", "Comunicación", "Modificó la publicación #{id}"),
            r("DELETE", "/api/app/anuncios/{id}", "Comunicación", "Retiró la publicación #{id}"),
            r("(POST|PUT|PATCH)", "/api/app/whatsapp/.*", "Sistema", "Cambió la configuración de WhatsApp"),
            r("PUT", "/api/app/mantenimiento", "Sistema", "Cambió el modo de mantenimiento"),
            r("DELETE", "/api/app/errores", "Sistema", "Vació el registro de errores"));

    private final HistorialService historial;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String metodo = request.getMethod();
        if (!METODOS.contains(metodo)) return;
        String ruta = request.getRequestURI();
        String contexto = request.getContextPath();
        if (contexto != null && !contexto.isEmpty() && ruta.startsWith(contexto)) ruta = ruta.substring(contexto.length());
        if (!ruta.startsWith("/api/app/")) return;
        for (Pattern p : IGNORADAS) if (p.matcher(ruta).matches()) return;
        int estado = response.getStatus();
        if (estado == 401 || estado == 403) return;

        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        JwtUser usuario = a != null && a.getPrincipal() instanceof JwtUser u ? u : null;

        String modulo = "Otros";
        String accion = metodo + " " + ruta;
        Long registro = null;
        for (Regla regla : REGLAS) {
            if (!metodo.matches(regla.metodo())) continue;
            Matcher m = regla.ruta().matcher(ruta);
            if (!m.matches()) continue;
            modulo = regla.modulo();
            String id = m.groupCount() > 0 && m.group(1) != null && m.group(1).matches("\\d+") ? m.group(1) : null;
            accion = id == null ? regla.texto().replace(" #{id}", "") : regla.texto().replace("{id}", id);
            if (id != null) registro = Long.valueOf(id);
            break;
        }
        boolean ok = ex == null && estado < 400;
        Map<String, Object> detalle = new LinkedHashMap<>();
        Object cambios = request.getAttribute(HistorialService.ATRIBUTO_CAMBIOS);
        if (cambios instanceof Map<?, ?> c && !c.isEmpty()) detalle.put("cambios", c);
        Object resumen = request.getAttribute(HistorialService.ATRIBUTO_RESUMEN);
        if (resumen == null && registro != null && ruta.startsWith("/api/app/ediciones/")) resumen = historial.nombreDeActividad(registro);
        if (resumen != null) detalle.put("resumen", resumen);
        if (!ok) {
            Object mensaje = request.getAttribute(HistorialService.ATRIBUTO_ERROR);
            detalle.put("error", mensaje != null ? String.valueOf(mensaje) : ex != null ? ex.getClass().getSimpleName() : "HTTP " + estado);
        }

        String reenviado = request.getHeader("X-Forwarded-For");
        String ip = reenviado != null && !reenviado.isBlank() ? reenviado.split(",")[0].trim() : request.getRemoteAddr();
        historial.registrar(new HistorialService.Entrada(usuario == null ? null : usuario.id(),
                usuario == null ? null : usuario.username(), usuario == null ? null : usuario.rol(), modulo, accion,
                metodo, ruta, registro, estado, ok, ip, detalle));
    }
}

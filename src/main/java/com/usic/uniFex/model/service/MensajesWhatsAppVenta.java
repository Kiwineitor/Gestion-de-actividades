package com.usic.uniFex.model.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MensajesWhatsAppVenta {

    private record Plantilla(String encabezado, String cuerpo, String conCredenciales, String soloRecibo, String despedida) {
    }

    private static final List<Plantilla> PLANTILLAS = List.of(
            new Plantilla("🎉 ¡Bienvenido a {actividad}!", "Tu compra quedó registrada con éxito.",
                    "📎 Te enviamos tu recibo en PDF y tus credenciales virtuales.",
                    "📎 Te enviamos tu recibo en PDF.", "¡Te esperamos!"),
            new Plantilla("✅ ¡Tu lugar en {actividad} está confirmado!", "Registramos correctamente tu compra.",
                    "📎 A continuación recibirás el recibo y las credenciales virtuales.",
                    "📎 A continuación recibirás tu recibo.", "¡Nos vemos pronto!"),
            new Plantilla("🌟 ¡Gracias por ser parte de {actividad}!", "Tu registro se completó correctamente.",
                    "📎 Adjuntamos el recibo y las credenciales correspondientes.",
                    "📎 Adjuntamos tu recibo de compra.", "¡Te esperamos!"),
            new Plantilla("🎪 ¡Ya formas parte de {actividad}!", "Confirmamos que tu compra fue registrada.",
                    "📎 En los siguientes mensajes encontrarás tu recibo y tus credenciales virtuales.",
                    "📎 En el siguiente mensaje encontrarás tu recibo.", "¡Nos vemos allí!"),
            new Plantilla("🙌 ¡Compra completada con éxito!", "Nos alegra contar contigo en {actividad}.",
                    "📎 Te compartimos el recibo en PDF y las credenciales virtuales de tu registro.",
                    "📎 Te compartimos el recibo en PDF de tu compra.", "¡Te esperamos!"),
            new Plantilla("📣 ¡Registro confirmado para {actividad}!", "Tu compra ya se encuentra registrada.",
                    "📎 Recibirás enseguida el recibo y las credenciales virtuales.",
                    "📎 Recibirás enseguida tu recibo.", "¡Gracias por tu confianza!"),
            new Plantilla("🎊 ¡Te damos la bienvenida a {actividad}!", "Tu compra concluyó satisfactoriamente.",
                    "📎 Encontrarás adjuntos tu recibo y las credenciales virtuales.",
                    "📎 Encontrarás adjunto tu recibo.", "¡Te esperamos!"),
            new Plantilla("💫 ¡Todo listo para {actividad}!", "Tu compra fue procesada correctamente.",
                    "📎 Te enviaremos el recibo de compra y las credenciales virtuales.",
                    "📎 Te enviaremos el recibo de tu compra.", "¡Nos vemos pronto!"),
            new Plantilla("🤝 ¡Gracias por sumarte a {actividad}!", "Confirmamos el registro exitoso de tu compra.",
                    "📎 En este chat recibirás el recibo en PDF y tus credenciales virtuales.",
                    "📎 En este chat recibirás tu recibo en PDF.", "¡Te esperamos!"),
            new Plantilla("🏷️ ¡Tu espacio en {actividad} está registrado!", "Completamos correctamente tu compra.",
                    "📎 A continuación te compartimos el recibo y las credenciales virtuales.",
                    "📎 A continuación te compartimos tu recibo.", "¡Te esperamos!"));

    private static final String CIERRE = String.join("\n",
            "Responde este mensaje para confirmar la entrega.",
            "Registra este número para estar al tanto de las novedades de {actividad}.");

    private final SecureRandom aleatorio = new SecureRandom();
    private final List<Integer> pendientes = new ArrayList<>();
    private Integer ultima;
    private JdbcTemplate jdbc;

    @Autowired(required = false)
    void setJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String paraVenta(Long inscripcionId, String nombreCliente, boolean conCredenciales) {
        String actividad = null;
        if (jdbc != null && inscripcionId != null) {
            try {
                List<String> nombres = jdbc.queryForList("""
                        SELECT e.nombre FROM inscripcion i JOIN edicion e ON e.id = i.id_edicion WHERE i.id = ?
                        """, String.class, inscripcionId);
                actividad = nombres.isEmpty() ? null : nombres.getFirst();
            } catch (RuntimeException e) {
                actividad = null;
            }
        }
        return siguiente(nombreCliente, actividad, conCredenciales);
    }

    public synchronized String siguiente(String nombreCliente, String actividad, boolean conCredenciales) {
        if (pendientes.isEmpty()) prepararRonda();
        int indice = pendientes.removeLast();
        ultima = indice;
        Plantilla p = PLANTILLAS.get(indice);
        String nombre = nombreCliente == null || nombreCliente.isBlank() ? "cliente" : nombreCliente.trim();
        String evento = actividad == null || actividad.isBlank() ? "la actividad" : actividad.trim();
        String texto = String.join("\n", p.encabezado(), "", "Hola " + nombre + ",", p.cuerpo(), "",
                conCredenciales ? p.conCredenciales() : p.soloRecibo(), p.despedida()) + "\n\n" + CIERRE;
        return texto.replace("{actividad}", evento);
    }

    private void prepararRonda() {
        for (int i = 0; i < PLANTILLAS.size(); i++) pendientes.add(i);
        Collections.shuffle(pendientes, aleatorio);
        if (ultima != null && pendientes.getLast().equals(ultima)) {
            Collections.swap(pendientes, pendientes.size() - 1, pendientes.size() - 2);
        }
    }

    static int cantidadVariantes() {
        return PLANTILLAS.size();
    }
}

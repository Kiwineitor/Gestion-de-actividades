package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.entity.Usuario;
import com.usic.uniFex.security.AccesoSeguro;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SeguridadAccesoTest {

    @Autowired private MockMvc mvc;
    @Autowired private AccesoSeguro acceso;
    @Autowired private IUsuarioDao usuarios;
    @Autowired private com.usic.uniFex.model.dao.IPersonasDao personas;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    private final String nombre = "prueba" + UUID.randomUUID().toString().substring(0, 8);
    private final String claveInicial = "Inicial-2026x";
    private Long usuarioId;
    private Long personaId;

    @BeforeEach
    void preparar() {
        personaId = jdbc.queryForObject("""
                INSERT INTO persona (nombre, paterno, ci, "_estado", "_fecha_registro") VALUES ('PRUEBA', 'SEGURIDAD', ?, 'ACTIVO', now()) RETURNING id
                """, Long.class, "S" + UUID.randomUUID().toString().substring(0, 10));
        Usuario u = new Usuario();
        u.setUsername(nombre);
        u.setPassword(encoder.encode(claveInicial));
        u.setPersona(personas.findById(personaId).orElseThrow());
        u.setRol(usuarios.findByUsername("admin1").orElseThrow().getRol());
        u.setEstado("ACTIVO");
        u.setDebeCambiarClave(true);
        usuarioId = usuarios.save(u).getId();
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM historial_accion WHERE id_usuario = ? OR usuario = ?", usuarioId, nombre);
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);
        jdbc.update("DELETE FROM persona WHERE id = ?", personaId);
    }

    private JsonNode login(String clave) throws Exception {
        String r = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"usuario\":\"" + nombre + "\",\"contrasena\":\"" + clave + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(r);
    }

    @Test
    void bloqueaTrasCincoIntentosFallidos() {
        for (int i = 0; i < 5; i++) assertThat(acceso.ingresar(nombre, "incorrecta" + i, "10.9.9.9").estado()).isEqualTo(401);
        AccesoSeguro.Ingreso bloqueado = acceso.ingresar(nombre, claveInicial, "10.9.9.9");
        assertThat(bloqueado.estado()).isEqualTo(429);
        assertThat(bloqueado.mensaje()).contains("espera");
    }

    @Test
    void unaContrasenaPegadaEnLaBaseNoSirve() {
        jdbc.update("UPDATE usuario SET password = ? WHERE id = ?", encoder.encode("HackeadaDesdeLaBase1"), usuarioId);
        AccesoSeguro.Ingreso r = acceso.ingresar(nombre, "HackeadaDesdeLaBase1", "10.8.8.8");
        assertThat(r.ok()).isFalse();
        assertThat(r.codigo()).isEqualTo("ALTERADO");
        assertThat(acceso.restablecer(nombre, "Temporal-2026xx")).isTrue();
        assertThat(acceso.ingresar(nombre, "Temporal-2026xx", "10.8.8.8").ok()).isTrue();
    }

    @Test
    void elPrimerIngresoExigeTerminosYCambioDeClave() throws Exception {
        JsonNode r = login(claveInicial);
        assertThat(r.get("pendientes").toString()).contains("TERMINOS").contains("CLAVE");
        String token = r.get("token").asText();

        mvc.perform(get("/api/app/ediciones/resumen").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PRIMER_INGRESO"));

        String t2 = json.readTree(mvc.perform(post("/api/auth/aceptar-terminos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();

        mvc.perform(post("/api/auth/cambiar-clave").header("Authorization", "Bearer " + t2).contentType("application/json")
                .content("{\"actual\":\"" + claveInicial + "\",\"nueva\":\"corta1\"}")).andExpect(status().isUnprocessableEntity());
        String t3 = json.readTree(mvc.perform(post("/api/auth/cambiar-clave").header("Authorization", "Bearer " + t2).contentType("application/json")
                        .content("{\"actual\":\"" + claveInicial + "\",\"nueva\":\"MiFeria-Cobija2026\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();

        mvc.perform(get("/api/app/ediciones/resumen").header("Authorization", "Bearer " + t3)).andExpect(status().isOk());
        mvc.perform(get("/api/app/ediciones/resumen").header("Authorization", "Bearer " + t2)).andExpect(status().isUnauthorized());
        assertThat(login("MiFeria-Cobija2026").get("pendientes").size()).isZero();
    }

    @Test
    void desactivarAlUsuarioCierraSuSesion() throws Exception {
        jdbc.update("UPDATE usuario SET debe_cambiar_clave = FALSE, terminos_version = ? WHERE id = ?", AccesoSeguro.TERMINOS_VERSION, usuarioId);
        String token = login(claveInicial).get("token").asText();
        mvc.perform(get("/api/app/ediciones/resumen").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        Usuario u = usuarios.findById(usuarioId).orElseThrow();
        u.setEstado("INACTIVO");
        u.cerrarSesiones();
        usuarios.save(u);
        acceso.olvidarSesion(usuarioId);
        mvc.perform(get("/api/app/ediciones/resumen").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }
}

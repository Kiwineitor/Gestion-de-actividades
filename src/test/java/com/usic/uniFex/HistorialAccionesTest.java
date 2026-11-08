package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.entity.Usuario;
import com.usic.uniFex.security.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class HistorialAccionesTest {

    private static final String MARCA = "Prueba historial " + System.nanoTime();

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired IUsuarioDao usuarios;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager tx;

    private String token;
    private Long usuarioId;
    private Long actividad;
    private long ultimoAntes;

    @BeforeEach
    void preparar() {
        new TransactionTemplate(tx).executeWithoutResult(s -> {
            Usuario admin = usuarios.findByUsername("admin1").orElseThrow();
            usuarioId = admin.getId();
            token = "Bearer " + jwt.generar(admin);
        });
        ultimoAntes = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM historial_accion", Long.class);
    }

    @AfterEach
    void limpiar() throws Exception {
        if (actividad != null) {
            mvc.perform(delete("/api/app/ediciones/" + actividad).header("Authorization", token));
        }
        jdbc.update("DELETE FROM historial_accion WHERE id > ?", ultimoAntes);
    }

    @Test
    @SuppressWarnings("unchecked")
    void registraCreacionYCambiosConAntesYDespues() throws Exception {
        String creada = mvc.perform(post("/api/app/ediciones/asistente").header("Authorization", token)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("nombre", MARCA, "lugar", "Coliseo"))))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        actividad = ((Number) json.readValue(creada, Map.class).get("id")).longValue();

        mvc.perform(patch("/api/app/ediciones/" + actividad + "/configuracion").header("Authorization", token)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("lugar", "Plaza principal"))))
                .andExpect(status().is2xxSuccessful());

        String cuerpo = mvc.perform(get("/api/app/historial").param("q", MARCA).header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> filas = json.readValue(cuerpo, List.class);

        Map<String, Object> modificacion = filas.stream()
                .filter(f -> String.valueOf(f.get("accion")).startsWith("Modificó la configuración")).findFirst().orElseThrow();
        assertThat(modificacion.get("modulo")).isEqualTo("Actividades");
        assertThat(((Number) modificacion.get("usuarioId")).longValue()).isEqualTo(usuarioId);
        assertThat(modificacion.get("ok")).isEqualTo(true);
        assertThat(((Number) modificacion.get("registroId")).longValue()).isEqualTo(actividad);
        Map<String, Object> detalle = (Map<String, Object>) modificacion.get("detalle");
        assertThat(detalle.get("resumen")).isEqualTo(MARCA);
        Map<String, Object> cambios = (Map<String, Object>) detalle.get("cambios");
        assertThat(cambios).hasSize(1);
        Map<String, Object> lugar = (Map<String, Object>) cambios.values().iterator().next();
        assertThat(lugar.get("antes")).isEqualTo("Coliseo");
        assertThat(lugar.get("despues")).isEqualTo("Plaza principal");

        assertThat(filas).anyMatch(f -> "Creó una actividad".equals(f.get("accion")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void unaAccionRechazadaGuardaElMensajeQueVioElUsuario() throws Exception {
        mvc.perform(patch("/api/app/ediciones/999999999/configuracion").header("Authorization", token)
                        .contentType("application/json").content("{\"lugar\":\"x\"}"))
                .andExpect(status().is4xxClientError());
        Map<String, Object> fila = jdbc.queryForMap("SELECT ok, detalle FROM historial_accion WHERE id > ? ORDER BY id DESC LIMIT 1", ultimoAntes);
        assertThat(fila.get("ok")).isEqualTo(false);
        Map<String, Object> detalle = json.readValue(String.valueOf(fila.get("detalle")), Map.class);
        assertThat(String.valueOf(detalle.get("error"))).doesNotStartWith("HTTP ").isNotBlank();
    }

    @Test
    void soloLaAdministracionVeElHistorial() throws Exception {
        mvc.perform(get("/api/app/historial")).andExpect(status().isUnauthorized());
        String vendedor = new TransactionTemplate(tx).execute(s -> usuarios.findAll().stream()
                .filter(u -> u.getRol() != null && "ADMINISTRATIVO".equals(u.getRol().getNombre()))
                .findFirst().map(jwt::generar).orElse(null));
        if (vendedor != null) {
            mvc.perform(get("/api/app/historial").header("Authorization", "Bearer " + vendedor))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/app/historial/opciones").header("Authorization", token)).andExpect(status().isOk());
    }

    @Test
    void lasConsultasYLasReservasDelMapaNoSeRegistran() throws Exception {
        mvc.perform(get("/api/app/ediciones").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(post("/api/app/presencia").header("Authorization", token));
        Long nuevos = jdbc.queryForObject("SELECT count(*) FROM historial_accion WHERE id > ?", Long.class, ultimoAntes);
        assertThat(nuevos).isZero();
    }
}

package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.controller.publico.FeriaPublicaController;
import com.usic.uniFex.model.dao.IEdicionDao;
import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.service.ActividadService;
import com.usic.uniFex.model.service.CategoriaMapaService;
import com.usic.uniFex.model.service.PuestoMapaService;
import com.usic.uniFex.model.service.PlanoTextoService;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class OrganizarFeriaTest {
    @Autowired private ActividadService actividades;
    @Autowired private FeriaPublicaController publico;
    @Autowired private IEdicionDao ediciones;
    @Autowired private IPuestoDao puestos;
    @Autowired private PuestoMapaService mapa;
    @Autowired private PlanoTextoService textos;

    @Test
    void publicaElPlanoPropioConPrecioIndividualSinCambiarLaEdicionEnCurso() {
        var activa = ediciones.findFirstByActivaTrueOrderByAnioDesc().map(e -> e.getId());
        var categoria = new CategoriaMapaService.NuevaCategoria("Gastronomía", 2, "#3b82f6", null,
                null, new BigDecimal("300"), null, null, null, null);
        var datos = new ActividadService.Datos("Expo de prueba " + UUID.randomUUID(), 2026, "Feria", null,
                false, false, "Campo ferial", null, null, null, false, true,
                null, null, null, null, null, List.of(categoria));
        var resultado = actividades.crear(datos, null);
        assertThat(resultado.ok()).isTrue();
        Long feria = resultado.id();
        assertThat(publico.plano(feria)).containsEntry("disponible", false);
        assertThat(actividades.habilitar(feria, true).ok()).isFalse();

        var casetas = puestos.listarActivosDe(feria);
        assertThat(casetas).hasSize(2);
        Long puesto = casetas.getFirst().getId();
        mapa.guardarPosiciones(List.of(new PuestoMapaService.Posicion(puesto, 0.3, 0.4, 1.0, 0)));
        var rotulo = textos.crear(new PlanoTextoService.CambioTexto("Ingreso principal", 0.1, 0.2,
                0.02, "#111827", "#ffffff", 0.001, 0d, null, null, null), null, feria);
        assertThat(textos.listar(feria)).anyMatch(t -> t.id().equals(rotulo.id()));
        assertThat(textos.listar(null)).noneMatch(t -> t.id().equals(rotulo.id()));
        assertThat(mapa.actualizarPrecios(List.of(new PuestoMapaService.PrecioCaseta(puesto, new BigDecimal("240"))), null).ok())
                .isTrue();
        assertThat(actividades.habilitar(feria, true).ok()).isTrue();
        assertThat(ediciones.findFirstByActivaTrueOrderByAnioDesc().map(e -> e.getId())).isEqualTo(activa);
        var plano = publico.plano(feria);
        assertThat(plano).containsEntry("disponible", true);
        assertThat((List<?>) plano.get("textos")).hasSize(1);
        var publicados = (List<?>) plano.get("puestos");
        assertThat(publicados).hasSize(1);
        assertThat((BigDecimal) ((Map<?, ?>) publicados.getFirst()).get("precio")).isEqualByComparingTo("240");
        assertThat(publico.info(feria).get("edicion")).isInstanceOf(Map.class);
        assertThat(publico.edicionesPublicas()).anyMatch(f -> feria.equals(f.get("id")));
    }
}

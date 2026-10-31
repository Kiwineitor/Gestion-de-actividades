package com.usic.uniFex.controller.administracion;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.service.ReporteEventoService;
import com.usic.uniFex.model.service.TablaExportService;
import com.usic.uniFex.model.service.TablaExportService.Tabla;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/reportes-evento")
@RequiredArgsConstructor
public class ReporteEventoApiController {

    private static final String VE_REPORTE = Roles.VE_CONTROL_VENTAS + " or " + Roles.VALIDA_MESAS
            + " or @acceso.puede('reportes') or @acceso.puede('direccion')";

    private final ReporteEventoService servicio;
    private final TablaExportService exportador;

    @GetMapping("/{id}")
    @PreAuthorize(VE_REPORTE)
    public ResponseEntity<Map<String, Object>> resumen(@PathVariable Long id) {
        Map<String, Object> r = servicio.resumen(id);
        return r == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(r);
    }

    @GetMapping("/{id}/{formato}")
    @PreAuthorize(VE_REPORTE)
    public ResponseEntity<byte[]> descargar(@PathVariable Long id, @PathVariable String formato) {
        boolean pdf = "pdf".equalsIgnoreCase(formato);
        boolean excel = "excel".equalsIgnoreCase(formato) || "xlsx".equalsIgnoreCase(formato);
        if (!pdf && !excel) {
            return ResponseEntity.badRequest().body("Formato no soportado: usa pdf o excel".getBytes());
        }
        List<Tabla> tablas = servicio.tablas(id);
        if (tablas == null) return ResponseEntity.notFound().build();
        byte[] datos = pdf ? exportador.pdf(tablas) : exportador.excel(tablas);
        String archivo = "reporte-evento-" + id + (pdf ? ".pdf" : ".xlsx");
        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF
                        : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo + "\"")
                .body(datos);
    }
}

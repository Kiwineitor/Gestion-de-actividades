package com.usic.uniFex.controller.administracion;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dto.ControlVentasDTO;
import com.usic.uniFex.model.service.ControlVentasExportService;
import com.usic.uniFex.model.service.ControlVentasService;
import com.usic.uniFex.model.service.ControlVentasService.Filtros;
import com.usic.uniFex.security.AccesoPantallas;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/control-ventas")
@RequiredArgsConstructor
@PreAuthorize(Roles.VE_CONTROL_VENTAS)
public class ControlVentasApiController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ControlVentasService control;
    private final ControlVentasExportService exportacion;

    @GetMapping
    public ControlVentasDTO.Tablero tablero(
            @RequestParam(required = false) Long vendedor,
            @RequestParam(required = false) Long categoria,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q) {
        return control.tablero(filtrosDe(vendedor, categoria, desde, hasta, estado, q));
    }

    @GetMapping("/filtros")
    public Map<String, Object> filtros() {
        Map<String, Object> r = new LinkedHashMap<>(control.opcionesDeFiltro());
        Long soloDe = soloDe();
        if (soloDe != null) {
            @SuppressWarnings("unchecked")
            List<ControlVentasDTO.Opcion> todos = (List<ControlVentasDTO.Opcion>) r.get("vendedores");
            r.put("vendedores", todos.stream().filter(o -> soloDe.equals(o.id())).toList());
        }
        r.put("soloPropias", soloDe != null);
        r.put("puedeAdjuntar", esAdministracion() || soloDe != null);
        return r;
    }

    @GetMapping("/ventas")
    public List<ControlVentasDTO.Venta> ventas(
            @RequestParam(required = false) Long vendedor,
            @RequestParam(required = false) Long categoria,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q) {
        return control.ventas(filtrosDe(vendedor, categoria, desde, hasta, estado, q));
    }

    @GetMapping("/ventas/{id}")
    public ResponseEntity<?> ficha(@PathVariable Long id) {
        Long soloDe = soloDe();
        return control.ficha(id)
                .filter(f -> soloDe == null || soloDe.equals(f.venta().getVendedorId()))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(Map.of("ok", false, "mensaje", "La venta no existe, está anulada o es de otra edición")));
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(
            @RequestParam(required = false) Long vendedor,
            @RequestParam(required = false) Long categoria,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q) {
        byte[] datos = exportacion.excel(filtrosDe(vendedor, categoria, desde, hasta, estado, q));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"control-ventas.xlsx\"")
                .body(datos);
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(
            @RequestParam(required = false) Long vendedor,
            @RequestParam(required = false) Long categoria,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q) {
        Filtros f = filtrosDe(vendedor, categoria, desde, hasta, estado, q);
        byte[] datos = exportacion.pdf(f);
        String archivo = f.vendedorId() == null ? "control-ventas.pdf" : "rendicion-vendedor-" + f.vendedorId() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo + "\"")
                .body(datos);
    }

    private static Filtros filtrosDe(Long vendedor, Long categoria, LocalDate desde, LocalDate hasta,
                                     String estado, String q) {
        Long soloDe = soloDe();
        return new Filtros(soloDe != null ? soloDe : vendedor, categoria, desde, hasta, estado, q);
    }

    private static Long soloDe() {
        return AccesoPantallas.soloDe(Roles.AUTORIDADES_VEN_REPORTES);
    }

    private static boolean esAdministracion() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream()
                .anyMatch(g -> Roles.AUTORIDADES_ADMINISTRA.contains(g.getAuthority()));
    }
}

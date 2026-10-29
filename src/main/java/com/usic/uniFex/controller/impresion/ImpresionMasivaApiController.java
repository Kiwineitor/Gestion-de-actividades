package com.usic.uniFex.controller.impresion;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dto.CredencialDTO;
import com.usic.uniFex.model.dto.PlantillaCredencial;
import com.usic.uniFex.model.service.CredencialPdfService;
import com.usic.uniFex.model.service.CredencialService;
import com.usic.uniFex.model.service.ImpresionMasivaService;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/impresion-masiva")
@RequiredArgsConstructor
@PreAuthorize(Roles.ADMINISTRA)
public class ImpresionMasivaApiController {

    private final ImpresionMasivaService impresion;
    private final CredencialPdfService pdfService;
    private final CredencialService credencialService;

    @GetMapping
    public List<CredencialDTO> listar(@RequestParam(required = false) Long vendedorId) {
        if (vendedorId != null) {
            return impresion.listarDeVendedor(vendedorId);
        }
        return impresion.listarTodas();
    }

    @PostMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(
            @RequestBody(required = false) PeticionPdf req) {

        List<CredencialDTO> credenciales = (req == null || req.responsables() == null || req.responsables().isEmpty())
                ? impresion.listarTodas()
                : impresion.porResponsables(req.responsables());

        if (credenciales.isEmpty()) {
            return ResponseEntity.status(409)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("No hay credenciales de expositores para imprimir."
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        var conCodigo = credenciales.stream().map(c -> {
            String codigo = credencialService.codigos().codigoDe(c.responsableId());
            return new CredencialDTO(
                    c.responsableId(), codigo, c.nombre(), c.ci(), c.fotoUrl(),
                    c.esTitular(), c.entidad(), c.rubro(), c.categoria(), c.categoriaId(),
                    c.casetas(), c.inscripcionId(), c.conComprobante(), c.conFoto(),
                    c.esExtra(), c.montoExtra(), c.comprobanteExtraUrl(), c.sinCosto(), c.cargo());
        }).toList();

        byte[] pdf = pdfService.generarDuplex4(
                conCodigo,
                PlantillaCredencial.actual(PlantillaCredencial.EXPOSITOR),
                CredencialPdfService.CRED_ANCHO_CM,
                CredencialPdfService.CRED_ALTO_CM,
                "https://fexpo-uapv2.uap.edu.bo/app",
                credencialService.codigos());

        String nombre = "credenciales-duplex-a4-" + credenciales.size() + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"" + nombre + "\"")
                .body(pdf);
    }

    public record PeticionPdf(@com.fasterxml.jackson.annotation.JsonAlias("ids") List<Long> responsables) {
    }
}

package com.usic.uniFex.model.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.ICredencialDao;
import com.usic.uniFex.model.dto.CredencialDTO;
import com.usic.uniFex.model.dto.CredencialFilaView;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ImpresionMasivaService {

    private final ICredencialDao dao;

    @Transactional(readOnly = true)
    public List<CredencialDTO> listarTodas() {
        return dao.listarDeEdicionActiva().stream()
                .map(this::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CredencialDTO> listarDeVendedor(Long vendedorId) {
        return dao.listarDeEdicionActivaDe(vendedorId).stream()
                .map(this::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CredencialDTO> porResponsables(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return ids.stream()
                .map(dao::buscarPorResponsable)
                .flatMap(Optional::stream)
                .map(this::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CredencialDTO> porInscripcion(Long inscripcionId) {
        if (inscripcionId == null) return List.of();
        return listarTodas().stream()
                .filter(c -> inscripcionId.equals(c.inscripcionId()))
                .toList();
    }

    private CredencialDTO de(CredencialFilaView f) {
        String foto = f.getFoto();
        boolean conFoto = foto != null && !foto.isBlank();

        return new CredencialDTO(
                f.getResponsableId(),
                null,
                nombreCompleto(f),
                limpio(f.getCi()),
                foto != null && !foto.isBlank() ? "/files/" + foto : null,
                Boolean.TRUE.equals(f.getEsTitular()),
                limpio(f.getEntidad()),
                limpio(f.getRubro()),
                limpio(f.getCategorias()),
                f.getCategoriaId(),
                ordenarCasetas(f.getCasetas()),
                f.getInscripcionId(),
                true,
                conFoto,
                Boolean.TRUE.equals(f.getEsExtra()),
                f.getMontoExtra(),
                (f.getComprobanteExtra() != null && !f.getComprobanteExtra().isBlank())
                        ? "/files/" + f.getComprobanteExtra() : null,
                false,
                limpio(f.getCargo()));
    }

    private static String nombreCompleto(CredencialFilaView f) {
        return Stream.of(f.getNombre(), f.getPaterno(), f.getMaterno())
                .map(CredencialService::limpio)
                .filter(s -> s != null && !s.isEmpty())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }

    private static String ordenarCasetas(String crudo) {
        if (crudo == null || crudo.isBlank()) return "";
        return Stream.of(crudo.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .sorted((a, b) -> {
                    try {
                        return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
                    } catch (NumberFormatException e) {
                        return a.compareTo(b);
                    }
                })
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private static String limpio(String s) {
        return s == null ? "" : s.trim();
    }
}
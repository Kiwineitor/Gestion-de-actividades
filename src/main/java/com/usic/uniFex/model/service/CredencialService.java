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
public class CredencialService {

    private final ICredencialDao dao;
    private final CredencialCodigoService codigos;

    public CredencialCodigoService codigos() {
        return codigos;
    }

    @Transactional(readOnly = true)
    public List<CredencialDTO> listar() {
        return listar(null);
    }

    @Transactional(readOnly = true)
    public List<CredencialDTO> listar(Long soloDeUsuario) {
        var filas = soloDeUsuario == null
                ? dao.listarDeEdicionActiva()
                : dao.listarDeEdicionActivaDe(soloDeUsuario);
        return filas.stream().map(this::de).toList();
    }

    @Transactional(readOnly = true)
    public List<CredencialDTO> listarAptas(String plantilla) {
        return listarAptas(plantilla, null);
    }

    @Transactional(readOnly = true)
    public List<CredencialDTO> listarAptas(String plantilla, Long soloDeUsuario) {
        return listar(soloDeUsuario).stream().filter(c -> c.apto(plantilla)).toList();
    }

    @Transactional(readOnly = true)
    public boolean esDeUsuario(Long responsableId, Long usuarioId) {
        return responsableId != null && usuarioId != null
                && dao.esDeUsuario(responsableId, usuarioId);
    }

    @Transactional(readOnly = true)
    public Optional<CredencialDTO> porResponsable(Long responsableId) {
        return dao.buscarPorResponsable(responsableId).map(this::de);
    }

    @Transactional(readOnly = true)
    public Optional<CredencialDTO> porCodigo(String codigo) {
        Long id = codigos.responsableDe(codigo);
        return id == null ? Optional.empty() : porResponsable(id);
    }

    @Transactional(readOnly = true)
    public List<CredencialDTO> porResponsables(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return ids.stream()
                .map(this::porResponsable)
                .flatMap(Optional::stream)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CredencialDTO> porInscripcion(Long inscripcionId) {
        if (inscripcionId == null) return List.of();
        return listar(null).stream()
                .filter(c -> inscripcionId.equals(c.inscripcionId()))
                .toList();
    }

    private CredencialDTO de(CredencialFilaView f) {
        boolean conComprobante = f.getComprobante() != null && !f.getComprobante().isBlank();
        String foto = f.getFoto();
        boolean conFoto = foto != null && !foto.isBlank();

        return new CredencialDTO(
                f.getResponsableId(),
                codigos.codigoDe(f.getResponsableId()),
                nombreCompleto(f),
                limpio(f.getCi()),
                conFoto ? "/files/" + foto : null,
                Boolean.TRUE.equals(f.getEsTitular()),
                limpio(f.getEntidad()),
                limpio(f.getRubro()),
                limpio(f.getCategorias()),
                f.getCategoriaId(),
                ordenarCasetas(f.getCasetas()),
                f.getInscripcionId(),
                conComprobante,
                conFoto,
                Boolean.TRUE.equals(f.getEsExtra()),
                f.getMontoExtra(),
                (f.getComprobanteExtra() != null && !f.getComprobanteExtra().isBlank())
                        ? "/files/" + f.getComprobanteExtra() : null,
                f.getTotalVenta() == null || f.getTotalVenta().signum() <= 0,
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

public static String limpio(String s) {
        return s == null ? "" : s.trim();
    }
}

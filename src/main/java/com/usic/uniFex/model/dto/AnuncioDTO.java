package com.usic.uniFex.model.dto;

import java.time.LocalDateTime;

import com.usic.uniFex.model.entity.Anuncio;

public record AnuncioDTO(
        Long id,
        String titulo,
        String mensaje,
        String nivel,
        LocalDateTime vigenteHasta,
        LocalDateTime publicado,
        String publicadoPor,
        boolean activo) {

    public static AnuncioDTO de(Anuncio a, String autor) {
        return new AnuncioDTO(
                a.getId(), a.getTitulo(), a.getMensaje(), a.getNivel(), a.getVigenteHasta(),
                a.getRegistro() == null ? null
                        : LocalDateTime.ofInstant(a.getRegistro().toInstant(),
                                                  java.time.ZoneId.systemDefault()),
                autor,
                Anuncio.PUBLICADO.equals(a.getEstado()));
    }

    public static AnuncioDTO retirado(Long id) {
        return new AnuncioDTO(id, null, null, null, null, null, null, false);
    }
}

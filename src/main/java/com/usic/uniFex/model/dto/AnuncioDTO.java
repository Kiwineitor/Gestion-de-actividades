package com.usic.uniFex.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.usic.uniFex.model.entity.Anuncio;

public record AnuncioDTO(
        Long id,
        String titulo,
        String mensaje,
        String nivel,
        LocalDateTime vigenteHasta,
        LocalDateTime publicado,
        String publicadoPor,
        boolean activo,
        String destino,
        LocalDateTime vigenteDesde,
        String enlace,
        String formato,
        String imagenUrl,
        Long edicionId,
        String edicionNombre,
        BigDecimal precio,
        BigDecimal precioAntes,
        List<String> incluye,
        String botonTexto,
        String botonAccion,
        String contacto,
        int vistas,
        int clics) {

    public static AnuncioDTO de(Anuncio a, String autor) {
        return de(a, autor, null, null);
    }

    public static AnuncioDTO de(Anuncio a, String autor, String edicionNombre, String contacto) {
        return new AnuncioDTO(
                a.getId(), a.getTitulo(), a.getMensaje(), a.getNivel(), a.getVigenteHasta(),
                a.getRegistro() == null ? null
                        : LocalDateTime.ofInstant(a.getRegistro().toInstant(),
                                                  java.time.ZoneId.systemDefault()),
                autor,
                Anuncio.PUBLICADO.equals(a.getEstado()),
                a.getDestino() == null ? Anuncio.INTERNO : a.getDestino(),
                a.getVigenteDesde(),
                a.getEnlace(),
                a.getFormato() == null ? Anuncio.FORMATO_AVISO : a.getFormato(),
                a.getImagen() == null ? null : "/files/" + a.getImagen(),
                a.getEdicionId(),
                edicionNombre,
                a.getPrecio(),
                a.getPrecioAntes(),
                lineas(a.getIncluye()),
                a.getBotonTexto(),
                a.getBotonAccion() == null ? Anuncio.BOTON_NINGUNO : a.getBotonAccion(),
                contacto,
                a.getVistas() == null ? 0 : a.getVistas(),
                a.getClics() == null ? 0 : a.getClics());
    }

    public static AnuncioDTO retirado(Long id) {
        return new AnuncioDTO(id, null, null, null, null, null, null, false, null, null, null, null, null, null, null,
                null, null, List.of(), null, null, null, 0, 0);
    }

    private static List<String> lineas(String texto) {
        if (texto == null || texto.isBlank()) return List.of();
        return texto.lines().map(String::trim).filter(l -> !l.isEmpty()).toList();
    }
}

package com.usic.uniFex.controller.publico;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dto.AnuncioDTO;
import com.usic.uniFex.model.service.AnuncioService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/publico/anuncios")
@RequiredArgsConstructor
public class AnuncioPublicoController {

    private final AnuncioService anuncios;

    @GetMapping
    public List<AnuncioDTO> vigentes() {
        return anuncios.publicos();
    }

    @PostMapping("/{id}/vista")
    public ResponseEntity<Void> vista(@PathVariable Long id) {
        anuncios.contar(id, false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/clic")
    public ResponseEntity<Void> clic(@PathVariable Long id) {
        anuncios.contar(id, true);
        return ResponseEntity.noContent().build();
    }
}

package com.usic.uniFex.controller.mesas;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.service.MesaService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/publico/mesas")
@RequiredArgsConstructor
public class MesaPublicaController {

    private final MesaService mesas;

    public record PeticionReserva(Long mesaId, MesaService.Cliente cliente) {
    }

    @GetMapping
    public List<Map<String, Object>> eventos() {
        return mesas.eventosPublicos();
    }

    @GetMapping("/eventos/{id}")
    public ResponseEntity<Map<String, Object>> evento(@PathVariable Long id) {
        Map<String, Object> e = mesas.eventoPublico(id);
        return e == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(e);
    }

    @PostMapping("/eventos/{id}/reservas")
    public ResponseEntity<Map<String, Object>> reservar(@PathVariable Long id, @RequestBody PeticionReserva req,
                                                        HttpServletRequest http) {
        if (req == null || req.mesaId() == null) return responder(new MesaService.Resultado(false, "Elige una mesa", null));
        return responder(mesas.reservar(id, req.mesaId(), req.cliente(), http.getRemoteAddr()));
    }

    @GetMapping("/reservas/{codigo}")
    public ResponseEntity<Map<String, Object>> reserva(@PathVariable String codigo) {
        Map<String, Object> r = mesas.consultarReserva(codigo);
        return r == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(r);
    }

    @PostMapping(value = "/reservas/{codigo}/comprobante", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> comprobante(@PathVariable String codigo,
                                                           @RequestParam("archivo") MultipartFile archivo) throws IOException {
        return responder(mesas.adjuntarComprobante(codigo, archivo));
    }

    @PostMapping("/reservas/{codigo}/cancelar")
    public ResponseEntity<Map<String, Object>> cancelar(@PathVariable String codigo) {
        return responder(mesas.cancelarPorCliente(codigo));
    }

    @ExceptionHandler(MesaService.Rechazo.class)
    public ResponseEntity<Map<String, Object>> rechazo(MesaService.Rechazo e) {
        return responder(new MesaService.Resultado(false, e.getMessage(), null));
    }

    static ResponseEntity<Map<String, Object>> responder(MesaService.Resultado r) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        if (r.datos() != null) cuerpo.putAll(r.datos());
        return ResponseEntity.status(r.ok() ? 200 : 409).body(cuerpo);
    }
}

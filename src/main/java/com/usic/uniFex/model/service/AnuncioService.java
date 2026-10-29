package com.usic.uniFex.model.service;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IAnuncioDao;
import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.dto.AnuncioDTO;
import com.usic.uniFex.model.entity.Anuncio;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Usuario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnuncioService {

    public static final String TOPIC = "/topic/anuncios";

    private static final Set<String> NIVELES = Set.of(Anuncio.INFO, Anuncio.AVISO, Anuncio.URGENTE);

    private final IAnuncioDao dao;
    private final IUsuarioDao usuarioDao;
    private final SimpMessagingTemplate messaging;

    public record NuevoAnuncio(String titulo, String mensaje, String nivel, Integer minutos) {
    }

    @Transactional(readOnly = true)
    public List<AnuncioDTO> vigentes() {
        return dao.vigentes(LocalDateTime.now()).stream().map(this::aDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AnuncioDTO> historial() {
        return dao.historial().stream().map(this::aDto).toList();
    }

    @Transactional
    public AnuncioDTO publicar(NuevoAnuncio req, Long usuarioId) {
        String mensaje = limpio(req.mensaje());
        if (mensaje == null) throw new IllegalArgumentException("El anuncio necesita un mensaje");

        Anuncio a = new Anuncio();
        a.setTitulo(limpio(req.titulo()));
        a.setMensaje(mensaje);
        a.setNivel(NIVELES.contains(req.nivel()) ? req.nivel() : Anuncio.INFO);
        if (req.minutos() != null && req.minutos() > 0) {
            a.setVigenteHasta(LocalDateTime.now().plusMinutes(req.minutos()));
        }
        a.setRegistro(new Date());
        a.setRegistroIdUsuario(usuarioId);
        a.setEstado(Anuncio.PUBLICADO);

        AnuncioDTO dto = aDto(dao.save(a));
        difundir(dto);
        log.info("Anuncio {} publicado por el usuario {} (nivel {})", dto.id(), usuarioId, dto.nivel());
        return dto;
    }

    @Transactional
    public boolean retirar(Long id, Long usuarioId) {
        Anuncio a = dao.findById(id).orElse(null);
        if (a == null || Anuncio.RETIRADO.equals(a.getEstado())) return false;
        a.setEstado(Anuncio.RETIRADO);
        a.setModificacion(new Date());
        a.setModificacionIdUsuario(usuarioId);
        dao.save(a);
        difundir(AnuncioDTO.retirado(id));
        log.info("Anuncio {} retirado por el usuario {}", id, usuarioId);
        return true;
    }

    private void difundir(AnuncioDTO dto) {
        try {
            messaging.convertAndSend(TOPIC, dto);
        } catch (Exception e) {
            log.warn("No se pudo difundir el anuncio {}: {}", dto.id(), e.getMessage());
        }
    }

    private AnuncioDTO aDto(Anuncio a) {
        String autor = null;
        if (a.getRegistroIdUsuario() != null) {
            Usuario u = usuarioDao.findById(a.getRegistroIdUsuario()).orElse(null);
            if (u != null) {
                Persona p = u.getPersona();
                String completo = p == null ? null : java.util.stream.Stream
                        .of(p.getNombre(), p.getPaterno())
                        .filter(x -> x != null && !x.isBlank())
                        .reduce((x, y) -> x + " " + y).orElse(null);
                autor = (completo == null || completo.isBlank()) ? u.getUsername() : completo;
            }
        }
        return AnuncioDTO.de(a, autor);
    }

    private static String limpio(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

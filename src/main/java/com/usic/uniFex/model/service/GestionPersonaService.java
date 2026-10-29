package com.usic.uniFex.model.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.IService.IPersonaService;
import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.entity.Persona;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GestionPersonaService {

    public static final String ACTIVO = "ACTIVO";
    public static final String ELIMINADO = "ELIMINADO";

    private final IPersonaService personaService;
    private final IUsuarioDao usuarioDao;
    private final CatalogoAcademicoService catalogoAcademico;

    public record Datos(String nombre, String paterno, String materno, String ci,
                        String correo, String celular, Long carreraId) {
    }

    public record Resultado(boolean ok, String mensaje, Persona persona) {
        static Resultado error(String m) { return new Resultado(false, m, null); }
        static Resultado exito(String m, Persona p) { return new Resultado(true, m, p); }
    }

    public List<Persona> listar() {
        return personaService.listarPersonas();
    }

    public Set<Long> idsConUsuario() {
        return Set.copyOf(usuarioDao.idsDePersonasConUsuario());
    }

    @Transactional
    public Resultado crear(Datos d, Long actorId) {
        String falta = validar(d);
        if (falta != null) return Resultado.error(falta);
        if (ciEnUso(d.ci(), null)) return Resultado.error("Ya existe una persona con ese C.I.");

        Persona p = new Persona();
        aplicar(p, d);
        p.setEstado(ACTIVO);
        p.setRegistro(new Date());
        p.setModificacion(new Date());
        p.setRegistroIdUsuario(actorId);
        p.setModificacionIdUsuario(actorId);
        return Resultado.exito("Persona creada.", personaService.save(p));
    }

    @Transactional
    public Resultado editar(Long id, Datos d, Long actorId) {
        Persona p = personaService.findById(id);
        if (p == null || ELIMINADO.equalsIgnoreCase(p.getEstado())) return Resultado.error("Persona no encontrada.");
        String falta = validar(d);
        if (falta != null) return Resultado.error(falta);
        if (ciEnUso(d.ci(), id)) return Resultado.error("Ya existe otra persona con ese C.I.");

        aplicar(p, d);
        p.setModificacion(new Date());
        p.setModificacionIdUsuario(actorId);
        return Resultado.exito("Persona actualizada.", personaService.save(p));
    }

    @Transactional
    public Resultado eliminar(Long id, Long actorId) {
        Persona p = personaService.findById(id);
        if (p == null || ELIMINADO.equalsIgnoreCase(p.getEstado())) return Resultado.error("Persona no encontrada.");
        if (idsConUsuario().contains(id)) {
            return Resultado.error("No se puede eliminar: la persona tiene un usuario. Elimina primero el usuario.");
        }
        p.setEstado(ELIMINADO);
        p.setModificacion(new Date());
        p.setModificacionIdUsuario(actorId);
        return Resultado.exito("Persona eliminada.", personaService.save(p));
    }

    public List<Seleccionable> buscarParaSelector(String texto, int limite) {
        String q = texto == null ? "" : texto.trim().toLowerCase();
        Set<Long> conUsuario = idsConUsuario();

        List<Persona> encontradas = new ArrayList<>(listar().stream()
                .filter(p -> q.isEmpty() || textoBuscable(p).contains(q))
                .limit(limite)
                .toList());

        if (!q.isEmpty() && encontradas.stream().noneMatch(p -> q.equalsIgnoreCase(trim(p.getCi())))) {
            buscarPorCi(q).filter(p -> encontradas.stream().noneMatch(e -> e.getId().equals(p.getId())))
                    .ifPresent(encontradas::add);
        }

        return encontradas.stream()
                .map(p -> new Seleccionable(p.getId(), nombreCompleto(p), p.getCi(),
                        conUsuario.contains(p.getId()), p.getEstado()))
                .toList();
    }

    public Optional<Persona> buscarPorCi(String ci) {
        String buscado = trim(ci);
        if (buscado == null || buscado.isEmpty()) return Optional.empty();
        return personaService.findFirstByCi(buscado)
                .filter(p -> !ELIMINADO.equalsIgnoreCase(p.getEstado()));
    }

    public Seleccionable comoSeleccionable(Persona p) {
        return new Seleccionable(p.getId(), nombreCompleto(p), p.getCi(),
                idsConUsuario().contains(p.getId()), p.getEstado());
    }

    public record Seleccionable(Long id, String nombre, String ci, boolean tieneUsuario, String origen) {
    }

    private String textoBuscable(Persona p) {
        return (nombreCompleto(p) + " " + (p.getCi() == null ? "" : p.getCi())).toLowerCase();
    }

    private static String nombreCompleto(Persona p) {
        return java.util.stream.Stream.of(p.getNombre(), p.getPaterno(), p.getMaterno())
                .filter(s -> s != null && !s.isBlank())
                .reduce((a, b) -> a + " " + b).orElse("(sin nombre)");
    }

    private String validar(Datos d) {
        if (vacio(d.nombre())) return "El nombre es obligatorio.";
        if (vacio(d.paterno()) && vacio(d.materno())) return "Debe tener al menos un apellido.";
        if (vacio(d.ci())) return "El C.I. es obligatorio.";
        return null;
    }

    private void aplicar(Persona p, Datos d) {
        p.setNombre(trim(d.nombre()));
        p.setPaterno(trim(d.paterno()));
        p.setMaterno(trim(d.materno()));
        p.setCi(trim(d.ci()));
        p.setCorreo(trim(d.correo()));
        p.setCelular(trim(d.celular()));
        p.setCarrera(catalogoAcademico.buscarViva(d.carreraId()));
    }

    private boolean ciEnUso(String ci, Long exceptoId) {
        return personaService.findFirstByCi(trim(ci))
                .filter(p -> !ELIMINADO.equalsIgnoreCase(p.getEstado()))
                .filter(p -> exceptoId == null || !p.getId().equals(exceptoId))
                .isPresent();
    }

    private boolean vacio(String s) { return s == null || s.isBlank(); }
    private String trim(String s) { return s == null ? null : s.trim(); }
}

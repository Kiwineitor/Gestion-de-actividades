package com.usic.uniFex.model.service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IRolDao;
import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.dto.RolDTO;
import com.usic.uniFex.model.entity.Rol;
import com.usic.uniFex.security.RolesSistema;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GestionRolService {

    public static final String ACTIVO = "ACTIVO";
    public static final String ELIMINADO = "ELIMINADO";

    private static final Pattern NOMBRE_VALIDO = Pattern.compile("^[A-ZÑ0-9]+( [A-ZÑ0-9]+)*$");

    private static final int NOMBRE_MIN = 3;
    private static final int NOMBRE_MAX = 40;

    private static final int DESCRIPCION_MAX = 255;

    private static final Logger logger = LoggerFactory.getLogger(GestionRolService.class);

    private final IRolDao rolDao;
    private final IUsuarioDao usuarioDao;

    public record Datos(String nombre, String descripcion) {
    }

    public record Resultado(boolean ok, String mensaje, Rol rol) {
        static Resultado error(String m) { return new Resultado(false, m, null); }
        static Resultado exito(String m, Rol r) { return new Resultado(true, m, r); }
    }

    public List<RolDTO> listar() {
        Map<Long, Long> porRol = new HashMap<>();
        for (Object[] fila : usuarioDao.contarUsuariosPorRol()) {
            if (fila[0] != null) porRol.put((Long) fila[0], (Long) fila[1]);
        }
        return rolDao.listarVivos().stream()
                .map(r -> RolDTO.de(r, porRol.getOrDefault(r.getId(), 0L)))
                .toList();
    }

    @Transactional
    public Resultado crear(Datos d, Long actorId) {
        String nombre = RolesSistema.normalizar(d == null ? null : d.nombre());
        String problema = validarNombre(nombre);
        if (problema != null) return Resultado.error(problema);
        if (rolDao.contarPorNombre(nombre, -1L) > 0) return Resultado.error("Ya existe un rol con ese nombre.");
        problema = validarDescripcion(d.descripcion());
        if (problema != null) return Resultado.error(problema);

        Rol r = new Rol();
        r.setNombre(nombre);
        r.setDescripcion(limpiar(d.descripcion()));
        r.setEstado(ACTIVO);
        r.setRegistro(new Date());
        r.setModificacion(new Date());
        r.setRegistroIdUsuario(actorId);
        r.setModificacionIdUsuario(actorId);
        Rol guardado = rolDao.save(r);
        logger.info("Rol creado: {} (id={}) por usuario {}", nombre, guardado.getId(), actorId);
        return Resultado.exito("Rol creado.", guardado);
    }

    @Transactional
    public Resultado editar(Long id, Datos d, Long actorId) {
        Rol r = rolDao.findById(id).orElse(null);
        if (r == null || ELIMINADO.equalsIgnoreCase(r.getEstado())) return Resultado.error("Rol no encontrado.");

        String nombre = RolesSistema.normalizar(d == null ? null : d.nombre());
        boolean cambiaNombre = !nombre.isBlank() && !nombre.equals(RolesSistema.normalizar(r.getNombre()));

        if (cambiaNombre && RolesSistema.esDelSistema(r.getNombre())) {
            return Resultado.error("\"" + r.getNombre() + "\" es un rol del sistema: su nombre no se puede cambiar, "
                    + "solo la descripcion.");
        }
        if (cambiaNombre) {
            String problema = validarNombre(nombre);
            if (problema != null) return Resultado.error(problema);
            if (rolDao.contarPorNombre(nombre, id) > 0) return Resultado.error("Ya existe un rol con ese nombre.");
            if (RolesSistema.esDelSistema(nombre)) {
                return Resultado.error("\"" + nombre + "\" esta reservado para un rol del sistema.");
            }
            r.setNombre(nombre);
        }

        String malaDescripcion = validarDescripcion(d.descripcion());
        if (malaDescripcion != null) return Resultado.error(malaDescripcion);

        r.setDescripcion(limpiar(d.descripcion()));
        r.setModificacion(new Date());
        r.setModificacionIdUsuario(actorId);
        return Resultado.exito("Rol actualizado.", rolDao.save(r));
    }

    @Transactional
    public Resultado eliminar(Long id, Long actorId) {
        Rol r = rolDao.findById(id).orElse(null);
        if (r == null || ELIMINADO.equalsIgnoreCase(r.getEstado())) return Resultado.error("Rol no encontrado.");
        if (RolesSistema.esDelSistema(r.getNombre())) {
            return Resultado.error("\"" + r.getNombre() + "\" es un rol del sistema y no se puede eliminar.");
        }
        long usuarios = usuarioDao.contarPorRol(id);
        if (usuarios > 0) {
            return Resultado.error("No se puede eliminar: " + usuarios + " usuario(s) tienen este rol. "
                    + "Cambiales el rol primero.");
        }
        r.setEstado(ELIMINADO);
        r.setModificacion(new Date());
        r.setModificacionIdUsuario(actorId);
        logger.info("Rol eliminado (baja logica): {} (id={}) por usuario {}", r.getNombre(), id, actorId);
        return Resultado.exito("Rol eliminado.", rolDao.save(r));
    }

    @Transactional
    public void asegurarRolesDelSistema() {
        for (RolesSistema definicion : RolesSistema.values()) {
            Rol r = rolDao.findFirstByNombreIgnoreCase(definicion.getNombre()).orElse(null);
            if (r == null) {
                r = new Rol();
                r.setNombre(definicion.getNombre());
                r.setDescripcion(definicion.getDescripcion());
                r.setEstado(ACTIVO);
                r.setRegistro(new Date());
                r.setModificacion(new Date());
                rolDao.save(r);
                logger.info("Rol del sistema creado: {}", definicion.getNombre());
                continue;
            }
            boolean cambio = false;
            if (!definicion.getNombre().equals(r.getNombre())) {
                r.setNombre(definicion.getNombre());
                cambio = true;
            }
            if (r.getDescripcion() == null || r.getDescripcion().isBlank()) {
                r.setDescripcion(definicion.getDescripcion());
                cambio = true;
            }
            if (ELIMINADO.equalsIgnoreCase(r.getEstado()) || r.getEstado() == null || r.getEstado().isBlank()) {
                r.setEstado(ACTIVO);
                cambio = true;
            }
            if (cambio) {
                r.setModificacion(new Date());
                rolDao.save(r);
            }
        }
    }

    public Rol delSistema(RolesSistema definicion) {
        return rolDao.findFirstByNombreIgnoreCase(definicion.getNombre()).orElse(null);
    }

    private String validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) return "El nombre del rol es obligatorio.";
        if (nombre.length() < NOMBRE_MIN) return "El nombre del rol es demasiado corto (minimo " + NOMBRE_MIN + ").";
        if (nombre.length() > NOMBRE_MAX) return "El nombre del rol es demasiado largo (maximo " + NOMBRE_MAX + ").";
        if (!NOMBRE_VALIDO.matcher(nombre).matches()) {
            return "El nombre solo admite letras sin tilde, numeros y espacios simples "
                    + "(se convierte en el permiso ROLE_" + nombre.replace(' ', '_') + ").";
        }
        return null;
    }

    private String validarDescripcion(String descripcion) {
        if (descripcion != null && descripcion.trim().length() > DESCRIPCION_MAX) {
            return "La descripcion no puede pasar de " + DESCRIPCION_MAX + " caracteres.";
        }
        return null;
    }

    private String limpiar(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

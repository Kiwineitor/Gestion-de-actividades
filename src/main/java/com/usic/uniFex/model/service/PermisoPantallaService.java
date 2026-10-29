package com.usic.uniFex.model.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IPermisoPantallaDao;
import com.usic.uniFex.security.PantallasSistema;
import com.usic.uniFex.security.RolesSistema;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermisoPantallaService {

    private final com.usic.uniFex.model.dao.IUsuarioDao usuarioDao;
    private final NotificacionService notificaciones;

    private final IPermisoPantallaDao dao;

    public static boolean loVeTodo(String nombreRol) {
        return RolesSistema.SUPER_USUARIO.getNombre()
                .equals(RolesSistema.normalizar(nombreRol));
    }

    private static List<String> todasLasClaves() {
        return PantallasSistema.todas().stream().map(PantallasSistema::getClave).toList();
    }

    @Transactional(readOnly = true)
    public List<String> pantallasDe(Long usuarioId, String nombreRol) {
        if (loVeTodo(nombreRol)) return todasLasClaves();
        return dao.pantallasDeUsuario(usuarioId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> matriz() {
        Map<Long, Map<String, Object>> porRol = new LinkedHashMap<>();
        for (Object[] f : dao.listarTodo()) {
            Long rolId = ((Number) f[0]).longValue();
            String nombre = (String) f[1];
            String pantalla = (String) f[2];

            Map<String, Object> fila = porRol.computeIfAbsent(rolId, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("rolId", rolId);
                m.put("rol", nombre);
                boolean todo = loVeTodo(nombre);
                m.put("loVeTodo", todo);
                m.put("sistema", RolesSistema.esDelSistema(nombre));
                m.put("pantallas", todo ? new ArrayList<>(todasLasClaves()) : new ArrayList<String>());
                return m;
            });

            if (pantalla != null && !loVeTodo(nombre)) {
                @SuppressWarnings("unchecked")
                List<String> ps = (List<String>) fila.get("pantallas");
                ps.add(pantalla);
            }
        }
        return new ArrayList<>(porRol.values());
    }

    @Transactional
    public List<String> guardar(Long rolId, String nombreRol, List<String> pantallas, Long adminId) {
        if (loVeTodo(nombreRol)) {
            throw new IllegalArgumentException(
                    "El SUPER USUARIO ve todas las pantallas y no se puede limitar: es la forma de "
                    + "recuperar el sistema si alguien se equivoca aqui.");
        }
        List<String> validas = (pantallas == null ? List.<String>of() : pantallas).stream()
                .map(p -> p == null ? "" : p.trim())
                .filter(PantallasSistema::existe)
                .distinct()
                .toList();
        dao.reemplazar(rolId, validas, adminId);
        log.info("Permisos del rol {}: {} pantallas (cambiado por el usuario {})",
                nombreRol, validas.size(), adminId);

        for (Long usuarioId : usuarioDao.idsPorRol(rolId)) {
            notificaciones.notificar(usuarioId, NotificacionService.TIPO_PERMISOS,
                    "Cambiaron tus opciones", "Se actualizo lo que puedes ver en el menu.",
                    null, null);
        }
        return validas;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> usuarios() {
        List<Map<String, Object>> r = new ArrayList<>();
        for (Object[] f : dao.listarUsuarios()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("usuarioId", ((Number) f[0]).longValue());
            m.put("username", f[1]);
            m.put("nombre", f[2]);
            m.put("rol", f[3]);
            m.put("estado", f[4]);
            m.put("propias", f[5] == null ? 0 : ((Number) f[5]).intValue());
            m.put("loVeTodo", loVeTodo((String) f[3]));
            r.add(m);
        }
        return r;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> deUsuario(Long usuarioId) {
        Object[] u = dao.usuario(usuarioId);
        if (u == null) throw new IllegalArgumentException("El usuario no existe");
        String rol = (String) u[3];
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("usuarioId", usuarioId);
        m.put("username", u[1]);
        m.put("nombre", u[2]);
        m.put("rol", rol);
        m.put("loVeTodo", loVeTodo(rol));
        m.put("delRol", loVeTodo(rol) ? todasLasClaves() : dao.pantallasDelRolDeUsuario(usuarioId));
        m.put("propias", dao.pantallasPropiasDeUsuario(usuarioId));
        return m;
    }

    @Transactional
    public List<String> guardarDeUsuario(Long usuarioId, List<String> pantallas, Long adminId) {
        Object[] u = dao.usuario(usuarioId);
        if (u == null) throw new IllegalArgumentException("El usuario no existe");
        String rol = (String) u[3];
        if (loVeTodo(rol)) {
            throw new IllegalArgumentException("Un SUPER USUARIO ya ve todas las pantallas.");
        }
        List<String> delRol = dao.pantallasDelRolDeUsuario(usuarioId);
        List<String> validas = (pantallas == null ? List.<String>of() : pantallas).stream()
                .map(p -> p == null ? "" : p.trim())
                .filter(PantallasSistema::existe)
                .filter(p -> !delRol.contains(p))
                .distinct()
                .toList();
        dao.reemplazarDeUsuario(usuarioId, validas, adminId);
        log.info("Pantallas propias del usuario {} ({}): {} (cambiado por el usuario {})",
                usuarioId, u[1], validas, adminId);
        notificaciones.notificar(usuarioId, NotificacionService.TIPO_PERMISOS,
                "Cambiaron tus opciones", "Se actualizo lo que puedes ver en el menu.", null, null);
        return validas;
    }
}

package com.usic.uniFex.model.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.dao.IVendedorAsignacionDao;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.dto.AsignacionPuestoDTO;
import com.usic.uniFex.model.dto.VendedorDTO;
import com.usic.uniFex.model.entity.Usuario;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VendedorAsignacionService {

    private static final Logger logger = LoggerFactory.getLogger(VendedorAsignacionService.class);

    private final IVendedorAsignacionDao dao;
    private final PuestoEventPublisher publisher;
    private final IUsuarioDao usuarioDao;
    private final NotificacionService notificaciones;

    @Transactional
    public void quitarPuesto(Long vendedorId, Long puestoId) {
        dao.quitarPuesto(vendedorId, puestoId);
        difundirCambios(List.of(puestoId));
    }

    @Transactional(readOnly = true)
    public Set<Long> getPuestoIdsDeVendedor(Long vendedorId) {
        return dao.findPuestoIdsByVendedor(vendedorId).stream().collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public List<Puesto> getPuestosVisiblesParaVendedor(Long vendedorId) {
        return dao.findPuestosVisiblesParaVendedor(vendedorId);
    }

    public record Habilitado(Long id, String username) {
    }

    public record CasetaAsignable(Long id, String codigo, Long categoriaId, String categoria,
                                  String estado, List<Habilitado> habilitados) {
    }

    public record CambioPorCategoria(String categoria, int sumadas, int quitadas) {
    }

    public record ResultadoAsignacion(int asignadas, int quitadas, List<Long> noDisponibles,
                                       List<CambioPorCategoria> porCategoria) {
    }

    public record ResultadoAsignacionMasiva(int vendedores, int casetas, int asignacionesNuevas,
                                            List<Long> noDisponibles) {
    }

    @Transactional(readOnly = true)
    public List<CasetaAsignable> catalogoAsignable() {
        Map<Long, CasetaAsignable> porCaseta = new java.util.LinkedHashMap<>();
        for (Object[] x : dao.findCatalogoAsignable()) {
            Long id = numero(x[0]);
            CasetaAsignable caseta = porCaseta.computeIfAbsent(id, k -> new CasetaAsignable(
                    id, texto(x[1]), numero(x[2]), texto(x[3]), texto(x[4]),
                    new java.util.ArrayList<>()));
            Long vendedorId = numero(x[5]);
            if (vendedorId != null) caseta.habilitados().add(new Habilitado(vendedorId, texto(x[6])));
        }
        return List.copyOf(porCaseta.values());
    }

    @Transactional
    public ResultadoAsignacion reemplazarPuestos(Long vendedorId, List<Long> puestoIds, Long adminId) {
        validarVendedor(vendedorId);
        List<Long> pedidos = puestoIds == null ? List.of() : puestoIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        Set<Long> antes = getPuestoIdsDeVendedor(vendedorId);

        dao.reemplazarPuestos(vendedorId, pedidos, adminId);

        Set<Long> despues = getPuestoIdsDeVendedor(vendedorId);
        List<Long> noDisponibles = pedidos.stream().filter(id -> !despues.contains(id)).toList();
        List<Long> sumadas = despues.stream().filter(id -> !antes.contains(id)).toList();
        List<Long> quitadas = antes.stream().filter(id -> !despues.contains(id)).toList();

        logger.info("Vendedor {}: {} caseta(s) asignadas, {} quitadas, {} no disponibles (admin {})",
                vendedorId, sumadas.size(), quitadas.size(), noDisponibles.size(), adminId);

        List<CambioPorCategoria> porCategoria = desglosarPorCategoria(sumadas, quitadas);
        if (!porCategoria.isEmpty()) {
            avisarAlVendedor(vendedorId, redactar(porCategoria));
        }
        difundirCambios(Stream.concat(sumadas.stream(), quitadas.stream()).distinct().toList());
        return new ResultadoAsignacion(sumadas.size(), quitadas.size(), noDisponibles, porCategoria);
    }

    @Transactional
    public ResultadoAsignacionMasiva agregarPuestosAArea(Long areaId, List<Long> puestoIds, Long adminId) {
        if (areaId == null) throw new IllegalArgumentException("Selecciona una facultad");
        List<Long> pedidos = puestoIds == null ? List.of() : puestoIds.stream()
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (pedidos.isEmpty()) throw new IllegalArgumentException("Selecciona al menos una caseta");

        List<Long> vendedores = usuarioDao.findByRolNombre("ADMINISTRATIVO").stream()
                .filter(u -> u.getPersona() != null
                        && u.getPersona().getCarrera() != null
                        && u.getPersona().getCarrera().getArea() != null
                        && areaId.equals(u.getPersona().getCarrera().getArea().getId()))
                .map(Usuario::getId)
                .toList();
        if (vendedores.isEmpty()) {
            throw new IllegalArgumentException("No hay vendedores activos en esa facultad");
        }

        int nuevas = dao.agregarPuestosAVendedores(vendedores, pedidos, adminId);
        List<Long> disponibles = dao.nombresDeCategoriaPorPuesto(pedidos).stream()
                .map(f -> numero(f[0]))
                .distinct()
                .toList();
        Set<Long> disponiblesSet = Set.copyOf(disponibles);
        List<Long> noDisponibles = pedidos.stream().filter(id -> !disponiblesSet.contains(id)).toList();

        for (Long vendedorId : vendedores) {
            avisarAlVendedor(vendedorId, "Se te habilitaron " + disponibles.size()
                    + casetas(disponibles.size()) + " por asignacion de facultad.");
        }
        difundirCambios(disponibles);
        logger.info("Area {}: {} caseta(s) agregadas a {} vendedor(es) por admin {}",
                areaId, disponibles.size(), vendedores.size(), adminId);
        return new ResultadoAsignacionMasiva(vendedores.size(), disponibles.size(),
                nuevas, noDisponibles);
    }

    private void avisarAlVendedor(Long vendedorId, String mensaje) {
        try {
            notificaciones.notificar(vendedorId, NotificacionService.TIPO_ASIGNACION,
                    "Cambió lo que puedes vender", mensaje, null, null);
        } catch (Exception e) {
            logger.warn("No se pudo avisar al vendedor {} del cambio de asignacion: {}",
                    vendedorId, e.getMessage());
        }
    }

    private String redactar(List<CambioPorCategoria> cambios) {
        StringBuilder sb = new StringBuilder();
        for (CambioPorCategoria c : cambios) {
            if (c.sumadas() > 0) {
                sb.append("Se te asignaron ").append(c.sumadas()).append(casetas(c.sumadas()))
                  .append(" de ").append(c.categoria()).append(". ");
            }
            if (c.quitadas() > 0) {
                sb.append("Se te quitaron ").append(c.quitadas()).append(casetas(c.quitadas()))
                  .append(" de ").append(c.categoria()).append(". ");
            }
        }
        return sb.toString().trim();
    }

    private static String casetas(int n) { return n == 1 ? " caseta" : " casetas"; }

    private List<CambioPorCategoria> desglosarPorCategoria(List<Long> sumadas, List<Long> quitadas) {
        List<Long> todas = new java.util.ArrayList<>(sumadas);
        todas.addAll(quitadas);
        if (todas.isEmpty()) return List.of();

        java.util.Map<Long, String> categoriaDe = new java.util.HashMap<>();
        for (Object[] fila : dao.nombresDeCategoriaPorPuesto(todas)) {
            categoriaDe.put(numero(fila[0]), texto(fila[1]));
        }
        java.util.Map<String, int[]> acumulado = new java.util.LinkedHashMap<>();
        for (Long id : sumadas) {
            acumulado.computeIfAbsent(categoriaDe.getOrDefault(id, "(sin categoria)"), k -> new int[2])[0]++;
        }
        for (Long id : quitadas) {
            acumulado.computeIfAbsent(categoriaDe.getOrDefault(id, "(sin categoria)"), k -> new int[2])[1]++;
        }
        return acumulado.entrySet().stream()
                .map(e -> new CambioPorCategoria(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    private static Long numero(Object o) { return o == null ? null : ((Number) o).longValue(); }
    private static String texto(Object o) { return o == null ? null : o.toString(); }

    @Transactional(readOnly = true)
    public List<Puesto> getPuestosAsignados(Long vendedorId) {
        return dao.findPuestosAsignadosByVendedor(vendedorId);
    }

    @Transactional(readOnly = true)
    public boolean vendedorTienePuesto(Long vendedorId, Long puestoId) {
        return dao.vendedorTienePuesto(vendedorId, puestoId);
    }

    @Transactional(readOnly = true)
    public boolean puedeVender(Long vendedorId, Long puestoId) {
        return dao.vendedorTienePuesto(vendedorId, puestoId);
    }

    @Transactional(readOnly = true)
    public List<Long> casetasNoPermitidas(Long vendedorId, List<Long> puestoIds) {
        if (puestoIds == null || puestoIds.isEmpty()) return List.of();
        java.util.Set<Long> permitidas = dao.puestosPermitidosDe(vendedorId, puestoIds);
        return puestoIds.stream().filter(id -> !permitidas.contains(id)).toList();
    }

    @Transactional
    public void liberarAsignacionesDe(Long vendedorId) {
        List<Long> sueltas = List.copyOf(getPuestoIdsDeVendedor(vendedorId));
        dao.borrarAsignacionesDe(vendedorId);
        difundirCambios(sueltas);
    }

    private void validarVendedor(Long vendedorId) {
        Usuario u = usuarioDao.findById(vendedorId).orElse(null);
        if (u == null) throw new IllegalArgumentException("El vendedor no existe");
        if (u.getRol() == null || !"ADMINISTRATIVO".equals(u.getRol().getNombre())) {
            throw new IllegalArgumentException("El usuario no es un vendedor (rol ADMINISTRATIVO)");
        }
        if ("X".equals(u.getEstado()) || "ELIMINADO".equals(u.getEstado())) {
            throw new IllegalArgumentException("El vendedor está inactivo o eliminado");
        }
    }

    @Transactional(readOnly = true)
    public List<VendedorDTO> listarVendedores() {
        Map<Long, List<VendedorDTO.CategoriaAsignada>> porVendedor = dao
                .contarPuestosPorCategoriaYVendedor().stream()
                .collect(Collectors.groupingBy(
                        f -> numero(f[0]),
                        java.util.LinkedHashMap::new,
                        Collectors.mapping(
                                f -> new VendedorDTO.CategoriaAsignada(
                                        numero(f[1]), texto(f[2]), ((Number) f[3]).intValue()),
                                Collectors.toList())));

        return usuarioDao.findByRolNombre("ADMINISTRATIVO").stream()
                .map(u -> VendedorDTO.de(u, porVendedor.getOrDefault(u.getId(), List.of())))
                .toList();
    }
    @Transactional(readOnly = true)
    public List<AsignacionPuestoDTO> asignacionesConVendedor() {
        return dao.findAsignacionesConVendedor().stream()
                .map(f -> new AsignacionPuestoDTO(
                        numero(f[0]),
                        numero(f[1]),
                        nombreDe(f[2], f[3], f[4], f[6]),
                        limpio(f[5])))
                .toList();
    }

    private void difundirCambios(List<Long> puestoIds) {
        if (puestoIds == null || puestoIds.isEmpty()) return;

        Set<Long> tocadas = Set.copyOf(puestoIds);
        Map<Long, List<AsignacionPuestoDTO>> vigentes = asignacionesConVendedor().stream()
                .filter(a -> tocadas.contains(a.puestoId()))
                .collect(Collectors.groupingBy(AsignacionPuestoDTO::puestoId));

        List<AsignacionPuestoDTO> cambios = puestoIds.stream().distinct()
                .flatMap(id -> vigentes.containsKey(id)
                        ? vigentes.get(id).stream()
                        : Stream.of(new AsignacionPuestoDTO(id, null, null, null)))
                .toList();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publisher.publicarAsignaciones(cambios);
                }
            });
        } else {
            publisher.publicarAsignaciones(cambios);
        }
    }

    private static String limpio(Object o) {
        String t = o == null ? "" : o.toString().trim();
        return t.isEmpty() ? null : t;
    }

    private static String nombreDe(Object nombre, Object paterno, Object materno, Object username) {
        String completo = Stream.of(nombre, paterno, materno)
                .map(VendedorAsignacionService::limpio)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        return completo.isEmpty() ? limpio(username) : completo;
    }
}

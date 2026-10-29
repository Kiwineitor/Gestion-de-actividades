package com.usic.uniFex.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.Usuario;

public interface IUsuarioDao extends JpaRepository<Usuario, Long> {
      Optional<Usuario> findByUsername(String username);

      @Query("select u.id from Usuario u where u.rol.nombre in ('SUPER USUARIO','ADMINISTRADOR') "
              + "and (u.estado is null or u.estado <> 'ELIMINADO')")
      List<Long> idsDeAdministracion();

      @EntityGraph(attributePaths = { "persona", "rol" })
      @Query("select u from Usuario u where u.estado is null or u.estado <> 'ELIMINADO' order by u.username")
      List<Usuario> listarGestionables();

      @Query("select count(u) from Usuario u where upper(u.username) = upper(:username) "
              + "and u.id <> :exceptoId")
      long contarPorUsername(@Param("username") String username, @Param("exceptoId") Long exceptoId);

      @Query("select count(u) from Usuario u where u.persona.id = :personaId "
              + "and (u.estado is null or u.estado <> 'ELIMINADO') and u.id <> :exceptoId")
      long contarPorPersona(@Param("personaId") Long personaId, @Param("exceptoId") Long exceptoId);

      @Query("select u.persona.id from Usuario u where (u.estado is null or u.estado <> 'ELIMINADO') "
              + "and u.persona is not null")
      List<Long> idsDePersonasConUsuario();

      @Query("select u.id from Usuario u where u.rol.id = :rolId "
              + "and (u.estado is null or u.estado <> 'ELIMINADO')")
      List<Long> idsPorRol(@Param("rolId") Long rolId);

      @Query("select count(u) from Usuario u where u.rol.id = :rolId "
              + "and (u.estado is null or u.estado <> 'ELIMINADO')")
      long contarPorRol(@Param("rolId") Long rolId);

      @Query("select u.rol.id, count(u) from Usuario u "
              + "where (u.estado is null or u.estado <> 'ELIMINADO') group by u.rol.id")
      List<Object[]> contarUsuariosPorRol();

      @Query("select u from Usuario u join fetch u.rol r left join fetch u.persona p "
              + "left join fetch p.carrera c left join fetch c.area "
              + "where r.nombre = :rolNombre and (u.estado is null or u.estado <> 'ELIMINADO') "
              + "order by u.username")
      List<Usuario> findByRolNombre(@Param("rolNombre") String rolNombre);

      @Query("select u.id from Usuario u where u.rol.nombre <> 'SUPER USUARIO' "
              + "and (u.estado is null or u.estado = 'ACTIVO')")
      List<Long> idsNoSuperUsuario();

      @Query("select count(u) from Usuario u where u.rol.nombre = :rol "
              + "and u.estado = 'ACTIVO' and u.id <> :exceptoId")
      long contarActivosPorRol(@Param("rol") String rol, @Param("exceptoId") Long exceptoId);
}

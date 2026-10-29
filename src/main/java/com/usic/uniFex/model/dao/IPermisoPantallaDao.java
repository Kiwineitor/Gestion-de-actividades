package com.usic.uniFex.model.dao;

import java.util.List;

public interface IPermisoPantallaDao {

    List<Object[]> listarTodo();

    List<String> pantallasDeRol(Long rolId);

    List<String> pantallasDeUsuario(Long usuarioId);

    List<String> pantallasDelRolDeUsuario(Long usuarioId);

    List<String> pantallasPropiasDeUsuario(Long usuarioId);

    List<Object[]> listarUsuarios();

    Object[] usuario(Long usuarioId);

    void reemplazar(Long rolId, List<String> pantallas, Long adminId);

    void reemplazarDeUsuario(Long usuarioId, List<String> pantallas, Long adminId);
}

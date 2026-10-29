package com.usic.uniFex.model.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.ICategoriaDao;
import com.usic.uniFex.model.dao.IInteresadoStandDao;
import com.usic.uniFex.model.dto.InteresadoStandRequest;
import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.entity.InteresadoStand;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InteresadoStandService {

    private static final Pattern SOLO_LETRAS = Pattern.compile("[\\p{L} ]+");
    private static final Pattern SIN_NUMEROS = Pattern.compile("[^\\p{N}\\p{Cc}]+");
    private static final Pattern CELULAR = Pattern.compile("\\+?\\d{7,15}");
    private static final Locale ES = Locale.forLanguageTag("es-BO");

    private final IInteresadoStandDao interesadoStandDao;
    private final ICategoriaDao categoriaDao;

    public record Resultado(boolean ok, String mensaje, InteresadoStand interesado) {
        static Resultado error(String m) { return new Resultado(false, m, null); }
    }

    @Transactional
    public Resultado registrar(InteresadoStandRequest d) {
        String falta = validar(d);
        if (falta != null) return Resultado.error(falta);
        Categoria categoria = categoriaViva(d.categoriaId());
        if (categoria == null) return Resultado.error("La categoría elegida ya no existe.");

        InteresadoStand interesado = new InteresadoStand();
        aplicar(interesado, d, categoria);
        return new Resultado(true, "Registro enviado.", interesadoStandDao.save(interesado));
    }

    @Transactional(readOnly = true)
    public List<InteresadoStand> listar() {
        return interesadoStandDao.findAllByOrderByFechaRegistroDesc();
    }

    @Transactional
    public Resultado editar(Long id, InteresadoStandRequest d) {
        InteresadoStand i = interesadoStandDao.findById(id).orElse(null);
        if (i == null) return Resultado.error("El registro ya no existe.");
        String falta = validar(d);
        if (falta != null) return Resultado.error(falta);
        Categoria categoria = categoriaViva(d.categoriaId());
        if (categoria == null) return Resultado.error("La categoría elegida ya no existe.");

        aplicar(i, d, categoria);
        return new Resultado(true, "Registro actualizado.", interesadoStandDao.save(i));
    }

    private void aplicar(InteresadoStand i, InteresadoStandRequest d, Categoria categoria) {
        i.setNombreCompleto(mayusculas(d.nombreCompleto()));
        i.setCelular(d.celular().trim());
        i.setEmpresa(mayusculas(d.empresa()));
        i.setRubro(mayusculas(d.rubro()));
        i.setCategoria(categoria);
    }

    private Categoria categoriaViva(Long id) {
        Categoria c = categoriaDao.findById(id).orElse(null);
        return (c == null || "X".equals(c.getEstado())) ? null : c;
    }

    private String validar(InteresadoStandRequest d) {
        String e = requerido(d.nombreCompleto(), "El nombre completo", 200);
        if (e == null && !SIN_NUMEROS.matcher(d.nombreCompleto().trim()).matches()) {
            e = "El nombre completo no puede tener números.";
        }
        if (e == null) e = requerido(d.celular(), "El número de celular", 30);
        if (e == null && !CELULAR.matcher(d.celular().trim()).matches()) {
            e = "El número de celular solo puede tener números (de 7 a 15), con un + opcional al inicio.";
        }
        if (e == null) e = requerido(d.empresa(), "El nombre de la empresa o emprendimiento", 200);
        if (e == null) e = soloLetras(d.empresa(), "El nombre de la empresa o emprendimiento");
        if (e == null) e = requerido(d.rubro(), "El rubro", 200);
        if (e == null) e = soloLetras(d.rubro(), "El rubro");
        if (e == null && d.categoriaId() == null) e = "Elige una categoría.";
        return e;
    }

    private String requerido(String valor, String campo, int max) {
        if (valor == null || valor.isBlank()) return campo + " es obligatorio.";
        if (valor.trim().length() > max) return campo + " es demasiado largo (máx. " + max + " caracteres).";
        return null;
    }

    private String soloLetras(String valor, String campo) {
        return SOLO_LETRAS.matcher(valor.trim()).matches()
                ? null
                : campo + " solo puede tener letras (sin números ni símbolos).";
    }

    private static String mayusculas(String s) {
        return s.trim().replaceAll("\\s{2,}", " ").toUpperCase(ES);
    }
}

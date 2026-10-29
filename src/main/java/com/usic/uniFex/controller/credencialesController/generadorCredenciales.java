package com.usic.uniFex.controller.credencialesController;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.usic.uniFex.model.IService.ICategoriaService;
import com.usic.uniFex.model.repository.FuncionesInscripcion;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class generadorCredenciales {

    @Autowired
    private FuncionesInscripcion funcionesInscripcion;

    @Autowired
    private ICategoriaService iCategoriaService;

    
    @GetMapping(value = "/vistaGenerarCredenciales/")
    public String vistaGenerarCredenciales(HttpServletRequest request, Model model) {
        
        model.addAttribute("categorias", iCategoriaService.findAll());
        model.addAttribute("InscripcionesCategoria", funcionesInscripcion.fn_inscripciones_por_categoria());

        return "credenciales/vistaCredencialesGenerador";
    }

    @GetMapping(value = "/vistaGenerarCredenciales/credenciales/{id_inscripcion}")
    public String credenciales(HttpServletRequest request, Model model, @PathVariable("id_inscripcion")Long id_inscripcion) {
        
        model.addAttribute("inscripciones", funcionesInscripcion.obtener_inscripcion_detalle(id_inscripcion));

        System.out.println(funcionesInscripcion.obtener_inscripcion_detalle(id_inscripcion).size());

        return "credenciales/vistaCredencialPreview :: modalContent";
    }
}

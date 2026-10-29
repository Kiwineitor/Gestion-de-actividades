package com.usic.uniFex.Config;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.usic.uniFex.anotacion.ValidarUsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AutenticacionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (handler instanceof HandlerMethod hm
                && hm.getMethodAnnotation(ValidarUsuarioAutenticado.class) != null) {
            Object usuario = request.getSession().getAttribute("usuario");
            if (usuario == null) {
                response.sendRedirect(request.getContextPath() + "/");
                return false;
            }
        }
        return true;
    }
}

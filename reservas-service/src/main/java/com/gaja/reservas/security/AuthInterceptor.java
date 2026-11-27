package com.gaja.reservas.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Se ejecuta antes de cada endpoint de /api/**:
 * - sin @Publico, exige "Authorization: Bearer <token>" válido (si no, 401);
 * - con @RequiereAdmin, además exige rol Administrador (si no, 403).
 * Así un endpoint NUEVO queda protegido por defecto.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtService jwt;

    public AuthInterceptor(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod metodo)) {
            return true;
        }
        if (tiene(metodo, Publico.class)) {
            return true;
        }

        String cabecera = request.getHeader("Authorization");
        if (cabecera == null || !cabecera.startsWith("Bearer ")) {
            throw new NoAutorizadoException("Debes iniciar sesión");
        }
        AuthUser usuario = jwt.validar(cabecera.substring("Bearer ".length()).trim())
                .orElseThrow(() -> new NoAutorizadoException(
                        "Tu sesión no es válida o venció. Inicia sesión de nuevo"));
        request.setAttribute(AuthContext.ATRIBUTO, usuario);

        if (tiene(metodo, RequiereAdmin.class) && !usuario.esAdmin()) {
            throw new ProhibidoException("Esta acción es solo para administradores");
        }
        return true;
    }

    private static boolean tiene(HandlerMethod metodo, Class<? extends Annotation> anotacion) {
        return metodo.hasMethodAnnotation(anotacion)
                || AnnotatedElementUtils.hasAnnotation(metodo.getBeanType(), anotacion);
    }
}

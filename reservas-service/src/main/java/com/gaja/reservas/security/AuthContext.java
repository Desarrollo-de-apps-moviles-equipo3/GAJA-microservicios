package com.gaja.reservas.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Para saber, dentro de un controlador, quién hace la petición y si puede tocar ciertos datos. */
public final class AuthContext {

    static final String ATRIBUTO = "gaja.authUser";

    private AuthContext() {
    }

    public static AuthUser actual() {
        var atributos = RequestContextHolder.getRequestAttributes();
        if (atributos instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            Object usuario = request.getAttribute(ATRIBUTO);
            if (usuario instanceof AuthUser autenticado) {
                return autenticado;
            }
        }
        throw new NoAutorizadoException("Debes iniciar sesión");
    }

    /** Cada persona solo puede usar sus propios datos (por correo); el administrador puede con todos. */
    public static void exigirMismoCorreoOAdmin(String email) {
        AuthUser yo = actual();
        if (yo.esAdmin()) {
            return;
        }
        if (email == null || !yo.email().equalsIgnoreCase(email.trim())) {
            throw new ProhibidoException("No puedes acceder a los datos de otra persona");
        }
    }

    /** Igual que el anterior, pero con el id del usuario. */
    public static void exigirMismoIdOAdmin(Long id) {
        AuthUser yo = actual();
        if (!yo.esAdmin() && !yo.id().equals(id)) {
            throw new ProhibidoException("No puedes acceder a los datos de otra persona");
        }
    }
}

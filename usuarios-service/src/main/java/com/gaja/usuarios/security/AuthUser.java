package com.gaja.usuarios.security;

/** Quién hace la petición, según el token ya validado. */
public record AuthUser(Long id, String email, String rol) {

    public static final String ADMIN = "Administrador";

    public boolean esAdmin() {
        return ADMIN.equals(rol);
    }
}

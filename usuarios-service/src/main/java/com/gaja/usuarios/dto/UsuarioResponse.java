package com.gaja.usuarios.dto;

import com.gaja.usuarios.model.Usuario;

/** Lo que sale hacia la app. Nunca incluye la contrasena ni su hash. */
public record UsuarioResponse(
        Long id,
        String email,
        String nombre,
        String rol,
        String estado,
        String telefono,
        String institucion,
        String motivoRechazo,
        boolean notifReservas,
        boolean notifAlertas,
        String creadoEn) {

    public static UsuarioResponse of(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getEmail(), u.getNombre(), u.getRol(), u.getEstado().name(),
                u.getTelefono(), u.getInstitucion(), u.getMotivoRechazo(), u.isNotifReservas(),
                u.isNotifAlertas(), u.getCreadoEn().toString());
    }
}

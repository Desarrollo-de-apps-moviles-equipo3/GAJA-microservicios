package com.gaja.usuarios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos personales que el usuario puede editar. Los campos de notificaciones son opcionales. */
public record ActualizarRequest(
        @NotBlank @Size(max = 80) String nombre,
        @Size(max = 20) String telefono,
        @Size(max = 80) String institucion,
        Boolean notifReservas,
        Boolean notifAlertas) {
}

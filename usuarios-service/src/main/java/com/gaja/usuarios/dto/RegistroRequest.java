package com.gaja.usuarios.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 5, max = 60) String password,
        @NotBlank @Size(max = 80) String nombre,
        @NotBlank String rol,
        @Size(max = 20) String telefono,
        @Size(max = 80) String institucion) {
}

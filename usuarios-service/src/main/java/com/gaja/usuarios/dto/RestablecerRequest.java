package com.gaja.usuarios.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RestablecerRequest(
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Size(max = 20) String codigo,
        @NotBlank @Size(min = 5, max = 60) String nueva) {
}

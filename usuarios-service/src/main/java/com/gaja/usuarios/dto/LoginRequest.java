package com.gaja.usuarios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 120) String email,
        @NotBlank @Size(max = 100) String password) {
}

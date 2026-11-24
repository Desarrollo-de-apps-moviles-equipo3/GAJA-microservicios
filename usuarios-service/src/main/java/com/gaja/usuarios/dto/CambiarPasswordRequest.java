package com.gaja.usuarios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarPasswordRequest(
        @NotBlank String actual,
        @NotBlank @Size(min = 5, max = 60) String nueva) {
}

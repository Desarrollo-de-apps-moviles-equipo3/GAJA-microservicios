package com.gaja.usuarios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarPasswordRequest(
        @NotBlank @Size(max = 100) String actual,
        @NotBlank @Size(min = 5, max = 60) String nueva) {
}

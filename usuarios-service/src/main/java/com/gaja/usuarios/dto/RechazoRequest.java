package com.gaja.usuarios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RechazoRequest(@NotBlank @Size(max = 200) String motivo) {
}

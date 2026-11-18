package com.gaja.espacios.dto;

import jakarta.validation.constraints.NotNull;

public record DisponibilidadRequest(@NotNull Boolean available) {
}

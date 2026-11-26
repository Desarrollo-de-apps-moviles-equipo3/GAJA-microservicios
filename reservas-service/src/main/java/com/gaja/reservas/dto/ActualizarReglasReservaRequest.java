package com.gaja.reservas.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ActualizarReglasReservaRequest(
        @Min(value = 0, message = "La anticipación mínima es 0 días")
        @Max(value = 365, message = "La anticipación máxima es 365 días")
        int maxDiasAdelante,

        @Min(value = 0, message = "El plazo de cancelación no puede ser negativo")
        @Max(value = 48, message = "El plazo de cancelación no puede superar 48 horas")
        int horasLimiteCancelacion
) {
}

package com.gaja.espacios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record BloqueoRequest(
        @NotNull LocalDate date,
        @NotBlank String timeSlot,
        String reason
) {
}

package com.gaja.espacios.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record EspacioRequest(
        String id,
        @NotBlank String name,
        @NotBlank String category,
        String description,
        @Min(1) int capacity,
        Boolean available,
        String colorHex,
        List<String> tags,
        Double latitude,
        Double longitude
) {
}

package com.gaja.espacios.dto;

import com.gaja.espacios.model.Espacio;
import java.util.List;

public record EspacioResponse(
        String id,
        String name,
        String category,
        Long categoryId,
        String description,
        int capacity,
        boolean available,
        String colorHex,
        List<String> tags,
        Double latitude,
        Double longitude
) {
    public static EspacioResponse from(Espacio e) {
        return new EspacioResponse(
                e.getId(),
                e.getNombre(),
                e.getCategoria().getNombre(),
                e.getCategoria().getId(),
                e.getDescripcion(),
                e.getCapacidad(),
                e.isDisponible(),
                e.getColorHex(),
                List.copyOf(e.getTags()),
                e.getLatitud(),
                e.getLongitud());
    }
}

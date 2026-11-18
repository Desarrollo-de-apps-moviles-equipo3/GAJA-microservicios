package com.gaja.espacios.dto;

import com.gaja.espacios.model.Categoria;
import jakarta.validation.constraints.NotBlank;

public record CategoriaDto(Long id, @NotBlank String name) {
    public static CategoriaDto from(Categoria c) {
        return new CategoriaDto(c.getId(), c.getNombre());
    }
}

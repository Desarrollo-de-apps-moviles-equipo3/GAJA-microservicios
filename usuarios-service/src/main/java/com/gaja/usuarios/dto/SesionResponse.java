package com.gaja.usuarios.dto;

import com.gaja.usuarios.model.Sesion;

public record SesionResponse(Long id, String fecha) {
    public static SesionResponse of(Sesion s) {
        return new SesionResponse(s.getId(), s.getFecha().toString());
    }
}

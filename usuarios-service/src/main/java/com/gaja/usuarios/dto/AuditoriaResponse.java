package com.gaja.usuarios.dto;

import com.gaja.usuarios.model.Auditoria;

public record AuditoriaResponse(Long id, String fecha, String actor, String accion, String detalle) {
    public static AuditoriaResponse of(Auditoria a) {
        return new AuditoriaResponse(a.getId(), a.getFecha().toString(), a.getActor(), a.getAccion(), a.getDetalle());
    }
}

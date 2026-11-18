package com.gaja.espacios.dto;

import com.gaja.espacios.model.Bloqueo;
import java.time.LocalDate;

public record BloqueoResponse(Long id, String spaceId, LocalDate date, String timeSlot, String reason) {
    public static BloqueoResponse from(Bloqueo b) {
        return new BloqueoResponse(b.getId(), b.getEspacio().getId(), b.getFecha(), b.getFranja(), b.getMotivo());
    }
}

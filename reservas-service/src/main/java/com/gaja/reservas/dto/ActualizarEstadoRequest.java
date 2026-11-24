package com.gaja.reservas.dto;

import com.gaja.reservas.model.EstadoReserva;
import jakarta.validation.constraints.NotNull;

public class ActualizarEstadoRequest {

    @NotNull(message = "Indica el nuevo estado")
    private EstadoReserva estado;

    public EstadoReserva getEstado() {
        return estado;
    }

    public void setEstado(EstadoReserva estado) {
        this.estado = estado;
    }
}
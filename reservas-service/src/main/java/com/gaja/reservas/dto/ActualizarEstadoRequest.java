package com.gaja.reservas.dto;

import com.gaja.reservas.model.EstadoReserva;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ActualizarEstadoRequest {

    @NotNull(message = "Indica el nuevo estado")
    private EstadoReserva estado;

    @Size(max = 200, message = "El motivo no puede superar 200 caracteres")
    private String motivoRechazo;

    public EstadoReserva getEstado() {
        return estado;
    }

    public void setEstado(EstadoReserva estado) {
        this.estado = estado;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }
}

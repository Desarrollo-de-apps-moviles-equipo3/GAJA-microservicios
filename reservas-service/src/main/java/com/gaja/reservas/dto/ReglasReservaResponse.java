package com.gaja.reservas.dto;

/** Valores vigentes que el servicio aplica al crear y cancelar reservas. */
public record ReglasReservaResponse(
        int maxDiasAdelante,
        int horasLimiteCancelacion,
        int horaApertura,
        int horaCierre
) {
}

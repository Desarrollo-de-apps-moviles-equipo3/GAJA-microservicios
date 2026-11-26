package com.gaja.reservas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Configuración operativa editable desde el panel administrador. */
@Entity
@Table(name = "reglas_reserva")
public class ReglasReserva {
    @Id
    private Long id = 1L;

    @Column(name = "max_dias_adelante", nullable = false)
    private int maxDiasAdelante = 7;

    @Column(name = "horas_limite_cancelacion", nullable = false)
    private int horasLimiteCancelacion = 2;

    public Long getId() { return id; }
    public int getMaxDiasAdelante() { return maxDiasAdelante; }
    public void setMaxDiasAdelante(int value) { maxDiasAdelante = value; }
    public int getHorasLimiteCancelacion() { return horasLimiteCancelacion; }
    public void setHorasLimiteCancelacion(int value) { horasLimiteCancelacion = value; }
}

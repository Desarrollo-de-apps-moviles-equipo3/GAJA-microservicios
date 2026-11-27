package com.gaja.usuarios.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Sesión cerrada antes de que venza su token (cierre de sesión, cambio de clave, cuenta desactivada). */
@Entity
@Table(name = "sesion_revocada")
public class SesionRevocada {

    @Id
    @Column(name = "id_sesion", nullable = false)
    private String idSesion;

    @Column(nullable = false)
    private Instant fecha = Instant.now();

    protected SesionRevocada() {
    }

    public SesionRevocada(String idSesion) {
        this.idSesion = idSesion;
    }

    public String getIdSesion() { return idSesion; }
    public Instant getFecha() { return fecha; }
}

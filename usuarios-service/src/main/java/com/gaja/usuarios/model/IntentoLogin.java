package com.gaja.usuarios.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Cuenta los inicios de sesión fallidos de un correo para bloquearlo un rato si se pasa del límite. */
@Entity
@Table(name = "intento_login")
public class IntentoLogin {

    @Id
    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "bloqueado_hasta")
    private Instant bloqueadoHasta;

    @Column(name = "ultimo_intento", nullable = false)
    private Instant ultimoIntento = Instant.now();

    protected IntentoLogin() {
    }

    public IntentoLogin(String email) {
        this.email = email;
    }

    public String getEmail() { return email; }
    public int getIntentos() { return intentos; }
    public void setIntentos(int intentos) { this.intentos = intentos; }
    public Instant getBloqueadoHasta() { return bloqueadoHasta; }
    public void setBloqueadoHasta(Instant bloqueadoHasta) { this.bloqueadoHasta = bloqueadoHasta; }
    public Instant getUltimoIntento() { return ultimoIntento; }
    public void setUltimoIntento(Instant ultimoIntento) { this.ultimoIntento = ultimoIntento; }
}

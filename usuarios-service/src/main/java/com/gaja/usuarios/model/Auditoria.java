package com.gaja.usuarios.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Registro de una acción crítica: quién, qué y cuándo. Solo lo lee el administrador. */
@Entity
@Table(name = "auditoria")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant fecha = Instant.now();

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String accion;

    @Column(length = 300)
    private String detalle;

    protected Auditoria() {
    }

    public Auditoria(String actor, String accion, String detalle) {
        this.actor = actor;
        this.accion = accion;
        this.detalle = detalle;
    }

    public Long getId() { return id; }
    public Instant getFecha() { return fecha; }
    public String getActor() { return actor; }
    public String getAccion() { return accion; }
    public String getDetalle() { return detalle; }
}

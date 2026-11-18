package com.gaja.espacios.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "bloqueo",
        uniqueConstraints = @UniqueConstraint(columnNames = {"espacio_id", "fecha", "franja"}))
public class Bloqueo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "espacio_id")
    private Espacio espacio;

    @Column(nullable = false)
    private LocalDate fecha;

    /** Mismo formato que la app, por ejemplo "08:00 - 10:00". */
    @Column(nullable = false)
    private String franja;

    private String motivo;

    protected Bloqueo() {
    }

    public Bloqueo(Espacio espacio, LocalDate fecha, String franja, String motivo) {
        this.espacio = espacio;
        this.fecha = fecha;
        this.franja = franja;
        this.motivo = motivo;
    }

    public Long getId() { return id; }
    public Espacio getEspacio() { return espacio; }
    public LocalDate getFecha() { return fecha; }
    public String getFranja() { return franja; }
    public String getMotivo() { return motivo; }
}

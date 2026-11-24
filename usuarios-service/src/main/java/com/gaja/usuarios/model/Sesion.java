package com.gaja.usuarios.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Un inicio de sesion exitoso. Alimenta la pantalla "Registro de sesiones". */
@Entity
@Table(name = "sesion")
public class Sesion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private Instant fecha = Instant.now();

    @Column(nullable = false)
    private String token;

    protected Sesion() {
    }

    public Sesion(Long usuarioId, String token) {
        this.usuarioId = usuarioId;
        this.token = token;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public Instant getFecha() { return fecha; }
    public String getToken() { return token; }
}

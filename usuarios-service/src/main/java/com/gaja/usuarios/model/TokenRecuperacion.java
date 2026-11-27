package com.gaja.usuarios.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Código de un solo uso para recuperar la contraseña. Solo se guarda su hash, nunca el código. */
@Entity
@Table(name = "token_recuperacion")
public class TokenRecuperacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "codigo_hash", nullable = false)
    private String codigoHash;

    @Column(nullable = false)
    private Instant expira;

    @Column(nullable = false)
    private boolean usado;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    protected TokenRecuperacion() {
    }

    public TokenRecuperacion(Long usuarioId, String codigoHash, Instant expira) {
        this.usuarioId = usuarioId;
        this.codigoHash = codigoHash;
        this.expira = expira;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getCodigoHash() { return codigoHash; }
    public Instant getExpira() { return expira; }
    public boolean isUsado() { return usado; }
    public void setUsado(boolean usado) { this.usado = usado; }
    public int getIntentos() { return intentos; }
    public void setIntentos(int intentos) { this.intentos = intentos; }
    public Instant getCreadoEn() { return creadoEn; }
}

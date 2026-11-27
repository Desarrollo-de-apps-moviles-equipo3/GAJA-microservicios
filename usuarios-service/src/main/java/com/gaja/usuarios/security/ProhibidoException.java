package com.gaja.usuarios.security;

/** La persona está identificada pero no tiene permiso para esto: responde 403. */
public class ProhibidoException extends RuntimeException {
    public ProhibidoException(String mensaje) {
        super(mensaje);
    }
}

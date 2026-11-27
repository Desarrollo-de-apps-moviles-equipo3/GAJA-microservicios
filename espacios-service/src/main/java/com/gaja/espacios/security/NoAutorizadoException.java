package com.gaja.espacios.security;

/** Falta el token, es inválido o venció: responde 401. */
public class NoAutorizadoException extends RuntimeException {
    public NoAutorizadoException(String mensaje) {
        super(mensaje);
    }
}

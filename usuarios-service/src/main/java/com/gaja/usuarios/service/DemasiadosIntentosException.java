package com.gaja.usuarios.service;

/** Demasiados inicios de sesión fallidos: la cuenta queda bloqueada un rato (HTTP 429). */
public class DemasiadosIntentosException extends RuntimeException {
    public DemasiadosIntentosException(String mensaje) {
        super(mensaje);
    }
}

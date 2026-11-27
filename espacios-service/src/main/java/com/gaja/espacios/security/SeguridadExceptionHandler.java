package com.gaja.espacios.security;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errores de seguridad con el mismo formato de este servicio: {"error": "mensaje"}. */
@RestControllerAdvice
public class SeguridadExceptionHandler {

    @ExceptionHandler(NoAutorizadoException.class)
    public ResponseEntity<Map<String, String>> noAutorizado(NoAutorizadoException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(ProhibidoException.class)
    public ResponseEntity<Map<String, String>> prohibido(ProhibidoException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }
}

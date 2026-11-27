package com.gaja.reservas.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;


@RestControllerAdvice
public class ReservaExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ReservaExceptionHandler.class);

    /** Datos que no cumplen las validaciones del formulario: se devuelve el primer mensaje (ya está en español). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> datosInvalidos(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage())
                .filter(m -> m != null && !m.isBlank())
                .findFirst().orElse("Datos inválidos");
        return ResponseEntity.badRequest().body(Map.of("mensaje", detalle));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> cuerpoIlegible(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", "El cuerpo de la solicitud no es válido"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> tipoIncorrecto(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", "El parámetro " + e.getName() + " no es válido"));
    }

    /** Errores con código propio lanzados por los servicios (por ejemplo 404 "Reserva no encontrada"). */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> conCodigo(ResponseStatusException e) {
        String mensaje = e.getReason() == null || e.getReason().isBlank() ? "Solicitud no válida" : e.getReason();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("mensaje", mensaje));
    }

    /**
     * Último recurso: cualquier error no previsto sale como 500 con un mensaje genérico.
     * El detalle (clase, mensaje, trazas) queda solo en el log del servidor, nunca en la respuesta.
     * Los errores de Spring con su propio código (404, 405, 415, ...) conservan ese código.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> inesperado(Exception e) {
        if (e instanceof ErrorResponse er) {
            HttpStatus estado = HttpStatus.resolve(er.getStatusCode().value());
            String mensaje = switch (estado == null ? HttpStatus.BAD_REQUEST : estado) {
                case NOT_FOUND -> "Recurso no encontrado";
                case METHOD_NOT_ALLOWED -> "Método no permitido";
                case UNSUPPORTED_MEDIA_TYPE -> "Tipo de contenido no soportado";
                default -> "Solicitud no válida";
            };
            return ResponseEntity.status(er.getStatusCode()).body(Map.of("mensaje", mensaje));
        }
        log.error("Error no previsto", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("mensaje", "Ocurrió un error inesperado. Intenta de nuevo"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> manejarDatoInvalido(
            IllegalArgumentException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("mensaje", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> manejarReglaIncumplida(
            IllegalStateException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("mensaje", exception.getMessage()));
    }
}
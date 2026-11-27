package com.gaja.espacios.web;

import com.gaja.espacios.service.ConflictException;
import com.gaja.espacios.service.NotFoundException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> cuerpoIlegible(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "El cuerpo de la solicitud no es válido"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> tipoIncorrecto(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "El parámetro " + e.getName() + " no es válido"));
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
            return ResponseEntity.status(er.getStatusCode()).body(Map.of("error", mensaje));
        }
        log.error("Error no previsto", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Ocurrió un error inesperado. Intenta de nuevo"));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> noEncontrado(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> conflicto(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalido(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .findFirst().orElse("Datos invalidos");
        return ResponseEntity.badRequest().body(Map.of("error", detalle));
    }
}

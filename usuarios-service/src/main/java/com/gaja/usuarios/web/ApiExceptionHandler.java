package com.gaja.usuarios.web;

import com.gaja.usuarios.service.ConflictException;
import com.gaja.usuarios.service.DemasiadosIntentosException;
import com.gaja.usuarios.service.ForbiddenException;
import com.gaja.usuarios.service.NotFoundException;
import com.gaja.usuarios.service.UnauthorizedException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Todos los errores salen como {"error": "mensaje"}, igual que en los otros servicios. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DemasiadosIntentosException.class)
    public ResponseEntity<Map<String, String>> demasiadosIntentos(DemasiadosIntentosException e) {
        return responder(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> cuerpoIlegible(HttpMessageNotReadableException e) {
        return responder(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es válido");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> tipoIncorrecto(MethodArgumentTypeMismatchException e) {
        return responder(HttpStatus.BAD_REQUEST, "El parámetro " + e.getName() + " no es válido");
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
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado. Intenta de nuevo");
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> noEncontrado(NotFoundException e) {
        return responder(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> conflicto(ConflictException e) {
        return responder(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, String>> noAutorizado(UnauthorizedException e) {
        return responder(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, String>> prohibido(ForbiddenException e) {
        return responder(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalido(IllegalArgumentException e) {
        return responder(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalido(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .findFirst().orElse("Datos invalidos");
        return responder(HttpStatus.BAD_REQUEST, detalle);
    }

    private ResponseEntity<Map<String, String>> responder(HttpStatus status, String mensaje) {
        return ResponseEntity.status(status).body(Map.of("error", mensaje));
    }
}

package com.gaja.reservas.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestValidationTest {

    private static Validator validator;
    private static AutoCloseable validationFactory;

    @BeforeAll
    static void crearValidador() {
        var factory = Validation.buildDefaultValidatorFactory();
        validationFactory = factory::close;
        validator = factory.getValidator();
    }

    @AfterAll
    static void cerrarValidador() throws Exception {
        validationFactory.close();
    }

    @Test
    void crearReserva_requiereCorreoFechaEspacioHorarioYMotivo() {
        CrearReservaRequest request = new CrearReservaRequest();

        assertEquals(5, validator.validate(request).size());
    }

    @Test
    void crearReserva_rechazaCorreoInvalidoYMotivoDemasiadoLargo() {
        CrearReservaRequest request = new CrearReservaRequest();
        request.setUsuarioEmail("correo-invalido");
        request.setEspacio("Cancha A");
        request.setFecha(LocalDate.now().plusDays(1));
        request.setHorario("09:00 - 10:00");
        request.setMotivo("x".repeat(501));

        var violations = validator.validate(request);

        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("usuarioEmail")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("motivo")));
    }

    @Test
    void actualizarReglas_rechazaValoresFueraDeRango() {
        var request = new ActualizarReglasReservaRequest(366, 49);

        assertEquals(2, validator.validate(request).size());
    }

    @Test
    void actualizarReglas_aceptaLimitesPermitidos() {
        var request = new ActualizarReglasReservaRequest(365, 48);

        assertTrue(validator.validate(request).isEmpty());
    }
}

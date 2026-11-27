package com.gaja.reservas;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gaja.reservas.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de seguridad: NO usan TestAuthConfig, así que las peticiones van sin token
 * salvo que cada prueba se lo ponga.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeguridadApiTest {

    private static final String CLAVE = "clave-solo-para-pruebas-0123456789-abcdefghij";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwt;

    private String admin() {
        return "Bearer " + jwt.generar(1L, "admin@gaja.cl", "Administrador", "t");
    }

    private String cadete() {
        return "Bearer " + jwt.generar(2L, "cadete@gaja.cl", "Cadete", "t");
    }

    @Test
    void sinTokenSeRespondeNoAutorizado() throws Exception {
        mvc.perform(get("/api/reservas/reglas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje", not(emptyString())));
    }

    @Test
    void unTokenAlteradoSeRechaza() throws Exception {
        mvc.perform(get("/api/reservas/reglas").header("Authorization", cadete() + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenFirmadoConOtraClaveSeRechaza() throws Exception {
        String ajeno = new JwtService("otra-clave-distinta-de-mas-de-32-caracteres", 1)
                .generar(1L, "admin@gaja.cl", "Administrador", "t");
        mvc.perform(get("/api/reservas/reglas").header("Authorization", "Bearer " + ajeno))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenVencidoSeRechaza() throws Exception {
        String vencido = new JwtService(CLAVE, -1).generar(1L, "admin@gaja.cl", "Administrador", "t");
        mvc.perform(get("/api/reservas/reglas").header("Authorization", "Bearer " + vencido))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unCadeteNoPuedeUsarLasAccionesDeAdministrador() throws Exception {
        mvc.perform(get("/api/reservas").header("Authorization", cadete()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/reservas/reglas").header("Authorization", cadete())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/reservas/1/estado").header("Authorization", cadete())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/incidencias").header("Authorization", cadete()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/incidencias/pendientes/count").header("Authorization", cadete()))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/incidencias/1/resolver").header("Authorization", cadete()))
                .andExpect(status().isForbidden());
    }

    @Test
    void elAdministradorPuedeListarReservasEIncidencias() throws Exception {
        mvc.perform(get("/api/reservas").header("Authorization", admin())).andExpect(status().isOk());
        mvc.perform(get("/api/incidencias").header("Authorization", admin())).andExpect(status().isOk());
    }

    @Test
    void cualquierPersonaConSesionVeLasReglas() throws Exception {
        mvc.perform(get("/api/reservas/reglas").header("Authorization", cadete()))
                .andExpect(status().isOk());
    }

    @Test
    void cadeteSoloVeSusPropiasReservasYAlertas() throws Exception {
        mvc.perform(get("/api/reservas/usuario").param("email", "cadete@gaja.cl").header("Authorization", cadete()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/reservas/usuario").param("email", "otra@gaja.cl").header("Authorization", cadete()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/alertas").param("email", "cadete@gaja.cl").header("Authorization", cadete()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/alertas").param("email", "otra@gaja.cl").header("Authorization", cadete()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/alertas/no-leidas").param("email", "otra@gaja.cl").header("Authorization", cadete()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/incidencias/usuario").param("email", "otra@gaja.cl").header("Authorization", cadete()))
                .andExpect(status().isForbidden());
    }

    @Test
    void elAdministradorPuedeVerLosDatosDeOtraPersona() throws Exception {
        mvc.perform(get("/api/reservas/usuario").param("email", "otra@gaja.cl").header("Authorization", admin()))
                .andExpect(status().isOk());
    }

    @Test
    void nadieReservaNiCancelaEnNombreDeOtraPersona() throws Exception {
        String reservaAjena = "{\"usuarioEmail\":\"otra@gaja.cl\",\"espacio\":\"futbol-a\","
                + "\"fecha\":\"2030-01-01\",\"horario\":\"08:00 - 09:00\",\"motivo\":\"Prueba\"}";
        mvc.perform(post("/api/reservas").header("Authorization", cadete())
                        .contentType(MediaType.APPLICATION_JSON).content(reservaAjena))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/reservas/1/cancelar").param("email", "otra@gaja.cl").header("Authorization", cadete()))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/reservas/1/ocultar").param("email", "otra@gaja.cl").header("Authorization", cadete()))
                .andExpect(status().isForbidden());
    }
}

package com.gaja.espacios;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gaja.espacios.security.JwtService;
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
        mvc.perform(get("/api/espacios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", not(emptyString())));
    }

    @Test
    void unTokenAlteradoSeRechaza() throws Exception {
        mvc.perform(get("/api/espacios").header("Authorization", cadete() + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenFirmadoConOtraClaveSeRechaza() throws Exception {
        String ajeno = new JwtService("otra-clave-distinta-de-mas-de-32-caracteres", 1)
                .generar(1L, "admin@gaja.cl", "Administrador", "t");
        mvc.perform(get("/api/espacios").header("Authorization", "Bearer " + ajeno))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenVencidoSeRechaza() throws Exception {
        String vencido = new JwtService(CLAVE, -1).generar(1L, "admin@gaja.cl", "Administrador", "t");
        mvc.perform(get("/api/espacios").header("Authorization", "Bearer " + vencido))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cualquierPersonaConSesionPuedeVerLasCanchas() throws Exception {
        mvc.perform(get("/api/espacios").header("Authorization", cadete())).andExpect(status().isOk());
        mvc.perform(get("/api/categorias").header("Authorization", cadete())).andExpect(status().isOk());
        mvc.perform(get("/api/bloqueos").header("Authorization", cadete())).andExpect(status().isOk());
    }

    @Test
    void unCadeteNoPuedeCrearCambiarNiBorrar() throws Exception {
        mvc.perform(post("/api/categorias").header("Authorization", cadete())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nueva\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/espacios").header("Authorization", cadete())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/espacios/futbol-a/disponibilidad").header("Authorization", cadete())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"available\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/espacios/futbol-a").header("Authorization", cadete()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/espacios/futbol-a/bloqueos").header("Authorization", cadete())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/bloqueos/1").header("Authorization", cadete()))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinTokenTampocoSePuedeBorrar() throws Exception {
        mvc.perform(delete("/api/espacios/futbol-a")).andExpect(status().isUnauthorized());
    }
}

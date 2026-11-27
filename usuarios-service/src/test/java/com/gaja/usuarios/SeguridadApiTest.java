package com.gaja.usuarios;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gaja.usuarios.security.JwtService;
import com.jayway.jsonpath.JsonPath;
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
        mvc.perform(get("/api/usuarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", not(emptyString())));
    }

    @Test
    void unTokenAlteradoSeRechaza() throws Exception {
        mvc.perform(get("/api/usuarios").header("Authorization", cadete() + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenFirmadoConOtraClaveSeRechaza() throws Exception {
        String ajeno = new JwtService("otra-clave-distinta-de-mas-de-32-caracteres", 1)
                .generar(1L, "admin@gaja.cl", "Administrador", "t");
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + ajeno))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenVencidoSeRechaza() throws Exception {
        String vencido = new JwtService(CLAVE, -1).generar(1L, "admin@gaja.cl", "Administrador", "t");
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + vencido))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unCadeteNoPuedeUsarLasAccionesDeAdministrador() throws Exception {
        mvc.perform(get("/api/usuarios").header("Authorization", cadete()))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/usuarios/1/aprobar").header("Authorization", cadete()))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/usuarios/1/desactivar").header("Authorization", cadete()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/usuarios/1").header("Authorization", cadete()))
                .andExpect(status().isForbidden());
    }

    @Test
    void elAdministradorPuedeListarUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios").header("Authorization", admin()))
                .andExpect(status().isOk());
    }

    @Test
    void loginYRegistroSonPublicos() throws Exception {
        mvc.perform(post("/api/usuarios/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@gaja.cl\",\"password\":\"12345\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/usuarios/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"seguridad.registro@gaja.cl\",\"password\":\"abcde\","
                                + "\"nombre\":\"Prueba Seguridad\",\"rol\":\"Cadete\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void elTokenDelLoginSirveParaLosEndpointsProtegidos() throws Exception {
        String respuesta = mvc.perform(post("/api/usuarios/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@gaja.cl\",\"password\":\"12345\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(respuesta, "$.token");

        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void cadeteVeSuPropioPerfilPeroNoElDeOtraPersona() throws Exception {
        String respuesta = mvc.perform(post("/api/usuarios/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cadete@gaja.cl\",\"password\":\"12345\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = "Bearer " + JsonPath.read(respuesta, "$.token");
        Number miId = JsonPath.read(respuesta, "$.id");

        mvc.perform(get("/api/usuarios/por-correo").param("email", "cadete@gaja.cl").header("Authorization", token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/usuarios/por-correo").param("email", "admin@gaja.cl").header("Authorization", token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios/" + miId).header("Authorization", token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/usuarios/" + (miId.longValue() + 1000)).header("Authorization", token))
                .andExpect(status().isForbidden());
    }
}

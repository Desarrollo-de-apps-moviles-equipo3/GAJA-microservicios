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

/** Los errores salen limpios: sin clases, trazas ni datos internos, y con el formato del servicio. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErroresApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwt;

    private String admin() {
        return "Bearer " + jwt.generar(1L, "admin@gaja.cl", "Administrador", "t-errores");
    }

    @Test
    void unJsonMalFormadoDa400SinDetallesInternos() throws Exception {
        mvc.perform(post("/api/espacios").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", not(emptyString())))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("jackson"))));
    }

    @Test
    void unParametroConTipoIncorrectoDa400SinDetallesInternos() throws Exception {
        mvc.perform(get("/api/espacios").param("disponible", "quizas").header("Authorization", admin()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("java.lang"))));
    }

    @Test
    void unaRutaQueNoExisteDa404ConElFormatoDelServicio() throws Exception {
        mvc.perform(get("/api/espacios/x/y/z/w").header("Authorization", admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", not(emptyString())));
    }

    @Test
    void sinTokenSigueDando401AunqueLaRutaNoExista() throws Exception {
        mvc.perform(get("/api/espacios")).andExpect(status().isUnauthorized());
    }
}

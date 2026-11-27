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
        mvc.perform(post("/api/reservas").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", not(emptyString())))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("jackson"))));
    }

    @Test
    void unParametroConTipoIncorrectoDa400SinDetallesInternos() throws Exception {
        mvc.perform(get("/api/reservas/disponibilidad").param("fecha", "no-es-fecha").param("espacio", "A").header("Authorization", admin()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("java.lang"))));
    }

    @Test
    void unaRutaQueNoExisteDa404ConElFormatoDelServicio() throws Exception {
        mvc.perform(get("/api/reservas/x/y/z/w").header("Authorization", admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje", not(emptyString())));
    }

    @Test
    void sinTokenSigueDando401AunqueLaRutaNoExista() throws Exception {
        mvc.perform(get("/api/reservas")).andExpect(status().isUnauthorized());
    }
}

package com.gaja.espacios;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestAuthConfig.class)
@ActiveProfiles("test")
class EspacioApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void listaLosSeisEspaciosDeLaApp() throws Exception {
        mvc.perform(get("/api/espacios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[?(@.id=='futbol-a')].name", contains("Cancha de Fútbol A")))
                .andExpect(jsonPath("$[?(@.id=='futbol-a')].category", contains("Deportes")));
    }

    @Test
    void filtraPorCategoriaYDisponibilidad() throws Exception {
        mvc.perform(get("/api/espacios").param("categoria", "Recintos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/espacios").param("disponible", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is("futbol-b")));
    }

    @Test
    void crearEditarDeshabilitarYEliminarUnEspacio() throws Exception {
        String body = """
                {"name":"Piscina Olímpica","category":"Recintos","description":"Piscina techada",
                 "capacity":20,"tags":["Natación"],"latitude":-33.405,"longitude":-70.577}
                """;
        mvc.perform(post("/api/espacios").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("piscina-olimpica")))
                .andExpect(jsonPath("$.available", is(true)));

        mvc.perform(patch("/api/espacios/piscina-olimpica/disponibilidad")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"available\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available", is(false)));

        mvc.perform(delete("/api/espacios/piscina-olimpica")).andExpect(status().isNoContent());
        mvc.perform(get("/api/espacios/piscina-olimpica")).andExpect(status().isNotFound());
    }

    @Test
    void noPermiteCrearConCategoriaInexistenteNiDatosInvalidos() throws Exception {
        mvc.perform(post("/api/espacios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"category\":\"Nada\",\"capacity\":5}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/espacios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"category\":\"Deportes\",\"capacity\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void bloqueaUnHorarioYNoDejaDuplicarlo() throws Exception {
        String body = "{\"date\":\"2026-11-25\",\"timeSlot\":\"08:00 - 10:00\",\"reason\":\"Mantención\"}";
        mvc.perform(post("/api/espacios/tenis-a/bloqueos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.spaceId", is("tenis-a")))
                .andExpect(jsonPath("$.timeSlot", is("08:00 - 10:00")));
        mvc.perform(post("/api/espacios/tenis-a/bloqueos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/espacios/tenis-a/bloqueos").param("fecha", "2026-11-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void categoriasSeCreanSinRepetirseYNoSeBorranSiTienenEspacios() throws Exception {
        mvc.perform(post("/api/categorias").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Piscinas\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/categorias").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"piscinas\"}"))
                .andExpect(status().isConflict());

        String json = mvc.perform(get("/api/categorias")).andReturn().getResponse().getContentAsString();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"id\":(\\d+),\"name\":\"Deportes\"").matcher(json);
        org.junit.jupiter.api.Assertions.assertTrue(m.find(), json);
        mvc.perform(delete("/api/categorias/" + m.group(1))).andExpect(status().isConflict());
    }
}

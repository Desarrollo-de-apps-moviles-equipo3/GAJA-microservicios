package com.gaja.usuarios;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gaja.usuarios.security.JwtService;
import com.gaja.usuarios.web.ApiExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Pruebas de abuso: lo que intentaría una persona malintencionada contra la API de usuarios. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AbusoApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwt;

    private String admin() {
        return "Bearer " + jwt.generar(1L, "admin@gaja.cl", "Administrador", "abuso-admin");
    }

    private String cadete() {
        return "Bearer " + jwt.generar(2L, "cadete@gaja.cl", "Cadete", "abuso-cadete");
    }

    private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                               String cuerpo) throws Exception {
        return mvc.perform(req.contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(cuerpo));
    }

    private static String b64(String texto) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }

    // ---------- Inyección ----------

    @Test
    void inyeccionSqlEnElLoginNoEntraNiRompeNada() throws Exception {
        json(post("/api/usuarios/login"), "{\"email\":\"' OR '1'='1\",\"password\":\"' OR '1'='1\"}")
                .andExpect(status().isUnauthorized());
        json(post("/api/usuarios/login"), "{\"email\":\"admin@gaja.cl'--\",\"password\":\"x\"}")
                .andExpect(status().isUnauthorized());
        json(post("/api/usuarios/login"), "{\"email\":\"\\\"; DROP TABLE usuario; --\",\"password\":\"x\"}")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inyeccionSqlEnLosParametrosDeConsultaNoDevuelveDatosDeMas() throws Exception {
        mvc.perform(get("/api/usuarios/por-correo").param("email", "x' OR '1'='1").header("Authorization", admin()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/usuarios").param("estado", "' OR 1=1 --").header("Authorization", admin()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/usuarios").param("rol", "' OR 1=1 --").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/auditoria").param("accion", "' OR 1=1 --").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ---------- Tokens falsificados ----------

    @Test
    void unTokenSinFirmaConAlgoritmoNoneSeRechaza() throws Exception {
        String sinFirma = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "."
                + b64("{\"sub\":\"admin@gaja.cl\",\"id\":1,\"rol\":\"Administrador\",\"jti\":\"x\"}") + ".";
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + sinFirma))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cambiarElRolDentroDelTokenInvalidaLaFirma() throws Exception {
        String[] partes = cadete().substring("Bearer ".length()).split("\\.");
        String payloadFalso = b64("{\"jti\":\"abuso-cadete\",\"sub\":\"cadete@gaja.cl\",\"id\":2,"
                + "\"rol\":\"Administrador\",\"iat\":1,\"exp\":99999999999}");
        String falsificado = partes[0] + "." + payloadFalso + "." + partes[2];
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + falsificado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void otrosEsquemasDeAutorizacionNoSirven() throws Exception {
        mvc.perform(get("/api/usuarios").header("Authorization", "Basic YWRtaW46MTIzNDU="))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer null"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Acceso a datos de otras personas y a funciones de administrador ----------

    @Test
    void unCadeteNoPuedeTocarLosDatosDeOtraPersonaNiLasFuncionesDeAdmin() throws Exception {
        String c = cadete();
        mvc.perform(get("/api/usuarios/1").header("Authorization", c)).andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios/1/sesiones").header("Authorization", c)).andExpect(status().isForbidden());
        json(put("/api/usuarios/1").header("Authorization", c), "{\"nombre\":\"Hackeado\"}")
                .andExpect(status().isForbidden());
        json(patch("/api/usuarios/1/password").header("Authorization", c),
                "{\"actual\":\"12345\",\"nueva\":\"hackeada1\"}").andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios/por-correo").param("email", "admin@gaja.cl").header("Authorization", c))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/usuarios/3/aprobar").header("Authorization", c)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/usuarios/3").header("Authorization", c)).andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios").header("Authorization", c)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auditoria").header("Authorization", c)).andExpect(status().isForbidden());
    }

    // ---------- Datos que el cliente no debe poder imponer ----------

    @Test
    void registrarseNoPermiteElegirElEstadoNiElIdNiSerAdministrador() throws Exception {
        json(post("/api/usuarios/registro"),
                "{\"email\":\"abuso1@gaja.cl\",\"password\":\"abcde\",\"nombre\":\"X\",\"rol\":\"Cadete\","
                        + "\"estado\":\"APROBADO\",\"id\":1,\"passwordHash\":\"x\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado", is("PENDIENTE")))
                .andExpect(jsonPath("$.id", not(1)));
        json(post("/api/usuarios/registro"),
                "{\"email\":\"abuso2@gaja.cl\",\"password\":\"abcde\",\"nombre\":\"X\",\"rol\":\"Administrador\"}")
                .andExpect(status().isBadRequest());
    }

    // ---------- Entradas enormes o mal formadas ----------

    @Test
    void loscamposDemasiadoLargosSeRechazan() throws Exception {
        String largo = "a".repeat(10_000);
        json(post("/api/usuarios/registro"),
                "{\"email\":\"abuso3@gaja.cl\",\"password\":\"abcde\",\"nombre\":\"" + largo + "\",\"rol\":\"Cadete\"}")
                .andExpect(status().isBadRequest());
        json(post("/api/usuarios/registro"),
                "{\"email\":\"abuso4@gaja.cl\",\"password\":\"" + largo + "\",\"nombre\":\"X\",\"rol\":\"Cadete\"}")
                .andExpect(status().isBadRequest());
        json(post("/api/usuarios/login"), "{\"email\":\"" + largo + "@gaja.cl\",\"password\":\"x\"}")
                .andExpect(status().isBadRequest());
        json(post("/api/usuarios/login"), "{\"email\":\"a@gaja.cl\",\"password\":\"" + largo + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void unJsonMalFormadoDa400SinDetallesInternos() throws Exception {
        mvc.perform(post("/api/usuarios/login").contentType(MediaType.APPLICATION_JSON).content("{esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", not(emptyString())))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("jackson"))));
    }

    @Test
    void unIdQueNoEsNumeroDa400SinDetallesInternos() throws Exception {
        mvc.perform(get("/api/usuarios/abc").header("Authorization", admin()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("java.lang"))));
    }

    @Test
    void rutasMetodosYContenidosInvalidosRespondenConErroresLimpios() throws Exception {
        mvc.perform(get("/api/usuarios/x/y/z").header("Authorization", admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", not(emptyString())));
        mvc.perform(put("/api/usuarios").header("Authorization", admin()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error", not(emptyString())));
        mvc.perform(post("/api/usuarios/login").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error", not(emptyString())));
    }

    // ---------- Errores inesperados ----------

    @Test
    void unErrorInesperadoSale_como500_sinContarNadaInterno() {
        var respuesta = new ApiExceptionHandler()
                .inesperado(new RuntimeException("password=SECRETO jdbc:postgresql://host/db"));

        Assertions.assertEquals(500, respuesta.getStatusCode().value());
        String cuerpo = String.valueOf(respuesta.getBody());
        Assertions.assertFalse(cuerpo.contains("SECRETO"));
        Assertions.assertFalse(cuerpo.contains("jdbc"));
        Assertions.assertFalse(cuerpo.contains("RuntimeException"));
    }
}

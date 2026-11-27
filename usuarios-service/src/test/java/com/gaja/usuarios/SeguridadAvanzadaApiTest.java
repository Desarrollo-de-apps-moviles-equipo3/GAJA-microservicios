package com.gaja.usuarios;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gaja.usuarios.security.JwtService;
import com.gaja.usuarios.service.CorreoService;
import com.jayway.jsonpath.JsonPath;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Sesión (cierre y renovación), bloqueo por intentos, recuperar contraseña y auditoría.
 * No usa TestAuthConfig: cada prueba pone (o no) su propio token. Cada una crea su propio usuario.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeguridadAvanzadaApiTest {

    private static final AtomicInteger CONTADOR = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwt;

    @MockBean
    CorreoService correo;

    // ---------- Ayudas ----------

    private String admin() {
        return "Bearer " + jwt.generar(1L, "admin@gaja.cl", "Administrador", "t-admin");
    }

    private String cadeteAjeno() {
        return "Bearer " + jwt.generar(2L, "cadete@gaja.cl", "Cadete", "t-cadete");
    }

    private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                               String cuerpo) throws Exception {
        return mvc.perform(req.contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(cuerpo));
    }

    private ResultActions login(String email, String password) throws Exception {
        return json(post("/api/usuarios/login"), "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    /** El administrador crea un cadete aprobado con clave "abcde" y devuelve su correo. */
    private String usuarioNuevo() throws Exception {
        String email = "avanzada" + CONTADOR.incrementAndGet() + "@gaja.cl";
        json(post("/api/usuarios").header("Authorization", admin()),
                "{\"email\":\"" + email + "\",\"password\":\"abcde\",\"nombre\":\"Persona de Prueba\",\"rol\":\"Cadete\"}")
                .andExpect(status().isCreated());
        return email;
    }

    private String tokenDe(String email, String password) throws Exception {
        String cuerpo = login(email, password).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.token");
    }

    private ResultActions pedirCodigo(String email) throws Exception {
        return json(post("/api/usuarios/recuperar"), "{\"email\":\"" + email + "\"}");
    }

    private String codigoEnviadoA(String email) {
        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(correo).enviarCodigoRecuperacion(eq(email), codigo.capture());
        return codigo.getValue();
    }

    private ResultActions restablecer(String email, String codigo, String nueva) throws Exception {
        return json(post("/api/usuarios/restablecer"),
                "{\"email\":\"" + email + "\",\"codigo\":\"" + codigo + "\",\"nueva\":\"" + nueva + "\"}");
    }

    // ---------- Bloqueo por intentos fallidos ----------

    @Test
    void tras5IntentosFallidosLaCuentaQuedaBloqueadaAunqueLaClaveSeaCorrecta() throws Exception {
        String email = usuarioNuevo();
        for (int i = 0; i < 5; i++) {
            login(email, "mala").andExpect(status().isUnauthorized());
        }
        login(email, "mala").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error", containsString("Intenta de nuevo")));
        login(email, "abcde").andExpect(status().isTooManyRequests());

        mvc.perform(get("/api/auditoria").param("accion", "LOGIN_BLOQUEADO").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].actor", hasItem(email)));
    }

    @Test
    void unInicioDeSesionCorrectoReiniciaElContador() throws Exception {
        String email = usuarioNuevo();
        for (int i = 0; i < 4; i++) {
            login(email, "mala").andExpect(status().isUnauthorized());
        }
        login(email, "abcde").andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            login(email, "mala").andExpect(status().isUnauthorized());
        }
        login(email, "abcde").andExpect(status().isOk());
    }

    @Test
    void elBloqueoTambienAplicaACorreosQueNoExisten() throws Exception {
        String inexistente = "fantasma" + CONTADOR.incrementAndGet() + "@gaja.cl";
        for (int i = 0; i < 5; i++) {
            login(inexistente, "x").andExpect(status().isUnauthorized());
        }
        login(inexistente, "x").andExpect(status().isTooManyRequests());
    }

    // ---------- Cierre de sesión y renovación ----------

    @Test
    void despuesDeCerrarSesionElTokenYaNoSirve() throws Exception {
        String email = usuarioNuevo();
        String token = "Bearer " + tokenDe(email, "abcde");

        mvc.perform(get("/api/usuarios/por-correo").param("email", email).header("Authorization", token))
                .andExpect(status().isOk());

        mvc.perform(post("/api/usuarios/logout").header("Authorization", token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/usuarios/por-correo").param("email", email).header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cerrarSesionSinTokenDa401() throws Exception {
        mvc.perform(post("/api/usuarios/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    void renovarEntregaUnTokenQueFunciona() throws Exception {
        String email = usuarioNuevo();
        String token = "Bearer " + tokenDe(email, "abcde");

        String cuerpo = mvc.perform(post("/api/usuarios/renovar").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyString())))
                .andReturn().getResponse().getContentAsString();
        String nuevo = "Bearer " + JsonPath.<String>read(cuerpo, "$.token");

        mvc.perform(get("/api/usuarios/por-correo").param("email", email).header("Authorization", nuevo))
                .andExpect(status().isOk());
    }

    @Test
    void unaSesionCerradaNoSePuedeRenovar() throws Exception {
        String email = usuarioNuevo();
        String token = "Bearer " + tokenDe(email, "abcde");
        mvc.perform(post("/api/usuarios/logout").header("Authorization", token)).andExpect(status().isNoContent());

        mvc.perform(post("/api/usuarios/renovar").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void renovarSinTokenDa401() throws Exception {
        mvc.perform(post("/api/usuarios/renovar")).andExpect(status().isUnauthorized());
    }

    @Test
    void unaCuentaDesactivadaPierdeSusSesionesYNoPuedeRenovar() throws Exception {
        String email = usuarioNuevo();
        String token = "Bearer " + tokenDe(email, "abcde");
        String id = JsonPath.read(
                mvc.perform(get("/api/usuarios/por-correo").param("email", email).header("Authorization", token))
                        .andReturn().getResponse().getContentAsString(), "$.id").toString();

        mvc.perform(patch("/api/usuarios/" + id + "/desactivar").header("Authorization", admin()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/usuarios/por-correo").param("email", email).header("Authorization", token))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/usuarios/renovar").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Recuperar contraseña ----------

    @Test
    void recuperarLaContrasenaConElCodigoDelCorreo() throws Exception {
        String email = usuarioNuevo();
        String tokenViejo = "Bearer " + tokenDe(email, "abcde");

        pedirCodigo(email).andExpect(status().isOk()).andExpect(jsonPath("$.mensaje", not(emptyString())));
        String codigo = codigoEnviadoA(email);

        restablecer(email, codigo, "nueva123").andExpect(status().isNoContent());

        login(email, "nueva123").andExpect(status().isOk());
        login(email, "abcde").andExpect(status().isUnauthorized());

        // La sesión que había antes se cierra
        mvc.perform(get("/api/usuarios/por-correo").param("email", email).header("Authorization", tokenViejo))
                .andExpect(status().isUnauthorized());

        // El código sirve una sola vez
        restablecer(email, codigo, "otraclave1").andExpect(status().isBadRequest());
        login(email, "nueva123").andExpect(status().isOk());
    }

    @Test
    void elCodigoSeAceptaConMinusculasYEspacios() throws Exception {
        String email = usuarioNuevo();
        pedirCodigo(email).andExpect(status().isOk());
        String codigo = codigoEnviadoA(email);
        String escrito = " " + codigo.substring(0, 4).toLowerCase() + "-" + codigo.substring(4).toLowerCase() + " ";

        restablecer(email, escrito, "nueva123").andExpect(status().isNoContent());
    }

    @Test
    void pedirCodigoParaUnCorreoInexistenteRespondeIgualYNoEnviaNada() throws Exception {
        pedirCodigo("nadie.registrado@gaja.cl").andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje", not(emptyString())));
        verify(correo, never()).enviarCodigoRecuperacion(anyString(), anyString());
    }

    @Test
    void unCodigoIncorrectoSeRechazaYTras5IntentosElCodigoSeInvalida() throws Exception {
        String email = usuarioNuevo();
        pedirCodigo(email).andExpect(status().isOk());
        String codigo = codigoEnviadoA(email);

        for (int i = 0; i < 5; i++) {
            restablecer(email, "AAAAAAAA", "nueva123").andExpect(status().isBadRequest());
        }
        restablecer(email, codigo, "nueva123").andExpect(status().isBadRequest());
        login(email, "abcde").andExpect(status().isOk());
    }

    @Test
    void solo3SolicitudesDeCodigoPorHora() throws Exception {
        String email = usuarioNuevo();
        for (int i = 0; i < 5; i++) {
            pedirCodigo(email).andExpect(status().isOk());
        }
        verify(correo, times(3)).enviarCodigoRecuperacion(eq(email), anyString());
    }

    @Test
    void laContrasenaNuevaDebeCumplirLaLongitudMinima() throws Exception {
        String email = usuarioNuevo();
        pedirCodigo(email).andExpect(status().isOk());
        String codigo = codigoEnviadoA(email);

        restablecer(email, codigo, "abc").andExpect(status().isBadRequest());
        restablecer(email, codigo, "nueva123").andExpect(status().isNoContent());
    }

    // ---------- Auditoría ----------

    @Test
    void laAuditoriaSoloLaLeeElAdministrador() throws Exception {
        mvc.perform(get("/api/auditoria")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auditoria").header("Authorization", cadeteAjeno())).andExpect(status().isForbidden());
        mvc.perform(get("/api/auditoria").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(java.util.List.class)));
    }

    @Test
    void lasAccionesDelAdministradorQuedanRegistradas() throws Exception {
        String email = usuarioNuevo();
        String id = JsonPath.read(
                mvc.perform(get("/api/usuarios/por-correo").param("email", email).header("Authorization", admin()))
                        .andReturn().getResponse().getContentAsString(), "$.id").toString();
        mvc.perform(patch("/api/usuarios/" + id + "/desactivar").header("Authorization", admin()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/auditoria").param("accion", "USUARIO_DESACTIVADO").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].detalle", hasItem("id=" + id)))
                .andExpect(jsonPath("$[*].actor", hasItem("admin@gaja.cl")));
    }

    @Test
    void elRegistroNuncaGuardaContrasenasNiCodigos() throws Exception {
        String email = usuarioNuevo();
        login(email, "claveSecretaXYZ").andExpect(status().isUnauthorized());
        String cuerpo = mvc.perform(get("/api/auditoria").param("limite", "500").header("Authorization", admin()))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(cuerpo.contains("claveSecretaXYZ"));
    }
}

package com.gaja.usuarios;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestAuthConfig.class)
@ActiveProfiles("test")
class UsuarioApiTest {

    private static final AtomicInteger CONTADOR = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    // ---------- Ayudas ----------

    private ResultActions enviar(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                                 String json) throws Exception {
        return mvc.perform(req.contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(json));
    }

    private ResultActions login(String email, String password, String rol) throws Exception {
        return enviar(post("/api/usuarios/login"),
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"rol\":\"" + rol + "\"}");
    }

    private String correoNuevo() {
        return "test" + CONTADOR.incrementAndGet() + "@gaja.cl";
    }

    /** Registra un cadete (queda PENDIENTE) y devuelve su id. */
    private long registrar(String email) throws Exception {
        String body = enviar(post("/api/usuarios/registro"),
                "{\"email\":\"" + email + "\",\"password\":\"abcde\",\"nombre\":\"Persona de Prueba\",\"rol\":\"Cadete\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    // ---------- Login ----------

    @Test
    void elAdminDeLaSemillaPuedeIniciarSesionYNoSeDevuelveLaClave() throws Exception {
        login("admin@gaja.cl", "12345", "Administrador")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("admin@gaja.cl")))
                .andExpect(jsonPath("$.rol", is("Administrador")))
                .andExpect(jsonPath("$.estado", is("APROBADO")))
                .andExpect(jsonPath("$.token", not(emptyString())))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void loginIgnoraMayusculasEnElCorreo() throws Exception {
        login("CADETE@gaja.cl", "12345", "Cadete").andExpect(status().isOk());
    }

    @Test
    void clavePorCorreoOPorRolIncorrectoDa401() throws Exception {
        login("cadete@gaja.cl", "mala", "Cadete").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Correo, contraseña o rol incorrectos")));
        login("cadete@gaja.cl", "12345", "Oficial").andExpect(status().isUnauthorized());
        login("nadie@gaja.cl", "12345", "Cadete").andExpect(status().isUnauthorized());
    }

    @Test
    void unPerfilPendienteRechazadoODesactivadoNoEntra() throws Exception {
        login("cadete.nuevo@gaja.cl", "12345", "Cadete").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", containsString("pendiente")));
        login("cadete.rechazado@gaja.cl", "12345", "Cadete").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", containsString("nómina")));
        login("oficial.baja@gaja.cl", "12345", "Oficial").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", containsString("desactivado")));
    }

    // ---------- Registro y aprobacion ----------

    @Test
    void registrarDejaElPerfilPendienteYAprobarloLoDejaEntrar() throws Exception {
        String email = correoNuevo();
        long id = registrar(email);

        login(email, "abcde", "Cadete").andExpect(status().isForbidden());

        mvc.perform(patch("/api/usuarios/" + id + "/aprobar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("APROBADO")));

        login(email, "abcde", "Cadete").andExpect(status().isOk());
    }

    @Test
    void noSePuedeRegistrarUnAdministradorNiUnRolInventadoNiRepetirElCorreo() throws Exception {
        String email = correoNuevo();
        enviar(post("/api/usuarios/registro"),
                "{\"email\":\"" + email + "\",\"password\":\"abcde\",\"nombre\":\"X\",\"rol\":\"Administrador\"}")
                .andExpect(status().isBadRequest());
        enviar(post("/api/usuarios/registro"),
                "{\"email\":\"" + email + "\",\"password\":\"abcde\",\"nombre\":\"X\",\"rol\":\"Marciano\"}")
                .andExpect(status().isBadRequest());

        registrar(email);
        enviar(post("/api/usuarios/registro"),
                "{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"abcde\",\"nombre\":\"X\",\"rol\":\"Cadete\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void validaCorreoYLargoDeLaClave() throws Exception {
        enviar(post("/api/usuarios/registro"),
                "{\"email\":\"no-es-correo\",\"password\":\"abcde\",\"nombre\":\"X\",\"rol\":\"Cadete\"}")
                .andExpect(status().isBadRequest());
        enviar(post("/api/usuarios/registro"),
                "{\"email\":\"" + correoNuevo() + "\",\"password\":\"abc\",\"nombre\":\"X\",\"rol\":\"Cadete\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void elAdminCreaUnUsuarioYNaceAprobado() throws Exception {
        String email = correoNuevo();
        enviar(post("/api/usuarios"),
                "{\"email\":\"" + email + "\",\"password\":\"abcde\",\"nombre\":\"Oficial Nuevo\",\"rol\":\"Oficial\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado", is("APROBADO")));
        login(email, "abcde", "Oficial").andExpect(status().isOk());
    }

    @Test
    void rechazarExigeMotivoYElMotivoQuedaGuardado() throws Exception {
        long id = registrar(correoNuevo());

        enviar(patch("/api/usuarios/" + id + "/rechazar"), "{\"motivo\":\"\"}")
                .andExpect(status().isBadRequest());

        enviar(patch("/api/usuarios/" + id + "/rechazar"), "{\"motivo\":\"Datos incompletos\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("RECHAZADO")))
                .andExpect(jsonPath("$.motivoRechazo", is("Datos incompletos")));

        mvc.perform(patch("/api/usuarios/" + id + "/aprobar")).andExpect(status().isConflict());
    }

    @Test
    void desactivarYActivarSoloEnElEstadoCorrecto() throws Exception {
        long id = registrar(correoNuevo());

        mvc.perform(patch("/api/usuarios/" + id + "/desactivar")).andExpect(status().isConflict());
        mvc.perform(patch("/api/usuarios/" + id + "/aprobar")).andExpect(status().isOk());
        mvc.perform(patch("/api/usuarios/" + id + "/desactivar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("DESACTIVADO")));
        mvc.perform(patch("/api/usuarios/" + id + "/activar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("APROBADO")));
        mvc.perform(patch("/api/usuarios/" + id + "/activar")).andExpect(status().isConflict());
    }

    // ---------- Consulta ----------

    @Test
    void listaFiltrandoPorEstadoYPorRol() throws Exception {
        mvc.perform(get("/api/usuarios").param("estado", "PENDIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].estado", everyItem(is("PENDIENTE"))))
                .andExpect(jsonPath("$[*].email", hasItem("cadete.nuevo@gaja.cl")));

        mvc.perform(get("/api/usuarios").param("rol", "Administrador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].rol", everyItem(is("Administrador"))));

        mvc.perform(get("/api/usuarios").param("estado", "RECHAZADO").param("rol", "Cadete"))
                .andExpect(jsonPath("$[*].email", hasItem("cadete.rechazado@gaja.cl")));

        mvc.perform(get("/api/usuarios").param("estado", "VOLANDO")).andExpect(status().isBadRequest());
    }

    @Test
    void buscarPorCorreoDevuelveElPerfilSinImportarMayusculas() throws Exception {
        mvc.perform(get("/api/usuarios/por-correo").param("email", "CADETE@gaja.cl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("cadete@gaja.cl")))
                .andExpect(jsonPath("$.rol", is("Cadete")));

        mvc.perform(get("/api/usuarios/por-correo").param("email", "nadie@gaja.cl"))
                .andExpect(status().isNotFound());
    }

    @Test
    void obtenerUnUsuarioInexistenteDa404() throws Exception {
        mvc.perform(get("/api/usuarios/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Usuario no encontrado")));
    }

    @Test
    void unPerfilExternoDebeInformarSuInstitucionYLaConserva() throws Exception {
        String email = correoNuevo();
        enviar(post("/api/usuarios/registro"), """
                {"email":"%s","password":"abcde","nombre":"Persona externa",
                 "rol":"Institución externa","institucion":"   "}
                """.formatted(email))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("institución externa")));

        enviar(post("/api/usuarios/registro"), """
                {"email":"%s","password":"abcde","nombre":"Persona externa",
                 "rol":"Institución externa","institucion":" Club Deportivo "}
                """.formatted(email))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado", is("PENDIENTE")))
                .andExpect(jsonPath("$.institucion", is("Club Deportivo")));

        mvc.perform(get("/api/usuarios/por-correo").param("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.institucion", is("Club Deportivo")));
    }

    // ---------- Perfil del usuario ----------

    @Test
    void editarLosDatosPersonalesQuedaGuardado() throws Exception {
        long id = registrar(correoNuevo());

        enviar(put("/api/usuarios/" + id),
                "{\"nombre\":\"Nombre Editado\",\"telefono\":\"+56911112222\",\"notifAlertas\":false}")
                .andExpect(status().isOk());

        mvc.perform(get("/api/usuarios/" + id))
                .andExpect(jsonPath("$.nombre", is("Nombre Editado")))
                .andExpect(jsonPath("$.telefono", is("+56911112222")))
                .andExpect(jsonPath("$.notifAlertas", is(false)))
                .andExpect(jsonPath("$.notifReservas", is(true)));

        enviar(put("/api/usuarios/" + id), "{\"nombre\":\"\"}").andExpect(status().isBadRequest());
    }

    @Test
    void cambiarLaContrasenaExigeLaActualYLaNuevaServiraParaEntrar() throws Exception {
        String email = correoNuevo();
        long id = registrar(email);
        mvc.perform(patch("/api/usuarios/" + id + "/aprobar")).andExpect(status().isOk());

        enviar(patch("/api/usuarios/" + id + "/password"), "{\"actual\":\"equivocada\",\"nueva\":\"nueva1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("actual")));

        enviar(patch("/api/usuarios/" + id + "/password"), "{\"actual\":\"abcde\",\"nueva\":\"nueva1\"}")
                .andExpect(status().isNoContent());

        login(email, "abcde", "Cadete").andExpect(status().isUnauthorized());
        login(email, "nueva1", "Cadete").andExpect(status().isOk());
    }

    @Test
    void cadaLoginExitosoQuedaEnElRegistroDeSesiones() throws Exception {
        String email = correoNuevo();
        long id = registrar(email);
        mvc.perform(patch("/api/usuarios/" + id + "/aprobar")).andExpect(status().isOk());

        mvc.perform(get("/api/usuarios/" + id + "/sesiones")).andExpect(jsonPath("$", hasSize(0)));

        login(email, "abcde", "Cadete").andExpect(status().isOk());
        login(email, "mala", "Cadete").andExpect(status().isUnauthorized());
        login(email, "abcde", "Cadete").andExpect(status().isOk());

        mvc.perform(get("/api/usuarios/" + id + "/sesiones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void eliminarUnUsuarioLoQuitaJuntoConSusSesiones() throws Exception {
        String email = correoNuevo();
        long id = registrar(email);
        mvc.perform(patch("/api/usuarios/" + id + "/aprobar")).andExpect(status().isOk());
        login(email, "abcde", "Cadete").andExpect(status().isOk());

        mvc.perform(delete("/api/usuarios/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/usuarios/" + id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/usuarios/" + id)).andExpect(status().isNotFound());
    }
}

package com.gaja.reservas;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gaja.reservas.model.EstadoReserva;
import com.gaja.reservas.model.Reserva;
import com.gaja.reservas.repo.ReservaRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pruebas de punta a punta (HTTP -> servicio -> H2) de reservas, reglas editables, alertas e incidencias.
 * Complementan a ReservaServiceTest, que prueba el servicio con repositorios simulados.
 * Cada prueba corre dentro de una transacción que se deshace al terminar, así que ninguna
 * deja datos para la siguiente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReservaApiTest {

    private static final ZoneId SANTIAGO = ZoneId.of("America/Santiago");

    @Autowired
    MockMvc mvc;

    @Autowired
    ReservaRepository reservaRepository;

    // ---------- Ayudas ----------

    private static LocalDate hoy() {
        return LocalDate.now(SANTIAGO);
    }

    private ResultActions enviar(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String json)
            throws Exception {
        return mvc.perform(req.contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(json));
    }

    private static String json(String email, String espacio, LocalDate fecha, String horario, String motivo) {
        return "{\"usuarioEmail\":\"" + email + "\",\"espacio\":\"" + espacio + "\",\"fecha\":\"" + fecha
                + "\",\"horario\":\"" + horario + "\",\"motivo\":\"" + motivo + "\"}";
    }

    private ResultActions solicitar(String email, String espacio, LocalDate fecha, String horario) throws Exception {
        return enviar(post("/api/reservas"), json(email, espacio, fecha, horario, "Entrenamiento"));
    }

    /** Crea una reserva por la API y devuelve su id. */
    private long crear(String email, String espacio, LocalDate fecha, String horario) throws Exception {
        String body = solicitar(email, espacio, fecha, horario)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions cambiarEstado(long id, String estado, String motivo) throws Exception {
        String cuerpo = motivo == null
                ? "{\"estado\":\"" + estado + "\"}"
                : "{\"estado\":\"" + estado + "\",\"motivoRechazo\":\"" + motivo + "\"}";
        return enviar(patch("/api/reservas/" + id + "/estado"), cuerpo);
    }

    // ---------- Crear reserva ----------

    @Test
    void unaSolicitudValidaQuedaPendienteYGeneraUnaAlerta() throws Exception {
        LocalDate fecha = hoy().plusDays(3);

        solicitar("cadete@gaja.cl", "Cancha de Fútbol A", fecha, "10:00 - 11:00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.espacio").value("Cancha de Fútbol A"));

        mvc.perform(get("/api/alertas").param("email", "cadete@gaja.cl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].mensaje", containsString("pendiente de aprobación")))
                .andExpect(jsonPath("$[0].leida").value(false));
    }

    @Test
    void noSePuedeReservarUnaFechaPasada() throws Exception {
        solicitar("cadete@gaja.cl", "Cancha A", hoy().minusDays(1), "10:00 - 11:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("No puedes reservar una fecha pasada"));
    }

    @Test
    void hoyEsUnaFechaValida() throws Exception {
        solicitar("cadete@gaja.cl", "Cancha A", hoy(), "22:00 - 23:00")
                .andExpect(status().isCreated());
    }

    @Test
    void noSePuedeReservarMasAllaDeLosSieteDias() throws Exception {
        solicitar("cadete@gaja.cl", "Cancha A", hoy().plusDays(8), "10:00 - 11:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Solo puedes reservar hasta 7 días adelante"));
    }

    @Test
    void el_septimo_dia_es_el_ultimo_permitido() throws Exception {
        solicitar("cadete@gaja.cl", "Cancha A", hoy().plusDays(7), "10:00 - 11:00")
                .andExpect(status().isCreated());
    }

    @Test
    void unHorarioOcupadoSeRechazaConConflicto() throws Exception {
        LocalDate fecha = hoy().plusDays(2);
        crear("uno@gaja.cl", "Cancha A", fecha, "10:00 - 11:00");

        solicitar("dos@gaja.cl", "Cancha A", fecha, "10:00 - 11:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Ese horario ya está ocupado"));
    }

    @Test
    void elMismoHorarioEnOtraCanchaEstaLibre() throws Exception {
        LocalDate fecha = hoy().plusDays(2);
        crear("uno@gaja.cl", "Cancha A", fecha, "10:00 - 11:00");

        solicitar("dos@gaja.cl", "Cancha B", fecha, "10:00 - 11:00")
                .andExpect(status().isCreated());
    }

    @Test
    void unHorarioRechazadoVuelveAEstarLibre() throws Exception {
        LocalDate fecha = hoy().plusDays(2);
        long id = crear("uno@gaja.cl", "Cancha A", fecha, "10:00 - 11:00");
        cambiarEstado(id, "RECHAZADA", "Cancha en mantención").andExpect(status().isOk());

        solicitar("dos@gaja.cl", "Cancha A", fecha, "10:00 - 11:00")
                .andExpect(status().isCreated());
    }

    @Test
    void unDiaNoTieneTopeDeReservas() throws Exception {
        LocalDate fecha = hoy().plusDays(4);
        crear("a@gaja.cl", "Cancha A", fecha, "08:00 - 09:00");
        crear("b@gaja.cl", "Cancha A", fecha, "09:00 - 10:00");
        crear("c@gaja.cl", "Cancha A", fecha, "10:00 - 11:00");

        solicitar("d@gaja.cl", "Cancha B", fecha, "12:00 - 13:00")
                .andExpect(status().isCreated());
    }

    @Test
    void unRangoDeVariasHorasOcupaTodasLasHorasDelIntervalo() throws Exception {
        LocalDate fecha = hoy().plusDays(2);
        crear("uno@gaja.cl", "Cancha A", fecha, "09:00 - 12:00");

        solicitar("dos@gaja.cl", "Cancha A", fecha, "10:00 - 11:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Ese horario ya está ocupado"));
        solicitar("dos@gaja.cl", "Cancha A", fecha, "11:00 - 13:00")
                .andExpect(status().isConflict());
        // Rangos consecutivos no se cruzan.
        solicitar("dos@gaja.cl", "Cancha A", fecha, "12:00 - 13:00")
                .andExpect(status().isCreated());
    }

    @Test
    void elHorarioDebeEstarEntreLas8YLas23() throws Exception {
        LocalDate fecha = hoy().plusDays(2);
        solicitar("a@gaja.cl", "Cancha A", fecha, "07:00 - 08:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El horario debe estar entre 08:00 y 23:00"));
        solicitar("a@gaja.cl", "Cancha A", fecha, "22:00 - 23:30")
                .andExpect(status().isBadRequest());
        solicitar("a@gaja.cl", "Cancha A", fecha, "22:00 - 23:00")
                .andExpect(status().isCreated());
    }

    @Test
    void soloSeAceptanHorasCompletasYFormatoValido() throws Exception {
        LocalDate fecha = hoy().plusDays(2);
        solicitar("a@gaja.cl", "Cancha A", fecha, "10:30 - 11:30")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Selecciona horas completas"));
        solicitar("a@gaja.cl", "Cancha A", fecha, "11:00 - 10:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El horario debe tener formato HH:mm - HH:mm"));
        solicitar("a@gaja.cl", "Cancha A", fecha, "mediodía")
                .andExpect(status().isBadRequest());
    }

    // ---------- Reglas editables ----------

    @Test
    void lasReglasInicialesSonSieteDiasYDosHoras() throws Exception {
        mvc.perform(get("/api/reservas/reglas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxDiasAdelante").value(7))
                .andExpect(jsonPath("$.horasLimiteCancelacion").value(2))
                .andExpect(jsonPath("$.horaApertura").value(8))
                .andExpect(jsonPath("$.horaCierre").value(23));
    }

    @Test
    void elAdministradorCambiaLaAnticipacionYSeAplicaAlTiro() throws Exception {
        enviar(put("/api/reservas/reglas"), "{\"maxDiasAdelante\":2,\"horasLimiteCancelacion\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxDiasAdelante").value(2));

        solicitar("a@gaja.cl", "Cancha A", hoy().plusDays(3), "10:00 - 11:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Solo puedes reservar hasta 2 días adelante"));
        solicitar("a@gaja.cl", "Cancha A", hoy().plusDays(2), "10:00 - 11:00")
                .andExpect(status().isCreated());
    }

    @Test
    void lasReglasFueraDeRangoSeRechazan() throws Exception {
        enviar(put("/api/reservas/reglas"), "{\"maxDiasAdelante\":400,\"horasLimiteCancelacion\":2}")
                .andExpect(status().isBadRequest());
        enviar(put("/api/reservas/reglas"), "{\"maxDiasAdelante\":7,\"horasLimiteCancelacion\":-1}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void elPlazoDeCancelacionConfiguradoSeRespeta() throws Exception {
        enviar(put("/api/reservas/reglas"), "{\"maxDiasAdelante\":7,\"horasLimiteCancelacion\":48}")
                .andExpect(status().isOk());
        // Una reserva para mañana queda a menos de 48 horas.
        long id = crear("cadete@gaja.cl", "Cancha A", hoy().plusDays(1), "10:00 - 11:00");

        enviar(patch("/api/reservas/" + id + "/cancelar").param("email", "cadete@gaja.cl"), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Debes cancelar con al menos 48 horas de anticipación"));
    }

    // ---------- Quitar de la lista ----------

    @Test
    void unaReservaCanceladaSePuedeQuitarDeLaListaDelUsuario() throws Exception {
        long id = crear("cadete@gaja.cl", "Cancha A", hoy().plusDays(3), "10:00 - 11:00");
        enviar(patch("/api/reservas/" + id + "/cancelar").param("email", "cadete@gaja.cl"), "")
                .andExpect(status().isOk());

        enviar(patch("/api/reservas/" + id + "/ocultar").param("email", "cadete@gaja.cl"), "")
                .andExpect(status().isOk());

        mvc.perform(get("/api/reservas/usuario").param("email", "cadete@gaja.cl"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void unaSolicitudPendienteNoSePuedeQuitarDeLaLista() throws Exception {
        long id = crear("cadete@gaja.cl", "Cancha A", hoy().plusDays(3), "10:00 - 11:00");

        enviar(patch("/api/reservas/" + id + "/ocultar").param("email", "cadete@gaja.cl"), "")
                .andExpect(status().isConflict());
    }

    @Test
    void faltanDatosObligatorios() throws Exception {
        enviar(post("/api/reservas"), "{\"usuarioEmail\":\"no-es-correo\"}")
                .andExpect(status().isBadRequest());
    }

    // ---------- Consultas ----------

    @Test
    void listaLasReservasDeUnUsuarioYFiltraPorEstado() throws Exception {
        LocalDate fecha = hoy().plusDays(2);
        long id = crear("uno@gaja.cl", "Cancha A", fecha, "10:00 - 11:00");
        crear("dos@gaja.cl", "Cancha A", fecha, "11:00 - 12:00");
        cambiarEstado(id, "APROBADA", null).andExpect(status().isOk());

        mvc.perform(get("/api/reservas/usuario").param("email", "uno@gaja.cl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].estado").value("APROBADA"));

        mvc.perform(get("/api/reservas").param("estado", "PENDIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].usuarioEmail").value("dos@gaja.cl"));
    }

    @Test
    void disponibilidadMuestraSoloLosHorariosActivosDeEsaCancha() throws Exception {
        LocalDate fecha = hoy().plusDays(2);
        crear("uno@gaja.cl", "Cancha A", fecha, "10:00 - 11:00");
        long rechazada = crear("dos@gaja.cl", "Cancha A", fecha, "11:00 - 12:00");
        crear("tres@gaja.cl", "Cancha B", fecha, "12:00 - 13:00");
        cambiarEstado(rechazada, "RECHAZADA", "Mantención").andExpect(status().isOk());

        mvc.perform(get("/api/reservas/disponibilidad")
                        .param("fecha", fecha.toString())
                        .param("espacio", "Cancha A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", contains("10:00 - 11:00")));
    }

    // ---------- Aprobar y rechazar ----------

    @Test
    void aprobarAvisaAlUsuario() throws Exception {
        long id = crear("cadete@gaja.cl", "Cancha A", hoy().plusDays(2), "10:00 - 11:00");

        cambiarEstado(id, "APROBADA", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"));

        mvc.perform(get("/api/alertas/no-leidas").param("email", "cadete@gaja.cl"))
                .andExpect(jsonPath("$.cantidad").value(2));
        mvc.perform(get("/api/alertas").param("email", "cadete@gaja.cl"))
                .andExpect(jsonPath("$[*].mensaje", hasItem(containsString("fue aprobada"))));
    }

    @Test
    void rechazarExigeMotivoYLoGuarda() throws Exception {
        long id = crear("cadete@gaja.cl", "Cancha A", hoy().plusDays(2), "10:00 - 11:00");

        cambiarEstado(id, "RECHAZADA", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Indica el motivo del rechazo"));
        cambiarEstado(id, "RECHAZADA", "   ")
                .andExpect(status().isBadRequest());

        cambiarEstado(id, "RECHAZADA", "Cancha en mantención")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.motivoRechazo").value("Cancha en mantención"));
    }

    @Test
    void soloSePuedeResolverUnaSolicitudPendiente() throws Exception {
        long id = crear("cadete@gaja.cl", "Cancha A", hoy().plusDays(2), "10:00 - 11:00");
        cambiarEstado(id, "APROBADA", null).andExpect(status().isOk());

        cambiarEstado(id, "RECHAZADA", "Ya no")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Solo puedes cambiar solicitudes pendientes"));
    }

    @Test
    void elNuevoEstadoSoloPuedeSerAprobadaORechazada() throws Exception {
        long id = crear("cadete@gaja.cl", "Cancha A", hoy().plusDays(2), "10:00 - 11:00");

        cambiarEstado(id, "CANCELADA", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El estado debe ser APROBADA o RECHAZADA"));
    }

    @Test
    void cambiarEstadoDeUnaReservaQueNoExisteDa404() throws Exception {
        cambiarEstado(999999, "APROBADA", null).andExpect(status().isNotFound());
    }

    // ---------- Cancelar ----------

    @Test
    void elDuenoPuedeCancelarConAnticipacion() throws Exception {
        long id = crear("cadete@gaja.cl", "Cancha A", hoy().plusDays(3), "10:00 - 11:00");

        enviar(patch("/api/reservas/" + id + "/cancelar").param("email", "cadete@gaja.cl"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));

        mvc.perform(get("/api/alertas").param("email", "cadete@gaja.cl"))
                .andExpect(jsonPath("$[*].mensaje", hasItem(containsString("fue cancelada"))));
    }

    @Test
    void otroUsuarioNoPuedeCancelarUnaReservaAjena() throws Exception {
        long id = crear("cadete@gaja.cl", "Cancha A", hoy().plusDays(3), "10:00 - 11:00");

        enviar(patch("/api/reservas/" + id + "/cancelar").param("email", "otro@gaja.cl"), "")
                .andExpect(status().isNotFound());
    }

    @Test
    void noSePuedeCancelarFaltandoMenosDeDosHoras() throws Exception {
        // Una reserva de ayer ya pasó el límite de 2 horas, sin depender de la hora en que corra la prueba.
        Reserva ayer = new Reserva();
        ayer.setUsuarioEmail("cadete@gaja.cl");
        ayer.setEspacio("Cancha A");
        ayer.setFecha(hoy().minusDays(1));
        ayer.setHorario("10:00 - 11:00");
        ayer.setMotivo("Partido");
        ayer.setEstado(EstadoReserva.APROBADA);
        Reserva guardada = reservaRepository.save(ayer);

        enviar(patch("/api/reservas/" + guardada.getId() + "/cancelar").param("email", "cadete@gaja.cl"), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Debes cancelar con al menos 2 horas de anticipación"));
    }

    @Test
    void unaReservaYaCanceladaNoSeCancelaDeNuevo() throws Exception {
        long id = crear("cadete@gaja.cl", "Cancha A", hoy().plusDays(3), "10:00 - 11:00");
        enviar(patch("/api/reservas/" + id + "/cancelar").param("email", "cadete@gaja.cl"), "")
                .andExpect(status().isOk());

        enviar(patch("/api/reservas/" + id + "/cancelar").param("email", "cadete@gaja.cl"), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Solo puedes cancelar reservas pendientes o aprobadas"));
    }

    // ---------- Alertas ----------

    @Test
    void marcarUnaAlertaComoLeidaBajaElContador() throws Exception {
        crear("cadete@gaja.cl", "Cancha A", hoy().plusDays(2), "10:00 - 11:00");
        String body = mvc.perform(get("/api/alertas").param("email", "cadete@gaja.cl"))
                .andReturn().getResponse().getContentAsString();
        long alertaId = ((Number) JsonPath.read(body, "$[0].id")).longValue();

        mvc.perform(get("/api/alertas/no-leidas").param("email", "cadete@gaja.cl"))
                .andExpect(jsonPath("$.cantidad").value(1));

        enviar(patch("/api/alertas/" + alertaId + "/leida").param("email", "cadete@gaja.cl"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leida").value(true));

        mvc.perform(get("/api/alertas/no-leidas").param("email", "cadete@gaja.cl"))
                .andExpect(jsonPath("$.cantidad").value(0));
    }

    @Test
    void noSePuedeMarcarComoLeidaLaAlertaDeOtroUsuario() throws Exception {
        crear("cadete@gaja.cl", "Cancha A", hoy().plusDays(2), "10:00 - 11:00");
        String body = mvc.perform(get("/api/alertas").param("email", "cadete@gaja.cl"))
                .andReturn().getResponse().getContentAsString();
        long alertaId = ((Number) JsonPath.read(body, "$[0].id")).longValue();

        enviar(patch("/api/alertas/" + alertaId + "/leida").param("email", "otro@gaja.cl"), "")
                .andExpect(status().isNotFound());
    }

    // ---------- Incidencias ----------

    private long crearIncidencia(String email, String espacio, String descripcion) throws Exception {
        String body = enviar(post("/api/incidencias"),
                "{\"usuarioEmail\":\"" + email + "\",\"espacio\":\"" + espacio
                        + "\",\"descripcion\":\"" + descripcion + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void unaIncidenciaSeCreaPendienteYSeResuelve() throws Exception {
        long id = crearIncidencia("cadete@gaja.cl", "Cancha A", "El arco está suelto");

        mvc.perform(get("/api/incidencias/pendientes/count"))
                .andExpect(jsonPath("$.cantidad").value(1));

        mvc.perform(patch("/api/incidencias/" + id + "/resolver"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RESUELTA"));

        mvc.perform(get("/api/incidencias/pendientes/count"))
                .andExpect(jsonPath("$.cantidad").value(0));
    }

    @Test
    void elAdministradorVeTodasYElUsuarioSoloLasSuyas() throws Exception {
        crearIncidencia("uno@gaja.cl", "Cancha A", "Problema uno");
        crearIncidencia("dos@gaja.cl", "Cancha B", "Problema dos");

        mvc.perform(get("/api/incidencias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mvc.perform(get("/api/incidencias/usuario").param("email", "uno@gaja.cl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].descripcion").value("Problema uno"));
    }

    @Test
    void resolverUnaIncidenciaQueNoExisteDa404() throws Exception {
        mvc.perform(patch("/api/incidencias/999999/resolver")).andExpect(status().isNotFound());
    }

    @Test
    void unaIncidenciaSinDescripcionSeRechaza() throws Exception {
        enviar(post("/api/incidencias"),
                "{\"usuarioEmail\":\"cadete@gaja.cl\",\"espacio\":\"Cancha A\",\"descripcion\":\"\"}")
                .andExpect(status().isBadRequest());
    }
}

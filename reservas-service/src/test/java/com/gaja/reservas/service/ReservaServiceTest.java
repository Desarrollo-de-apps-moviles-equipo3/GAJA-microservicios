package com.gaja.reservas.service;

import com.gaja.reservas.dto.CrearReservaRequest;
import com.gaja.reservas.model.EstadoReserva;
import com.gaja.reservas.model.ReglasReserva;
import com.gaja.reservas.model.Reserva;
import com.gaja.reservas.repo.ReglasReservaRepository;
import com.gaja.reservas.repo.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private ReglasReservaRepository reglasRepository;

    @Mock
    private AlertaService alertaService;

    @InjectMocks
    private ReservaService service;

    @BeforeEach
    void configurarReglasPredeterminadas() {
        lenient().when(reglasRepository.findById(1L)).thenReturn(Optional.of(new ReglasReserva()));
        lenient().when(reservaRepository.save(any(Reserva.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void crearReserva_aceptaRangoCompletoYMasDeTresSolicitudesEnElDia() {
        LocalDate fecha = hoy().plusDays(3);
        when(reservaRepository.findByFechaAndEspacioAndEstadoInOrderByHorarioAsc(
                eq(fecha), eq("Cancha A"), anyList()))
                .thenReturn(List.of(
                        reserva("08:00 - 09:00", EstadoReserva.PENDIENTE),
                        reserva("09:00 - 10:00", EstadoReserva.APROBADA),
                        reserva("10:00 - 11:00", EstadoReserva.PENDIENTE)
                ));

        Reserva result = service.crearReserva(request(fecha, "11:00 - 13:00"));

        assertEquals("11:00 - 13:00", result.getHorario());
        assertEquals(EstadoReserva.PENDIENTE, result.getEstado());
        verify(reservaRepository, never()).countByFechaAndEstadoIn(any(), anyList());
        verify(alertaService).crearAlerta(
                eq("gabriel@gaja.cl"), isNull(), contains("pendiente de aprobación")
        );
    }

    @Test
    void crearReserva_rechazaFechaPasada() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.crearReserva(request(hoy().minusDays(1), "10:00 - 11:00"))
        );

        assertEquals("No puedes reservar una fecha pasada", error.getMessage());
        verify(reservaRepository, never()).save(any(Reserva.class));
    }

    @Test
    void crearReserva_rechazaFechaQueSuperaLosDiasConfigurados() {
        ReglasReserva rules = new ReglasReserva();
        rules.setMaxDiasAdelante(2);
        when(reglasRepository.findById(1L)).thenReturn(Optional.of(rules));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.crearReserva(request(hoy().plusDays(3), "10:00 - 11:00"))
        );

        assertEquals("Solo puedes reservar hasta 2 días adelante", error.getMessage());
        verify(reservaRepository, never()).save(any(Reserva.class));
    }

    @Test
    void crearReserva_rechazaHorasFueraDeLaJornada() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.crearReserva(request(hoy().plusDays(1), "07:00 - 09:00"))
        );

        assertEquals("El horario debe estar entre 08:00 y 23:00", error.getMessage());
        verify(reservaRepository, never()).save(any(Reserva.class));
    }

    @Test
    void crearReserva_rechazaHorasQueNoSonCompletas() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.crearReserva(request(hoy().plusDays(1), "08:30 - 10:00"))
        );

        assertEquals("Selecciona horas completas", error.getMessage());
        verify(reservaRepository, never()).save(any(Reserva.class));
    }

    @Test
    void crearReserva_rechazaRangoQueSeCruzaConUnaReservaActiva() {
        LocalDate fecha = hoy().plusDays(1);
        when(reservaRepository.findByFechaAndEspacioAndEstadoInOrderByHorarioAsc(
                eq(fecha), eq("Cancha A"), anyList()))
                .thenReturn(List.of(reserva("09:00 - 12:00", EstadoReserva.APROBADA)));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.crearReserva(request(fecha, "11:00 - 13:00"))
        );

        assertEquals("Ese horario ya está ocupado", error.getMessage());
        verify(reservaRepository, never()).save(any(Reserva.class));
    }

    @Test
    void crearReserva_permiteRangosConsecutivosSinTraslape() {
        LocalDate fecha = hoy().plusDays(1);
        when(reservaRepository.findByFechaAndEspacioAndEstadoInOrderByHorarioAsc(
                eq(fecha), eq("Cancha A"), anyList()))
                .thenReturn(List.of(reserva("09:00 - 12:00", EstadoReserva.PENDIENTE)));

        Reserva result = service.crearReserva(request(fecha, "12:00 - 14:00"));

        assertEquals("12:00 - 14:00", result.getHorario());
        verify(reservaRepository).save(any(Reserva.class));
    }

    @Test
    void cancelarReserva_usaElPlazoConfigurado() {
        ReglasReserva rules = new ReglasReserva();
        rules.setHorasLimiteCancelacion(2);
        when(reglasRepository.findById(1L)).thenReturn(Optional.of(rules));
        LocalDateTime inicio = LocalDateTime.now(ZoneId.of("America/Santiago")).plusHours(1);
        Reserva reservation = reserva(
                inicio.toLocalDate(), inicio.toLocalTime().withMinute(0).withSecond(0).withNano(0),
                "Cancha A", EstadoReserva.APROBADA
        );
        when(reservaRepository.findByIdAndUsuarioEmail(15L, "gabriel@gaja.cl"))
                .thenReturn(Optional.of(reservation));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.cancelarReserva(15L, "gabriel@gaja.cl")
        );

        assertEquals("Debes cancelar con al menos 2 horas de anticipación", error.getMessage());
        verify(reservaRepository, never()).save(any(Reserva.class));
    }

    @Test
    void ocultarParaUsuario_ocultaUnaReservaCanceladaDeSuPropietario() {
        Reserva reservation = reserva("10:00 - 11:00", EstadoReserva.CANCELADA);
        when(reservaRepository.findByIdAndUsuarioEmail(4L, "gabriel@gaja.cl"))
                .thenReturn(Optional.of(reservation));

        Reserva result = service.ocultarParaUsuario(4L, " gabriel@gaja.cl ");

        assertTrue(result.isOcultaParaUsuario());
        verify(reservaRepository).save(reservation);
    }

    @Test
    void ocultarParaUsuario_noPermiteOcultarSolicitudPendiente() {
        Reserva reservation = reserva("10:00 - 11:00", EstadoReserva.PENDIENTE);
        when(reservaRepository.findByIdAndUsuarioEmail(5L, "gabriel@gaja.cl"))
                .thenReturn(Optional.of(reservation));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.ocultarParaUsuario(5L, "gabriel@gaja.cl")
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertFalse(reservation.isOcultaParaUsuario());
        verify(reservaRepository, never()).save(any(Reserva.class));
    }

    @Test
    void actualizarEstado_exigeMotivoParaRechazar() {
        Reserva reservation = reserva("10:00 - 11:00", EstadoReserva.PENDIENTE);
        when(reservaRepository.findById(6L)).thenReturn(Optional.of(reservation));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.actualizarEstado(6L, EstadoReserva.RECHAZADA, "  ")
        );

        assertEquals("Indica el motivo del rechazo", error.getMessage());
        verify(reservaRepository, never()).save(any(Reserva.class));
    }

    @Test
    void actualizarReglas_persisteYDevuelveLosValoresRecibidos() {
        when(reglasRepository.save(any(ReglasReserva.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.actualizarReglas(
                new com.gaja.reservas.dto.ActualizarReglasReservaRequest(14, 4)
        );

        assertEquals(14, result.maxDiasAdelante());
        assertEquals(4, result.horasLimiteCancelacion());
        assertEquals(8, result.horaApertura());
        assertEquals(23, result.horaCierre());
    }

    private CrearReservaRequest request(LocalDate fecha, String horario) {
        CrearReservaRequest request = new CrearReservaRequest();
        request.setUsuarioEmail(" gabriel@gaja.cl ");
        request.setEspacio(" Cancha A ");
        request.setFecha(fecha);
        request.setHorario(" " + horario + " ");
        request.setMotivo(" Entrenamiento de equipo ");
        return request;
    }

    private Reserva reserva(String horario, EstadoReserva estado) {
        return reserva(hoy().plusDays(1), LocalTime.parse(horario.substring(0, 5)),
                "Cancha A", estado, horario);
    }

    private Reserva reserva(LocalDate fecha, LocalTime inicio, String espacio, EstadoReserva estado) {
        return reserva(fecha, inicio, espacio, estado,
                inicio + " - " + inicio.plusHours(1));
    }

    private Reserva reserva(LocalDate fecha, LocalTime inicio, String espacio, EstadoReserva estado, String horario) {
        Reserva reservation = new Reserva();
        reservation.setFecha(fecha);
        reservation.setEspacio(espacio);
        reservation.setHorario(horario);
        reservation.setUsuarioEmail("gabriel@gaja.cl");
        reservation.setMotivo("Entrenamiento");
        reservation.setEstado(estado);
        return reservation;
    }

    private LocalDate hoy() {
        return LocalDate.now(ZoneId.of("America/Santiago"));
    }
}

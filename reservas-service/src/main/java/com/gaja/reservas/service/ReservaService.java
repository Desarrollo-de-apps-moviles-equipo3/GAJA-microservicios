package com.gaja.reservas.service;


import com.gaja.reservas.dto.CrearReservaRequest;
import com.gaja.reservas.model.EstadoReserva;
import com.gaja.reservas.model.Reserva;
import com.gaja.reservas.repo.ReservaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class ReservaService {

    private static final int MAX_DIAS_ADELANTE = 7;
    private static final int MAX_RESERVAS_POR_DIA = 3;

    private static final List<EstadoReserva> ESTADOS_ACTIVOS =
            List.of(EstadoReserva.PENDIENTE, EstadoReserva.APROBADA);

    private final ReservaRepository reservaRepository;

    private final AlertaService alertaService;
        
    public ReservaService(
        ReservaRepository reservaRepository,
        AlertaService alertaService
        ) {
                this.reservaRepository = reservaRepository;
                this.alertaService = alertaService;
        }

    @Transactional
    public Reserva crearReserva(CrearReservaRequest request) {
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Santiago"));
        LocalDate ultimaFechaPermitida = hoy.plusDays(MAX_DIAS_ADELANTE);

        if (request.getFecha().isBefore(hoy)) {
            throw new IllegalArgumentException("No puedes reservar una fecha pasada");
        }

        if (request.getFecha().isAfter(ultimaFechaPermitida)) {
            throw new IllegalArgumentException(
                    "Solo puedes reservar hasta " + MAX_DIAS_ADELANTE + " días adelante"
            );
        }

        long reservasDelDia = reservaRepository.countByFechaAndEstadoIn(
                request.getFecha(),
                ESTADOS_ACTIVOS
        );

        if (reservasDelDia >= MAX_RESERVAS_POR_DIA) {
            throw new IllegalStateException(
                    "Ese día ya alcanzó el máximo de reservas"
            );

        
        }

        boolean horarioOcupado =
                reservaRepository.existsByFechaAndEspacioAndHorarioAndEstadoIn(
                        request.getFecha(),
                        request.getEspacio(),
                        request.getHorario(),
                        ESTADOS_ACTIVOS
                );

        if (horarioOcupado) {
            throw new IllegalStateException("Ese horario ya está ocupado");
        }

        Reserva reserva = new Reserva();
        reserva.setUsuarioEmail(request.getUsuarioEmail().trim());
        reserva.setEspacio(request.getEspacio().trim());
        reserva.setFecha(request.getFecha());
        reserva.setHorario(request.getHorario().trim());
        reserva.setMotivo(request.getMotivo().trim());
        reserva.setEstado(EstadoReserva.PENDIENTE);


        Reserva guardada = reservaRepository.save(reserva);

        alertaService.crearAlerta(
                guardada.getUsuarioEmail(),
                guardada.getId(),
        "Recibimos tu solicitud para " + guardada.getEspacio() + ". Está pendiente de aprobación."
        );

        return guardada;
        
    }

    @Transactional(readOnly = true)
    public List<Reserva> listarPorUsuario(String usuarioEmail) {
        return reservaRepository.findByUsuarioEmailOrderByFechaAsc(
                usuarioEmail.trim()
        );
    }

    @Transactional(readOnly = true)
    public List<Reserva> listarPorFecha(LocalDate fecha) {
        return reservaRepository.findByFechaOrderByHorarioAsc(fecha);
    }

    @Transactional(readOnly = true)
    public List<String> listarHorariosOcupados(LocalDate fecha, String espacio) {
        return reservaRepository
                .findByFechaAndEspacioAndEstadoInOrderByHorarioAsc(
                        fecha,
                        espacio,
                        ESTADOS_ACTIVOS
                )
                .stream()
                .map(Reserva::getHorario)
                .distinct()
                .toList();
    }

    @Transactional
    public Reserva cancelarReserva(Long id, String usuarioEmail) {
        Reserva reserva = reservaRepository
            .findByIdAndUsuarioEmail(id, usuarioEmail.trim())
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No encontramos esa reserva"
            ));

    if (!ESTADOS_ACTIVOS.contains(reserva.getEstado())) {
        throw new IllegalStateException(
                "Solo puedes cancelar reservas pendientes o aprobadas"
        );
    }

    String horaInicioTexto = reserva.getHorario()
            .split("-")[0]
            .trim();

    LocalTime horaInicio = LocalTime.parse(horaInicioTexto);
    LocalDateTime inicioReserva = LocalDateTime.of(
            reserva.getFecha(),
            horaInicio
    );

    LocalDateTime limiteCancelacion = inicioReserva.minusHours(2);
    LocalDateTime ahora = LocalDateTime.now(
            ZoneId.of("America/Santiago")
    );

    if (ahora.isAfter(limiteCancelacion)) {
        throw new IllegalStateException(
                "Puedes cancelar hasta dos horas antes del inicio"
        );
    }

        reserva.setEstado(EstadoReserva.CANCELADA);

        Reserva guardada = reservaRepository.save(reserva);

        alertaService.crearAlerta(
        guardada.getUsuarioEmail(),
        guardada.getId(),
        "Tu reserva para " + guardada.getEspacio() + " fue cancelada."
    );

        return guardada;



    }

    @Transactional
    public Reserva actualizarEstado(Long id, EstadoReserva nuevoEstado) {
    Reserva reserva = reservaRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No encontramos esa reserva"
            ));

    if (reserva.getEstado() != EstadoReserva.PENDIENTE) {
        throw new IllegalStateException(
                "Solo puedes cambiar solicitudes pendientes"
        );
    }

    if (nuevoEstado != EstadoReserva.APROBADA
            && nuevoEstado != EstadoReserva.RECHAZADA) {
        throw new IllegalArgumentException(
                "El estado debe ser APROBADA o RECHAZADA"
        );
    }

    reserva.setEstado(nuevoEstado);
        

        Reserva guardada = reservaRepository.save(reserva);

        String mensaje = nuevoEstado == EstadoReserva.APROBADA
                ? "Tu reserva para " + guardada.getEspacio() + " fue aprobada."
                : "Tu reserva para " + guardada.getEspacio() + " fue rechazada.";

                alertaService.crearAlerta(
                        guardada.getUsuarioEmail(),
                        guardada.getId(),
                        mensaje
        );

        return guardada;



    }

    
    



}
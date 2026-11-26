package com.gaja.reservas.service;


import com.gaja.reservas.dto.CrearReservaRequest;
import com.gaja.reservas.dto.ReglasReservaResponse;
import com.gaja.reservas.dto.ActualizarReglasReservaRequest;
import com.gaja.reservas.model.EstadoReserva;
import com.gaja.reservas.model.Reserva;
import com.gaja.reservas.model.ReglasReserva;
import com.gaja.reservas.repo.ReservaRepository;
import com.gaja.reservas.repo.ReglasReservaRepository;
import org.springframework.data.domain.Sort;
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

    private static final int HORA_APERTURA = 8;
    private static final int HORA_CIERRE = 23;

    private static final List<EstadoReserva> ESTADOS_ACTIVOS =
            List.of(EstadoReserva.PENDIENTE, EstadoReserva.APROBADA);

    private final ReservaRepository reservaRepository;
    private final ReglasReservaRepository reglasRepository;

    private final AlertaService alertaService;

    @Transactional(readOnly = true)
    public ReglasReservaResponse obtenerReglas() {
        return respuesta(reglasActuales());
    }

    @Transactional
    public ReglasReservaResponse actualizarReglas(ActualizarReglasReservaRequest request) {
        ReglasReserva reglas = reglasRepository.findById(1L).orElseGet(ReglasReserva::new);
        reglas.setMaxDiasAdelante(request.maxDiasAdelante());
        reglas.setHorasLimiteCancelacion(request.horasLimiteCancelacion());
        return respuesta(reglasRepository.save(reglas));
    }
        
    public ReservaService(
        ReservaRepository reservaRepository,
        ReglasReservaRepository reglasRepository,
        AlertaService alertaService
        ) {
                this.reservaRepository = reservaRepository;
                this.reglasRepository = reglasRepository;
                this.alertaService = alertaService;
        }

    @Transactional
    public Reserva crearReserva(CrearReservaRequest request) {
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Santiago"));
        ReglasReserva reglas = reglasActuales();
        LocalDate ultimaFechaPermitida = hoy.plusDays(reglas.getMaxDiasAdelante());

        if (request.getFecha().isBefore(hoy)) {
            throw new IllegalArgumentException("No puedes reservar una fecha pasada");
        }

        if (request.getFecha().isAfter(ultimaFechaPermitida)) {
            throw new IllegalArgumentException(
                    "Solo puedes reservar hasta " + reglas.getMaxDiasAdelante() + " días adelante"
            );
        }

        Intervalo solicitado = parsearHorario(request.getHorario());
        if (solicitado.inicio().isBefore(LocalTime.of(HORA_APERTURA, 0))
                || solicitado.fin().isAfter(LocalTime.of(HORA_CIERRE, 0))) {
            throw new IllegalArgumentException("El horario debe estar entre 08:00 y 23:00");
        }
        if (solicitado.inicio().getMinute() != 0 || solicitado.inicio().getSecond() != 0
                || solicitado.fin().getMinute() != 0 || solicitado.fin().getSecond() != 0) {
            throw new IllegalArgumentException("Selecciona horas completas");
        }

        boolean horarioOcupado = reservaRepository
                .findByFechaAndEspacioAndEstadoInOrderByHorarioAsc(
                        request.getFecha(), request.getEspacio().trim(), ESTADOS_ACTIVOS)
                .stream()
                .map(reserva -> parsearHorario(reserva.getHorario()))
                .anyMatch(solicitado::seCruza);

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
    public List<Reserva> listar(EstadoReserva estado) {
        if (estado == null) {
            return reservaRepository.findAll(Sort.by("fecha", "horario"));
        }
        return reservaRepository.findByEstadoOrderByFechaAscHorarioAsc(estado);
    }

    @Transactional(readOnly = true)
    public List<Reserva> listarPorUsuario(String usuarioEmail) {
        return reservaRepository.findByUsuarioEmailAndOcultaParaUsuarioFalseOrderByCreadaEnDesc(
                usuarioEmail.trim()
        );
    }

    @Transactional
    public Reserva ocultarParaUsuario(Long id, String usuarioEmail) {
        Reserva reserva = reservaRepository.findByIdAndUsuarioEmail(id, usuarioEmail.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No encontramos esa reserva"
                ));

        if (!puedeOcultarseParaUsuario(reserva)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Solo puedes quitar de tu lista reservas canceladas, rechazadas o ya finalizadas"
            );
        }

        reserva.setOcultaParaUsuario(true);
        return reservaRepository.save(reserva);
    }

    private boolean puedeOcultarseParaUsuario(Reserva reserva) {
        if (reserva.getEstado() == EstadoReserva.CANCELADA
                || reserva.getEstado() == EstadoReserva.RECHAZADA) {
            return true;
        }
        if (reserva.getEstado() != EstadoReserva.APROBADA) {
            return false;
        }

        String horaFinTexto = reserva.getHorario().split("-")[1].trim();
        LocalDateTime finReserva = LocalDateTime.of(
                reserva.getFecha(),
                LocalTime.parse(horaFinTexto)
        );
        return !LocalDateTime.now(ZoneId.of("America/Santiago")).isBefore(finReserva);
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

    private ReglasReserva reglasActuales() {
        return reglasRepository.findById(1L).orElseGet(ReglasReserva::new);
    }

    private ReglasReservaResponse respuesta(ReglasReserva reglas) {
        return new ReglasReservaResponse(reglas.getMaxDiasAdelante(),
                reglas.getHorasLimiteCancelacion(),
                HORA_APERTURA, HORA_CIERRE);
    }

    private Intervalo parsearHorario(String texto) {
        try {
            String[] partes = texto.split("-");
            if (partes.length != 2) throw new IllegalArgumentException();
            LocalTime inicio = LocalTime.parse(partes[0].trim());
            LocalTime fin = LocalTime.parse(partes[1].trim());
            if (!inicio.isBefore(fin)) throw new IllegalArgumentException();
            return new Intervalo(inicio, fin);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("El horario debe tener formato HH:mm - HH:mm");
        }
    }

    private record Intervalo(LocalTime inicio, LocalTime fin) {
        boolean seCruza(Intervalo otro) {
            return inicio.isBefore(otro.fin()) && otro.inicio().isBefore(fin);
        }
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

    int horasLimiteCancelacion = reglasActuales().getHorasLimiteCancelacion();
    LocalDateTime limiteCancelacion = inicioReserva.minusHours(horasLimiteCancelacion);
    LocalDateTime ahora = LocalDateTime.now(
            ZoneId.of("America/Santiago")
    );

    if (ahora.isAfter(limiteCancelacion)) {
        throw new IllegalStateException(
                "Debes cancelar con al menos " + horasLimiteCancelacion + " horas de anticipación"
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
    public Reserva actualizarEstado(Long id, EstadoReserva nuevoEstado, String motivoRechazo) {
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

    if (nuevoEstado == EstadoReserva.RECHAZADA) {
        if (motivoRechazo == null || motivoRechazo.isBlank()) {
            throw new IllegalArgumentException("Indica el motivo del rechazo");
        }
        reserva.setMotivoRechazo(motivoRechazo.trim());
    }

    reserva.setEstado(nuevoEstado);
        

        Reserva guardada = reservaRepository.save(reserva);

        String mensaje = nuevoEstado == EstadoReserva.APROBADA
                ? "Tu reserva para " + guardada.getEspacio() + " fue aprobada."
                : "Tu reserva para " + guardada.getEspacio()
                    + " fue rechazada. Motivo: " + guardada.getMotivoRechazo();

                alertaService.crearAlerta(
                        guardada.getUsuarioEmail(),
                        guardada.getId(),
                        mensaje
        );

        return guardada;



    }

    
    



}

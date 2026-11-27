package com.gaja.reservas.repo;

import com.gaja.reservas.model.EstadoReserva;
import com.gaja.reservas.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReservaRepository  extends JpaRepository<Reserva, Long> {

    List<Reserva> findByUsuarioEmailOrderByFechaAsc(String usuarioEmail);

    List<Reserva> findByUsuarioEmailAndOcultaParaUsuarioFalseOrderByCreadaEnDesc(String usuarioEmail);

    List<Reserva> findByEstadoOrderByFechaAscHorarioAsc(EstadoReserva estado);

    List<Reserva> findByFechaOrderByHorarioAsc(LocalDate fecha);

    List<Reserva> findByFechaAndEspacioAndEstadoInOrderByHorarioAsc(
        LocalDate fecha,
        String espacio,
        List<EstadoReserva> estados
    );

    Optional<Reserva> findByIdAndUsuarioEmail(Long id, String usuarioEmail);

    long countByFechaAndEstadoIn(
            LocalDate fecha,
            List<EstadoReserva> estados
    );

    boolean existsByFechaAndEspacioAndHorarioAndEstadoIn(
            LocalDate fecha,
            String espacio,
            String horario,
            List<EstadoReserva> estados
    );

    

}


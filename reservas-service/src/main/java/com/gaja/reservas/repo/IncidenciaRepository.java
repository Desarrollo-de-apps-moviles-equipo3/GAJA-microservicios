package com.gaja.reservas.repo;

import com.gaja.reservas.model.EstadoIncidencia;
import com.gaja.reservas.model.Incidencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidenciaRepository
        extends JpaRepository<Incidencia, Long> {

    List<Incidencia> findByUsuarioEmailOrderByCreadaEnDesc(
            String usuarioEmail
    );

    List<Incidencia> findAllByOrderByCreadaEnDesc();

    long countByEstado(EstadoIncidencia estado);
}
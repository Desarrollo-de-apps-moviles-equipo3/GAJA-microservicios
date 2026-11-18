package com.gaja.espacios.repo;

import com.gaja.espacios.model.Bloqueo;
import com.gaja.espacios.model.Espacio;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BloqueoRepository extends JpaRepository<Bloqueo, Long> {
    List<Bloqueo> findByFecha(LocalDate fecha);
    List<Bloqueo> findByEspacio(Espacio espacio);
    List<Bloqueo> findByEspacioAndFecha(Espacio espacio, LocalDate fecha);
    boolean existsByEspacioAndFechaAndFranja(Espacio espacio, LocalDate fecha, String franja);
    void deleteByEspacio(Espacio espacio);
}

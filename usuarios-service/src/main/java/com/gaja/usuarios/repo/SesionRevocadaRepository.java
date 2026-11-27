package com.gaja.usuarios.repo;

import com.gaja.usuarios.model.SesionRevocada;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SesionRevocadaRepository extends JpaRepository<SesionRevocada, String> {
}

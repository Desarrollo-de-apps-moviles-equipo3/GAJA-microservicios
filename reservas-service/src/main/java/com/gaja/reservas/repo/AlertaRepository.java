package com.gaja.reservas.repo;

import com.gaja.reservas.model.Alerta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    List<Alerta> findByUsuarioEmailOrderByCreadaEnDesc(String usuarioEmail);

    Optional<Alerta> findByIdAndUsuarioEmail(Long id, String usuarioEmail);

    long countByUsuarioEmailAndLeidaFalse(String usuarioEmail);

    
}
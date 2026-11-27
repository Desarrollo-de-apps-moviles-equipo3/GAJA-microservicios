package com.gaja.usuarios.repo;

import com.gaja.usuarios.model.TokenRecuperacion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenRecuperacionRepository extends JpaRepository<TokenRecuperacion, Long> {
    List<TokenRecuperacion> findByUsuarioIdAndUsadoFalse(Long usuarioId);
    Optional<TokenRecuperacion> findFirstByUsuarioIdAndUsadoFalseOrderByCreadoEnDesc(Long usuarioId);
    long countByUsuarioIdAndCreadoEnAfter(Long usuarioId, Instant desde);
    void deleteByUsuarioId(Long usuarioId);
}

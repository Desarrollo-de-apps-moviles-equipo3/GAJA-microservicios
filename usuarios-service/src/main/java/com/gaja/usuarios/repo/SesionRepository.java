package com.gaja.usuarios.repo;

import com.gaja.usuarios.model.Sesion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SesionRepository extends JpaRepository<Sesion, Long> {
    List<Sesion> findByUsuarioIdOrderByFechaDesc(Long usuarioId);
    void deleteByUsuarioId(Long usuarioId);
}

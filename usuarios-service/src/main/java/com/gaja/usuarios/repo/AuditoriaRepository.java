package com.gaja.usuarios.repo;

import com.gaja.usuarios.model.Auditoria;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {
    List<Auditoria> findAllByOrderByFechaDescIdDesc(Pageable pagina);
    List<Auditoria> findByAccionOrderByFechaDescIdDesc(String accion, Pageable pagina);
}

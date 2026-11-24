package com.gaja.usuarios.repo;

import com.gaja.usuarios.model.EstadoUsuario;
import com.gaja.usuarios.model.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<Usuario> findAllByOrderByNombreAsc();
    List<Usuario> findByEstadoOrderByNombreAsc(EstadoUsuario estado);
    List<Usuario> findByRolOrderByNombreAsc(String rol);
    List<Usuario> findByEstadoAndRolOrderByNombreAsc(EstadoUsuario estado, String rol);
}

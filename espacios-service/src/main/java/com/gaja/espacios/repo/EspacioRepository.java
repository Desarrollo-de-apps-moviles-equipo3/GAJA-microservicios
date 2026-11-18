package com.gaja.espacios.repo;

import com.gaja.espacios.model.Categoria;
import com.gaja.espacios.model.Espacio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EspacioRepository extends JpaRepository<Espacio, String> {
    List<Espacio> findByCategoria(Categoria categoria);
    List<Espacio> findByDisponible(boolean disponible);
    List<Espacio> findByCategoriaAndDisponible(Categoria categoria, boolean disponible);
    boolean existsByCategoria(Categoria categoria);
}

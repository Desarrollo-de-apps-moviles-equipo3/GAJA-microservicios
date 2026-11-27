package com.gaja.usuarios.repo;

import com.gaja.usuarios.model.IntentoLogin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntentoLoginRepository extends JpaRepository<IntentoLogin, String> {
}

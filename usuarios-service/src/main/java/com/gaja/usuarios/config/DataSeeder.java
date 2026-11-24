package com.gaja.usuarios.config;

import com.gaja.usuarios.model.EstadoUsuario;
import com.gaja.usuarios.model.Usuario;
import com.gaja.usuarios.repo.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Carga los usuarios de prueba la primera vez (si la tabla esta vacia).
 * Los 5 primeros son los mismos que antes vivian dentro de la app; los otros 4 dejan datos
 * para las pantallas de perfiles pendientes, rechazados y desactivados. Contrasena de todos: 12345.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final String CLAVE = "12345";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public DataSeeder(UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (usuarios.count() > 0) return;

        crear("admin@gaja.cl", "Administrador GAJA", "Administrador", EstadoUsuario.APROBADO, null);
        crear("cadete@gaja.cl", "Cadete de Prueba", "Cadete", EstadoUsuario.APROBADO, null);
        crear("oficial@gaja.cl", "Oficial de Prueba", "Oficial", EstadoUsuario.APROBADO, null);
        crear("oficial.semana@gaja.cl", "Oficial de Semana", "Oficial de semana", EstadoUsuario.APROBADO, null);
        crear("externo@gaja.cl", "Club Deportivo Externo", "Institución externa", EstadoUsuario.APROBADO, null);

        crear("cadete.nuevo@gaja.cl", "Cadete Nuevo", "Cadete", EstadoUsuario.PENDIENTE, null);
        crear("externo.nuevo@gaja.cl", "Liceo Vecino", "Institución externa", EstadoUsuario.PENDIENTE, null);
        crear("cadete.rechazado@gaja.cl", "Cadete Rechazado", "Cadete", EstadoUsuario.RECHAZADO,
                "Los datos no coinciden con la nómina");
        crear("oficial.baja@gaja.cl", "Oficial en Retiro", "Oficial", EstadoUsuario.DESACTIVADO, null);
    }

    private void crear(String email, String nombre, String rol, EstadoUsuario estado, String motivo) {
        Usuario u = new Usuario(email, encoder.encode(CLAVE), nombre, rol, estado);
        u.setMotivoRechazo(motivo);
        usuarios.save(u);
    }
}

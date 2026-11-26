package com.gaja.usuarios.service;

import com.gaja.usuarios.dto.*;
import com.gaja.usuarios.model.EstadoUsuario;
import com.gaja.usuarios.model.Sesion;
import com.gaja.usuarios.model.Usuario;
import com.gaja.usuarios.repo.SesionRepository;
import com.gaja.usuarios.repo.UsuarioRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    public static final String ADMIN = "Administrador";
    public static final String EXTERNO = "Institución externa";
    public static final List<String> ROLES =
            List.of("Cadete", "Oficial", "Oficial de semana", EXTERNO, ADMIN);

    private final UsuarioRepository usuarios;
    private final SesionRepository sesiones;
    private final PasswordEncoder encoder;

    public UsuarioService(UsuarioRepository usuarios, SesionRepository sesiones, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.encoder = encoder;
    }

    // ---------- Login ----------

    /**
     * 401 si el correo, la contrasena o el rol no coinciden (mismo mensaje para no dar pistas).
     * 403 si las credenciales son correctas pero el perfil no puede entrar: pendiente, rechazado o desactivado.
     */
    @Transactional
    public LoginResponse login(LoginRequest req) {
        Usuario u = usuarios.findByEmailIgnoreCase(req.email().trim()).orElse(null);
        if (u == null || !encoder.matches(req.password(), u.getPasswordHash()) || !u.getRol().equals(req.rol())) {
            throw new UnauthorizedException("Correo, contraseña o rol incorrectos");
        }
        switch (u.getEstado()) {
            case PENDIENTE -> throw new ForbiddenException("Tu perfil está pendiente de aprobación");
            case RECHAZADO -> throw new ForbiddenException(
                    "Tu perfil fue rechazado" + (u.getMotivoRechazo() == null ? "" : ": " + u.getMotivoRechazo()));
            case DESACTIVADO -> throw new ForbiddenException("Tu perfil está desactivado. Contacta al administrador");
            default -> { }
        }
        String token = UUID.randomUUID().toString();
        sesiones.save(new Sesion(u.getId(), token));
        return new LoginResponse(u.getId(), u.getEmail(), u.getNombre(), u.getRol(), u.getEstado().name(), token);
    }

    // ---------- Alta ----------

    /** Registro publico: nunca puede crear un administrador y el perfil queda pendiente de aprobacion. */
    @Transactional
    public UsuarioResponse registrar(RegistroRequest req) {
        if (ADMIN.equals(req.rol())) {
            throw new IllegalArgumentException("No se puede registrar un administrador");
        }
        return crear(req, EstadoUsuario.PENDIENTE);
    }

    /** Alta hecha por el administrador ("Crear usuario"): el perfil nace aprobado. */
    @Transactional
    public UsuarioResponse crearComoAdmin(RegistroRequest req) {
        return crear(req, EstadoUsuario.APROBADO);
    }

    private UsuarioResponse crear(RegistroRequest req, EstadoUsuario estado) {
        if (!ROLES.contains(req.rol())) {
            throw new IllegalArgumentException("Rol no válido");
        }
        String institucion = limpio(req.institucion());
        if (EXTERNO.equals(req.rol()) && institucion == null) {
            throw new IllegalArgumentException("Indica el nombre de la institución externa");
        }
        String email = req.email().trim().toLowerCase();
        if (usuarios.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Ya existe un usuario con ese correo");
        }
        Usuario u = new Usuario(email, encoder.encode(req.password()), req.nombre().trim(), req.rol(), estado);
        u.setTelefono(limpio(req.telefono()));
        u.setInstitucion(institucion);
        return UsuarioResponse.of(usuarios.save(u));
    }

    // ---------- Consulta ----------

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar(String estado, String rol) {
        EstadoUsuario e = estado == null || estado.isBlank() ? null : parseEstado(estado);
        boolean hayRol = rol != null && !rol.isBlank();
        List<Usuario> lista;
        if (e != null && hayRol) lista = usuarios.findByEstadoAndRolOrderByNombreAsc(e, rol);
        else if (e != null) lista = usuarios.findByEstadoOrderByNombreAsc(e);
        else if (hayRol) lista = usuarios.findByRolOrderByNombreAsc(rol);
        else lista = usuarios.findAllByOrderByNombreAsc();
        return lista.stream().map(UsuarioResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtener(Long id) {
        return UsuarioResponse.of(buscar(id));
    }

    /** La app guarda el correo de la sesión; con él busca su propio perfil. */
    @Transactional(readOnly = true)
    public UsuarioResponse obtenerPorCorreo(String email) {
        return usuarios.findByEmailIgnoreCase(email.trim())
                .map(UsuarioResponse::of)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    @Transactional(readOnly = true)
    public List<SesionResponse> sesiones(Long id) {
        buscar(id);
        return sesiones.findByUsuarioIdOrderByFechaDesc(id).stream().map(SesionResponse::of).toList();
    }

    // ---------- Edicion ----------

    @Transactional
    public UsuarioResponse actualizar(Long id, ActualizarRequest req) {
        Usuario u = buscar(id);
        u.setNombre(req.nombre().trim());
        u.setTelefono(limpio(req.telefono()));
        u.setInstitucion(limpio(req.institucion()));
        if (req.notifReservas() != null) u.setNotifReservas(req.notifReservas());
        if (req.notifAlertas() != null) u.setNotifAlertas(req.notifAlertas());
        return UsuarioResponse.of(usuarios.save(u));
    }

    @Transactional
    public void cambiarPassword(Long id, CambiarPasswordRequest req) {
        Usuario u = buscar(id);
        if (!encoder.matches(req.actual(), u.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta");
        }
        u.setPasswordHash(encoder.encode(req.nueva()));
        usuarios.save(u);
    }

    @Transactional
    public void eliminar(Long id) {
        Usuario u = buscar(id);
        sesiones.deleteByUsuarioId(id);
        usuarios.delete(u);
    }

    // ---------- Cambios de estado (los hace el administrador) ----------

    @Transactional
    public UsuarioResponse aprobar(Long id) {
        Usuario u = buscar(id);
        exigir(u, EstadoUsuario.PENDIENTE, "Solo se puede aprobar un perfil pendiente");
        u.setEstado(EstadoUsuario.APROBADO);
        u.setMotivoRechazo(null);
        return UsuarioResponse.of(usuarios.save(u));
    }

    @Transactional
    public UsuarioResponse rechazar(Long id, String motivo) {
        Usuario u = buscar(id);
        exigir(u, EstadoUsuario.PENDIENTE, "Solo se puede rechazar un perfil pendiente");
        u.setEstado(EstadoUsuario.RECHAZADO);
        u.setMotivoRechazo(motivo.trim());
        return UsuarioResponse.of(usuarios.save(u));
    }

    @Transactional
    public UsuarioResponse desactivar(Long id) {
        Usuario u = buscar(id);
        exigir(u, EstadoUsuario.APROBADO, "Solo se puede desactivar un perfil aprobado");
        u.setEstado(EstadoUsuario.DESACTIVADO);
        return UsuarioResponse.of(usuarios.save(u));
    }

    @Transactional
    public UsuarioResponse activar(Long id) {
        Usuario u = buscar(id);
        exigir(u, EstadoUsuario.DESACTIVADO, "Solo se puede activar un perfil desactivado");
        u.setEstado(EstadoUsuario.APROBADO);
        return UsuarioResponse.of(usuarios.save(u));
    }

    // ---------- Utilidades ----------

    private Usuario buscar(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    private void exigir(Usuario u, EstadoUsuario esperado, String mensaje) {
        if (u.getEstado() != esperado) throw new ConflictException(mensaje);
    }

    private EstadoUsuario parseEstado(String texto) {
        try {
            return EstadoUsuario.valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Estado no válido");
        }
    }

    private String limpio(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}

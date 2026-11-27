package com.gaja.usuarios.service;

import com.gaja.usuarios.model.EstadoUsuario;
import com.gaja.usuarios.model.Sesion;
import com.gaja.usuarios.model.SesionRevocada;
import com.gaja.usuarios.model.Usuario;
import com.gaja.usuarios.repo.SesionRepository;
import com.gaja.usuarios.repo.SesionRevocadaRepository;
import com.gaja.usuarios.repo.UsuarioRepository;
import com.gaja.usuarios.security.AuthUser;
import com.gaja.usuarios.security.JwtService;
import com.gaja.usuarios.security.NoAutorizadoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cierre de sesión (revocación) y renovación del token. */
@Service
public class SesionService {

    private final UsuarioRepository usuarios;
    private final SesionRepository sesiones;
    private final SesionRevocadaRepository revocadas;
    private final JwtService jwt;

    public SesionService(UsuarioRepository usuarios, SesionRepository sesiones,
                         SesionRevocadaRepository revocadas, JwtService jwt) {
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.revocadas = revocadas;
        this.jwt = jwt;
    }

    @Transactional(readOnly = true)
    public boolean estaRevocada(String idSesion) {
        return idSesion != null && revocadas.existsById(idSesion);
    }

    /** Cierra una sesión: su token deja de servir en este servicio aunque no haya vencido. */
    @Transactional
    public void cerrar(String idSesion) {
        if (idSesion != null && !revocadas.existsById(idSesion)) {
            revocadas.save(new SesionRevocada(idSesion));
        }
    }

    /** Cierra todas las sesiones de una persona (cambio de clave, cuenta desactivada o eliminada). */
    @Transactional
    public void revocarTodas(Long usuarioId) {
        for (Sesion s : sesiones.findByUsuarioIdOrderByFechaDesc(usuarioId)) {
            cerrar(s.getToken());
        }
    }

    /**
     * Token nuevo para la misma sesión. Toma el rol actual desde la base de datos, así un cambio de rol
     * se aplica al renovar. Si la cuenta ya no está aprobada, no se renueva.
     */
    @Transactional(readOnly = true)
    public String renovar(AuthUser actual, String idSesion) {
        Usuario u = usuarios.findById(actual.id())
                .orElseThrow(() -> new NoAutorizadoException("Tu sesión no es válida. Inicia sesión de nuevo"));
        if (u.getEstado() != EstadoUsuario.APROBADO) {
            throw new NoAutorizadoException("Tu sesión no es válida. Inicia sesión de nuevo");
        }
        return jwt.generar(u.getId(), u.getEmail(), u.getRol(), idSesion);
    }
}

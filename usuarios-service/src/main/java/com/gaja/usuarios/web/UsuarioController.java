package com.gaja.usuarios.web;

import com.gaja.usuarios.security.AuthContext;

import com.gaja.usuarios.security.RequiereAdmin;

import com.gaja.usuarios.security.Publico;

import com.gaja.usuarios.dto.*;
import com.gaja.usuarios.security.AuthUser;
import com.gaja.usuarios.security.JwtService;
import com.gaja.usuarios.security.NoAutorizadoException;
import com.gaja.usuarios.service.AuditoriaService;
import com.gaja.usuarios.service.ControlIntentosService;
import com.gaja.usuarios.service.DemasiadosIntentosException;
import com.gaja.usuarios.service.ForbiddenException;
import com.gaja.usuarios.service.RecuperacionService;
import com.gaja.usuarios.service.SesionService;
import com.gaja.usuarios.service.UnauthorizedException;
import com.gaja.usuarios.service.UsuarioService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService service;
    private final ControlIntentosService intentos;
    private final AuditoriaService auditoria;
    private final SesionService sesiones;
    private final RecuperacionService recuperacion;
    private final JwtService jwt;

    public UsuarioController(UsuarioService service, ControlIntentosService intentos,
                             AuditoriaService auditoria, SesionService sesiones,
                             RecuperacionService recuperacion, JwtService jwt) {
        this.service = service;
        this.intentos = intentos;
        this.auditoria = auditoria;
        this.sesiones = sesiones;
        this.recuperacion = recuperacion;
        this.jwt = jwt;
    }

    /**
     * Inicio de sesión. Después de 5 intentos fallidos seguidos el correo queda bloqueado 15 minutos (429).
     * Todo intento queda en el registro de auditoría.
     */
    @Publico
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        String correo = req.email().trim().toLowerCase();
        try {
            intentos.exigirNoBloqueado(correo);
        } catch (DemasiadosIntentosException e) {
            auditoria.registrar(correo, "LOGIN_BLOQUEADO", "Intento con la cuenta bloqueada");
            throw e;
        }
        try {
            LoginResponse respuesta = service.login(req);
            intentos.limpiar(correo);
            auditoria.registrar(correo, "LOGIN_OK", null);
            return respuesta;
        } catch (UnauthorizedException e) {
            boolean bloqueado = intentos.registrarFallo(correo);
            auditoria.registrar(correo, "LOGIN_FALLIDO", bloqueado ? "La cuenta quedó bloqueada" : null);
            throw e;
        } catch (ForbiddenException e) {
            // La clave era correcta, pero el perfil no puede entrar (pendiente, rechazado o desactivado).
            intentos.limpiar(correo);
            auditoria.registrar(correo, "LOGIN_NO_PERMITIDO", e.getMessage());
            throw e;
        }
    }

    /** Cierra la sesión actual: el token deja de servir en este servicio. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader("Authorization") String autorizacion) {
        AuthUser yo = AuthContext.actual();
        jwt.idSesion(tokenDe(autorizacion)).ifPresent(sesiones::cerrar);
        auditoria.registrar(yo.email(), "LOGOUT", null);
    }

    /** Token nuevo para la misma sesión (con el rol actual de la cuenta). */
    @PostMapping("/renovar")
    public RenovarResponse renovar(@RequestHeader("Authorization") String autorizacion) {
        String idSesion = jwt.idSesion(tokenDe(autorizacion))
                .orElseThrow(() -> new NoAutorizadoException("Tu sesión no es válida. Inicia sesión de nuevo"));
        return new RenovarResponse(sesiones.renovar(AuthContext.actual(), idSesion));
    }

    /** Pide el código de recuperación. Responde igual exista o no el correo. */
    @Publico
    @PostMapping("/recuperar")
    public Map<String, String> recuperar(@Valid @RequestBody RecuperarRequest req) {
        recuperacion.solicitar(req.email());
        return Map.of("mensaje", "Si el correo está registrado, te enviamos un código para recuperar tu contraseña");
    }

    /** Cambia la contraseña con el código recibido por correo (un solo uso). */
    @Publico
    @PostMapping("/restablecer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restablecer(@Valid @RequestBody RestablecerRequest req) {
        recuperacion.restablecer(req.email(), req.codigo(), req.nueva());
    }

    private static String tokenDe(String autorizacion) {
        return autorizacion.startsWith("Bearer ") ? autorizacion.substring("Bearer ".length()).trim() : "";
    }

    /** Registro publico: el perfil queda PENDIENTE hasta que el administrador lo apruebe. */
    @Publico
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registro(@Valid @RequestBody RegistroRequest req) {
        return service.registrar(req);
    }

    /** "Crear usuario" del administrador: nace APROBADO. */
    @RequiereAdmin
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse crear(@Valid @RequestBody RegistroRequest req) {
        UsuarioResponse creado = service.crearComoAdmin(req);
        auditar("USUARIO_CREADO", "id=" + creado.id() + " rol=" + creado.rol());
        return creado;
    }

    @RequiereAdmin
    @GetMapping
    public List<UsuarioResponse> listar(@RequestParam(required = false) String estado,
                                        @RequestParam(required = false) String rol) {
        return service.listar(estado, rol);
    }

    /** Perfil de la persona con ese correo (el de su sesión). Va antes de "/{id}" para que no se confundan. */
    @GetMapping("/por-correo")
    public UsuarioResponse porCorreo(@RequestParam String email) {
        AuthContext.exigirMismoCorreoOAdmin(email);
        return service.obtenerPorCorreo(email);
    }

    @GetMapping("/{id}")
    public UsuarioResponse obtener(@PathVariable Long id) {
        AuthContext.exigirMismoIdOAdmin(id);
        return service.obtener(id);
    }

    @PutMapping("/{id}")
    public UsuarioResponse actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarRequest req) {
        AuthContext.exigirMismoIdOAdmin(id);
        return service.actualizar(id, req);
    }

    @PatchMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarPassword(@PathVariable Long id, @Valid @RequestBody CambiarPasswordRequest req) {
        AuthContext.exigirMismoIdOAdmin(id);
        service.cambiarPassword(id, req);
        auditar("PASSWORD_CAMBIADA", "id=" + id);
    }

    @RequiereAdmin
    @PatchMapping("/{id}/aprobar")
    public UsuarioResponse aprobar(@PathVariable Long id) {
        UsuarioResponse r = service.aprobar(id);
        auditar("USUARIO_APROBADO", "id=" + id);
        return r;
    }

    @RequiereAdmin
    @PatchMapping("/{id}/rechazar")
    public UsuarioResponse rechazar(@PathVariable Long id, @Valid @RequestBody RechazoRequest req) {
        UsuarioResponse r = service.rechazar(id, req.motivo());
        auditar("USUARIO_RECHAZADO", "id=" + id);
        return r;
    }

    @RequiereAdmin
    @PatchMapping("/{id}/desactivar")
    public UsuarioResponse desactivar(@PathVariable Long id) {
        UsuarioResponse r = service.desactivar(id);
        sesiones.revocarTodas(id);
        auditar("USUARIO_DESACTIVADO", "id=" + id);
        return r;
    }

    @RequiereAdmin
    @PatchMapping("/{id}/activar")
    public UsuarioResponse activar(@PathVariable Long id) {
        UsuarioResponse r = service.activar(id);
        auditar("USUARIO_ACTIVADO", "id=" + id);
        return r;
    }

    @GetMapping("/{id}/sesiones")
    public List<SesionResponse> sesiones(@PathVariable Long id) {
        AuthContext.exigirMismoIdOAdmin(id);
        return service.sesiones(id);
    }

    @RequiereAdmin
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        sesiones.revocarTodas(id);
        service.eliminar(id);
        auditar("USUARIO_ELIMINADO", "id=" + id);
    }

    private void auditar(String accion, String detalle) {
        auditoria.registrar(AuthContext.actual().email(), accion, detalle);
    }
}

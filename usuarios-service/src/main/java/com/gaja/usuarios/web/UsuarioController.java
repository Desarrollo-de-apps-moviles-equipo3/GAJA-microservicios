package com.gaja.usuarios.web;

import com.gaja.usuarios.security.AuthContext;

import com.gaja.usuarios.security.RequiereAdmin;

import com.gaja.usuarios.security.Publico;

import com.gaja.usuarios.dto.*;
import com.gaja.usuarios.service.UsuarioService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @Publico
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return service.login(req);
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
        return service.crearComoAdmin(req);
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
    }

    @RequiereAdmin
    @PatchMapping("/{id}/aprobar")
    public UsuarioResponse aprobar(@PathVariable Long id) {
        return service.aprobar(id);
    }

    @RequiereAdmin
    @PatchMapping("/{id}/rechazar")
    public UsuarioResponse rechazar(@PathVariable Long id, @Valid @RequestBody RechazoRequest req) {
        return service.rechazar(id, req.motivo());
    }

    @RequiereAdmin
    @PatchMapping("/{id}/desactivar")
    public UsuarioResponse desactivar(@PathVariable Long id) {
        return service.desactivar(id);
    }

    @RequiereAdmin
    @PatchMapping("/{id}/activar")
    public UsuarioResponse activar(@PathVariable Long id) {
        return service.activar(id);
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
        service.eliminar(id);
    }
}

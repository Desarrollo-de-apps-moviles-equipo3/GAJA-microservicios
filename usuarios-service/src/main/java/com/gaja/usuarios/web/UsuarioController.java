package com.gaja.usuarios.web;

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

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return service.login(req);
    }

    /** Registro publico: el perfil queda PENDIENTE hasta que el administrador lo apruebe. */
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registro(@Valid @RequestBody RegistroRequest req) {
        return service.registrar(req);
    }

    /** "Crear usuario" del administrador: nace APROBADO. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse crear(@Valid @RequestBody RegistroRequest req) {
        return service.crearComoAdmin(req);
    }

    @GetMapping
    public List<UsuarioResponse> listar(@RequestParam(required = false) String estado,
                                        @RequestParam(required = false) String rol) {
        return service.listar(estado, rol);
    }

    @GetMapping("/{id}")
    public UsuarioResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PutMapping("/{id}")
    public UsuarioResponse actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarRequest req) {
        return service.actualizar(id, req);
    }

    @PatchMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarPassword(@PathVariable Long id, @Valid @RequestBody CambiarPasswordRequest req) {
        service.cambiarPassword(id, req);
    }

    @PatchMapping("/{id}/aprobar")
    public UsuarioResponse aprobar(@PathVariable Long id) {
        return service.aprobar(id);
    }

    @PatchMapping("/{id}/rechazar")
    public UsuarioResponse rechazar(@PathVariable Long id, @Valid @RequestBody RechazoRequest req) {
        return service.rechazar(id, req.motivo());
    }

    @PatchMapping("/{id}/desactivar")
    public UsuarioResponse desactivar(@PathVariable Long id) {
        return service.desactivar(id);
    }

    @PatchMapping("/{id}/activar")
    public UsuarioResponse activar(@PathVariable Long id) {
        return service.activar(id);
    }

    @GetMapping("/{id}/sesiones")
    public List<SesionResponse> sesiones(@PathVariable Long id) {
        return service.sesiones(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        service.eliminar(id);
    }
}

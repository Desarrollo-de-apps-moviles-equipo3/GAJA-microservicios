package com.gaja.usuarios.web;

import com.gaja.usuarios.dto.AuditoriaResponse;
import com.gaja.usuarios.security.RequiereAdmin;
import com.gaja.usuarios.service.AuditoriaService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Registro de acciones críticas. Solo el administrador puede leerlo. */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoria;

    public AuditoriaController(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    @RequiereAdmin
    @GetMapping
    public List<AuditoriaResponse> listar(@RequestParam(required = false) String accion,
                                          @RequestParam(defaultValue = "100") int limite) {
        return auditoria.listar(accion, limite);
    }
}

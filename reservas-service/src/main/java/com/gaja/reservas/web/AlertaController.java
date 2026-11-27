package com.gaja.reservas.web;

import com.gaja.reservas.security.AuthContext;

import com.gaja.reservas.model.Alerta;
import com.gaja.reservas.service.AlertaService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alertas")
public class AlertaController {

    private final AlertaService alertaService;

    public AlertaController(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    @GetMapping
    public List<Alerta> listarPorUsuario(@RequestParam String email) {
        AuthContext.exigirMismoCorreoOAdmin(email);
        return alertaService.listarPorUsuario(email);
    }

    @GetMapping("/no-leidas")
    public Map<String, Long> contarNoLeidas(@RequestParam String email) {
        AuthContext.exigirMismoCorreoOAdmin(email);
        long cantidad = alertaService.contarNoLeidas(email);
        return Map.of("cantidad", cantidad);
    }

    @PatchMapping("/{id}/leida")
    public Alerta marcarLeida(
        @PathVariable Long id,
        @RequestParam String email
    ) {
        AuthContext.exigirMismoCorreoOAdmin(email);
        return alertaService.marcarLeida(id, email);
    }
}
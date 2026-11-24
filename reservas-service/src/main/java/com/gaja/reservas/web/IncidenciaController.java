package com.gaja.reservas.web;

import com.gaja.reservas.dto.CrearIncidenciaRequest;
import com.gaja.reservas.model.Incidencia;
import com.gaja.reservas.service.IncidenciaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/incidencias")
public class IncidenciaController {

    private final IncidenciaService incidenciaService;

    public IncidenciaController(IncidenciaService incidenciaService) {
        this.incidenciaService = incidenciaService;
    }

    @PostMapping
    public ResponseEntity<Incidencia> crear(
            @Valid @RequestBody CrearIncidenciaRequest request
    ) {
        Incidencia incidencia = incidenciaService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(incidencia);
    }

    @GetMapping("/usuario")
    public List<Incidencia> listarPorUsuario(@RequestParam String email) {
        return incidenciaService.listarPorUsuario(email);
    }

    @GetMapping
    public List<Incidencia> listarTodas() {
        return incidenciaService.listarTodas();
    }

    @GetMapping("/pendientes/count")
    public Map<String, Long> contarPendientes() {
        return Map.of("cantidad", incidenciaService.contarPendientes());
    }

    @PatchMapping("/{id}/resolver")
    public Incidencia marcarResuelta(@PathVariable Long id) {
        return incidenciaService.marcarResuelta(id);
    }
}
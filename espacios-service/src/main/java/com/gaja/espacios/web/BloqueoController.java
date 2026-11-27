package com.gaja.espacios.web;

import com.gaja.espacios.security.RequiereAdmin;

import com.gaja.espacios.dto.BloqueoResponse;
import com.gaja.espacios.service.BloqueoService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bloqueos")
public class BloqueoController {

    private final BloqueoService bloqueos;

    public BloqueoController(BloqueoService bloqueos) {
        this.bloqueos = bloqueos;
    }

    @GetMapping
    public List<BloqueoResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return bloqueos.listar(fecha);
    }

    @RequiereAdmin
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        bloqueos.eliminar(id);
    }
}

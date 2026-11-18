package com.gaja.espacios.web;

import com.gaja.espacios.dto.*;
import com.gaja.espacios.service.BloqueoService;
import com.gaja.espacios.service.EspacioService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/espacios")
public class EspacioController {

    private final EspacioService espacios;
    private final BloqueoService bloqueos;

    public EspacioController(EspacioService espacios, BloqueoService bloqueos) {
        this.espacios = espacios;
        this.bloqueos = bloqueos;
    }

    @GetMapping
    public List<EspacioResponse> listar(@RequestParam(required = false) String categoria,
                                        @RequestParam(required = false) Boolean disponible) {
        return espacios.listar(categoria, disponible);
    }

    @GetMapping("/{id}")
    public EspacioResponse obtener(@PathVariable String id) {
        return espacios.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EspacioResponse crear(@Valid @RequestBody EspacioRequest req) {
        return espacios.crear(req);
    }

    @PutMapping("/{id}")
    public EspacioResponse actualizar(@PathVariable String id, @Valid @RequestBody EspacioRequest req) {
        return espacios.actualizar(id, req);
    }

    @PatchMapping("/{id}/disponibilidad")
    public EspacioResponse disponibilidad(@PathVariable String id, @Valid @RequestBody DisponibilidadRequest req) {
        return espacios.cambiarDisponibilidad(id, req.available());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String id) {
        espacios.eliminar(id);
    }

    @GetMapping("/{id}/bloqueos")
    public List<BloqueoResponse> bloqueosDelEspacio(
            @PathVariable String id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return bloqueos.listarPorEspacio(id, fecha);
    }

    @PostMapping("/{id}/bloqueos")
    @ResponseStatus(HttpStatus.CREATED)
    public BloqueoResponse bloquear(@PathVariable String id, @Valid @RequestBody BloqueoRequest req) {
        return bloqueos.crear(id, req);
    }
}

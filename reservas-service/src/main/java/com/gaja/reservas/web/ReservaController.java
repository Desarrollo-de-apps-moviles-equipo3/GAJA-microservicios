package com.gaja.reservas.web;

import com.gaja.reservas.dto.CrearReservaRequest;
import com.gaja.reservas.model.Reserva;
import com.gaja.reservas.service.ReservaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import com.gaja.reservas.dto.ActualizarEstadoRequest;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private final ReservaService reservaService;

    public ReservaController(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    @PostMapping
    public ResponseEntity<Reserva> crear(
            @Valid @RequestBody CrearReservaRequest request
    ) {
        Reserva reservaCreada = reservaService.crearReserva(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(reservaCreada);
    }

    @GetMapping("/usuario")
    public List<Reserva> listarPorUsuario(
            @RequestParam String email
    ) {
        return reservaService.listarPorUsuario(email);
    
    }

    @GetMapping("/disponibilidad")
    public List<String> listarHorariosOcupados(
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fecha,

        @RequestParam
        String espacio
    ) {
        return reservaService.listarHorariosOcupados(fecha, espacio);
    }
    
    @PatchMapping("/{id}/cancelar")
    public Reserva cancelar(
        @PathVariable Long id,
        @RequestParam String email
    ) {
        return reservaService.cancelarReserva(id, email);

    }


    @PatchMapping("/{id}/estado")
    public Reserva actualizarEstado(    
        @PathVariable Long id,
        @Valid @RequestBody ActualizarEstadoRequest request
    ) {
        return reservaService.actualizarEstado(id, request.getEstado());
    }

}
package com.gaja.espacios.service;

import com.gaja.espacios.dto.BloqueoRequest;
import com.gaja.espacios.dto.BloqueoResponse;
import com.gaja.espacios.model.Bloqueo;
import com.gaja.espacios.model.Espacio;
import com.gaja.espacios.repo.BloqueoRepository;
import com.gaja.espacios.repo.EspacioRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BloqueoService {

    private final BloqueoRepository bloqueos;
    private final EspacioRepository espacios;

    public BloqueoService(BloqueoRepository bloqueos, EspacioRepository espacios) {
        this.bloqueos = bloqueos;
        this.espacios = espacios;
    }

    @Transactional(readOnly = true)
    public List<BloqueoResponse> listar(LocalDate fecha) {
        List<Bloqueo> lista = fecha == null ? bloqueos.findAll() : bloqueos.findByFecha(fecha);
        return lista.stream().map(BloqueoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<BloqueoResponse> listarPorEspacio(String espacioId, LocalDate fecha) {
        Espacio e = buscarEspacio(espacioId);
        List<Bloqueo> lista = fecha == null ? bloqueos.findByEspacio(e) : bloqueos.findByEspacioAndFecha(e, fecha);
        return lista.stream().map(BloqueoResponse::from).toList();
    }

    @Transactional
    public BloqueoResponse crear(String espacioId, BloqueoRequest req) {
        Espacio e = buscarEspacio(espacioId);
        String franja = req.timeSlot().trim();
        if (bloqueos.existsByEspacioAndFechaAndFranja(e, req.date(), franja)) {
            throw new ConflictException("Ese horario ya esta bloqueado");
        }
        return BloqueoResponse.from(bloqueos.save(new Bloqueo(e, req.date(), franja, req.reason())));
    }

    @Transactional
    public void eliminar(Long id) {
        if (!bloqueos.existsById(id)) {
            throw new NotFoundException("No existe el bloqueo " + id);
        }
        bloqueos.deleteById(id);
    }

    private Espacio buscarEspacio(String id) {
        return espacios.findById(id).orElseThrow(() -> new NotFoundException("No existe el espacio " + id));
    }
}

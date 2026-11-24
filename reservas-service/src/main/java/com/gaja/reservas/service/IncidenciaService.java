package com.gaja.reservas.service;

import com.gaja.reservas.dto.CrearIncidenciaRequest;
import com.gaja.reservas.model.EstadoIncidencia;
import com.gaja.reservas.model.Incidencia;
import com.gaja.reservas.repo.IncidenciaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class IncidenciaService {

    private final IncidenciaRepository incidenciaRepository;

    public IncidenciaService(IncidenciaRepository incidenciaRepository) {
        this.incidenciaRepository = incidenciaRepository;
    }

    @Transactional
    public Incidencia crear(CrearIncidenciaRequest request) {
        Incidencia incidencia = new Incidencia();
        incidencia.setUsuarioEmail(request.getUsuarioEmail().trim());
        incidencia.setEspacio(request.getEspacio().trim());
        incidencia.setDescripcion(request.getDescripcion().trim());
        incidencia.setEstado(EstadoIncidencia.PENDIENTE);

        return incidenciaRepository.save(incidencia);
    }

    @Transactional(readOnly = true)
    public List<Incidencia> listarPorUsuario(String usuarioEmail) {
        return incidenciaRepository.findByUsuarioEmailOrderByCreadaEnDesc(
                usuarioEmail.trim()
        );
    }

    @Transactional(readOnly = true)
    public List<Incidencia> listarTodas() {
        return incidenciaRepository.findAllByOrderByCreadaEnDesc();
    }

    @Transactional(readOnly = true)
    public long contarPendientes() {
        return incidenciaRepository.countByEstado(
                EstadoIncidencia.PENDIENTE
        );
    }

    @Transactional
    public Incidencia marcarResuelta(Long id) {
        Incidencia incidencia = incidenciaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No encontramos esa incidencia"
                ));

        incidencia.setEstado(EstadoIncidencia.RESUELTA);
        return incidenciaRepository.save(incidencia);
    }
}
package com.gaja.reservas.service;

import com.gaja.reservas.model.Alerta;
import com.gaja.reservas.repo.AlertaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AlertaService {

    private final AlertaRepository alertaRepository;

    public AlertaService(AlertaRepository alertaRepository) {
        this.alertaRepository = alertaRepository;
    }

    @Transactional
    public Alerta crearAlerta(
            String usuarioEmail,
            Long reservaId,
            String mensaje
    ) {
        Alerta alerta = new Alerta();
        alerta.setUsuarioEmail(usuarioEmail.trim());
        alerta.setReservaId(reservaId);
        alerta.setMensaje(mensaje);

        return alertaRepository.save(alerta);
    }

    @Transactional(readOnly = true)
    public List<Alerta> listarPorUsuario(String usuarioEmail) {
        return alertaRepository.findByUsuarioEmailOrderByCreadaEnDesc(
                usuarioEmail.trim()
        );
    }

    @Transactional(readOnly = true)
    public long contarNoLeidas(String usuarioEmail) {
        return alertaRepository.countByUsuarioEmailAndLeidaFalse(
                usuarioEmail.trim()
        );
    }

    @Transactional
    public Alerta marcarLeida(Long id, String usuarioEmail) {
    Alerta alerta = alertaRepository
            .findByIdAndUsuarioEmail(id, usuarioEmail.trim())
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No encontramos esa alerta"
            ));

        alerta.setLeida(true);
        return alertaRepository.save(alerta);
    }
}
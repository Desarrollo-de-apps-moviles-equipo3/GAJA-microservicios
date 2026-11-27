package com.gaja.usuarios.service;

import com.gaja.usuarios.dto.AuditoriaResponse;
import com.gaja.usuarios.model.Auditoria;
import com.gaja.usuarios.repo.AuditoriaRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Guarda quién hizo qué en las acciones críticas. Si falla al guardar, no interrumpe la acción original. */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private final AuditoriaRepository registros;

    public AuditoriaService(AuditoriaRepository registros) {
        this.registros = registros;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String actor, String accion, String detalle) {
        try {
            registros.save(new Auditoria(
                    limpio(actor == null ? "anonimo" : actor, 120), accion, limpio(detalle, 300)));
        } catch (RuntimeException e) {
            log.warn("No se pudo guardar el registro de auditoría ({})", accion);
        }
    }

    @Transactional(readOnly = true)
    public List<AuditoriaResponse> listar(String accion, int limite) {
        var pagina = PageRequest.of(0, Math.min(Math.max(limite, 1), 500));
        List<Auditoria> lista = accion == null || accion.isBlank()
                ? registros.findAllByOrderByFechaDescIdDesc(pagina)
                : registros.findByAccionOrderByFechaDescIdDesc(accion.trim(), pagina);
        return lista.stream().map(AuditoriaResponse::of).toList();
    }

    /** Sin saltos de línea ni caracteres de control (evita falsear registros) y con largo máximo. */
    private static String limpio(String texto, int maximo) {
        if (texto == null) return null;
        String sinControl = texto.replaceAll("\\p{Cntrl}", " ").trim();
        return sinControl.length() > maximo ? sinControl.substring(0, maximo) : sinControl;
    }
}

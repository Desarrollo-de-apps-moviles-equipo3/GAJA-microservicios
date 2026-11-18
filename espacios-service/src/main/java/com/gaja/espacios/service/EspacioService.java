package com.gaja.espacios.service;

import com.gaja.espacios.dto.EspacioRequest;
import com.gaja.espacios.dto.EspacioResponse;
import com.gaja.espacios.model.Categoria;
import com.gaja.espacios.model.Espacio;
import com.gaja.espacios.repo.BloqueoRepository;
import com.gaja.espacios.repo.CategoriaRepository;
import com.gaja.espacios.repo.EspacioRepository;
import java.text.Normalizer;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EspacioService {

    private final EspacioRepository espacios;
    private final CategoriaRepository categorias;
    private final BloqueoRepository bloqueos;

    public EspacioService(EspacioRepository espacios, CategoriaRepository categorias, BloqueoRepository bloqueos) {
        this.espacios = espacios;
        this.categorias = categorias;
        this.bloqueos = bloqueos;
    }

    @Transactional(readOnly = true)
    public List<EspacioResponse> listar(String categoria, Boolean disponible) {
        List<Espacio> lista;
        if (categoria != null && !categoria.isBlank()) {
            Categoria c = categorias.findByNombreIgnoreCase(categoria.trim())
                    .orElseThrow(() -> new NotFoundException("No existe la categoria " + categoria));
            lista = disponible == null
                    ? espacios.findByCategoria(c)
                    : espacios.findByCategoriaAndDisponible(c, disponible);
        } else if (disponible != null) {
            lista = espacios.findByDisponible(disponible);
        } else {
            lista = espacios.findAll();
        }
        return lista.stream().map(EspacioResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public EspacioResponse obtener(String id) {
        return EspacioResponse.from(buscar(id));
    }

    @Transactional
    public EspacioResponse crear(EspacioRequest req) {
        String id = (req.id() == null || req.id().isBlank()) ? slug(req.name()) : req.id().trim();
        if (espacios.existsById(id)) {
            throw new ConflictException("Ya existe el espacio " + id);
        }
        Espacio e = new Espacio(id);
        aplicar(e, req);
        return EspacioResponse.from(espacios.save(e));
    }

    @Transactional
    public EspacioResponse actualizar(String id, EspacioRequest req) {
        Espacio e = buscar(id);
        aplicar(e, req);
        return EspacioResponse.from(e);
    }

    /** Deshabilitar o volver a habilitar un espacio sin borrarlo. */
    @Transactional
    public EspacioResponse cambiarDisponibilidad(String id, boolean disponible) {
        Espacio e = buscar(id);
        e.setDisponible(disponible);
        return EspacioResponse.from(e);
    }

    @Transactional
    public void eliminar(String id) {
        Espacio e = buscar(id);
        bloqueos.deleteByEspacio(e);
        espacios.delete(e);
    }

    Espacio buscar(String id) {
        return espacios.findById(id).orElseThrow(() -> new NotFoundException("No existe el espacio " + id));
    }

    private void aplicar(Espacio e, EspacioRequest req) {
        Categoria c = categorias.findByNombreIgnoreCase(req.category().trim())
                .orElseThrow(() -> new NotFoundException("No existe la categoria " + req.category()));
        e.setNombre(req.name().trim());
        e.setCategoria(c);
        e.setDescripcion(req.description());
        e.setCapacidad(req.capacity());
        if (req.available() != null) e.setDisponible(req.available());
        if (req.colorHex() != null && !req.colorHex().isBlank()) e.setColorHex(req.colorHex());
        e.setTags(req.tags());
        e.setLatitud(req.latitude());
        e.setLongitud(req.longitude());
    }

    static String slug(String nombre) {
        String sinTildes = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase().trim().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}

package com.gaja.espacios.service;

import com.gaja.espacios.dto.CategoriaDto;
import com.gaja.espacios.model.Categoria;
import com.gaja.espacios.repo.CategoriaRepository;
import com.gaja.espacios.repo.EspacioRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoriaService {

    private final CategoriaRepository categorias;
    private final EspacioRepository espacios;

    public CategoriaService(CategoriaRepository categorias, EspacioRepository espacios) {
        this.categorias = categorias;
        this.espacios = espacios;
    }

    public List<CategoriaDto> listar() {
        return categorias.findAll().stream().map(CategoriaDto::from).toList();
    }

    @Transactional
    public CategoriaDto crear(String nombre) {
        String limpio = nombre.trim();
        if (categorias.findByNombreIgnoreCase(limpio).isPresent()) {
            throw new ConflictException("Ya existe la categoria " + limpio);
        }
        return CategoriaDto.from(categorias.save(new Categoria(limpio)));
    }

    @Transactional
    public CategoriaDto renombrar(Long id, String nombre) {
        Categoria c = categorias.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe la categoria " + id));
        c.setNombre(nombre.trim());
        return CategoriaDto.from(c);
    }

    @Transactional
    public void eliminar(Long id) {
        Categoria c = categorias.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe la categoria " + id));
        if (espacios.existsByCategoria(c)) {
            throw new ConflictException("La categoria tiene espacios; muevelos o eliminalos primero");
        }
        categorias.delete(c);
    }
}

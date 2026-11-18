package com.gaja.espacios.web;

import com.gaja.espacios.dto.CategoriaDto;
import com.gaja.espacios.service.CategoriaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaService categorias;

    public CategoriaController(CategoriaService categorias) {
        this.categorias = categorias;
    }

    @GetMapping
    public List<CategoriaDto> listar() {
        return categorias.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaDto crear(@Valid @RequestBody CategoriaDto body) {
        return categorias.crear(body.name());
    }

    @PutMapping("/{id}")
    public CategoriaDto renombrar(@PathVariable Long id, @Valid @RequestBody CategoriaDto body) {
        return categorias.renombrar(id, body.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        categorias.eliminar(id);
    }
}

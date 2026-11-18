package com.gaja.espacios.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaja.espacios.model.Categoria;
import com.gaja.espacios.model.Espacio;
import com.gaja.espacios.repo.CategoriaRepository;
import com.gaja.espacios.repo.EspacioRepository;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final CategoriaRepository categorias;
    private final EspacioRepository espacios;
    private final ObjectMapper mapper;

    public DataSeeder(CategoriaRepository categorias, EspacioRepository espacios, ObjectMapper mapper) {
        this.categorias = categorias;
        this.espacios = espacios;
        this.mapper = mapper;
    }

    @Override
    public void run(String... args) throws Exception {
        if (espacios.count() > 0) return;

        try (InputStream in = new ClassPathResource("seed/spaces.json").getInputStream()) {
            for (JsonNode n : mapper.readTree(in)) {
                String nombreCategoria = n.get("category").asText();
                Categoria cat = categorias.findByNombreIgnoreCase(nombreCategoria)
                        .orElseGet(() -> categorias.save(new Categoria(nombreCategoria)));

                Espacio e = new Espacio(n.get("id").asText());
                e.setNombre(n.get("name").asText());
                e.setCategoria(cat);
                e.setDescripcion(n.get("description").asText());
                e.setCapacidad(n.get("capacity").asInt());
                e.setDisponible(n.get("available").asBoolean());
                e.setColorHex(n.get("colorHex").asText());
                List<String> tags = new ArrayList<>();
                n.get("tags").forEach(t -> tags.add(t.asText()));
                e.setTags(tags);
                if (n.hasNonNull("latitude")) e.setLatitud(n.get("latitude").asDouble());
                if (n.hasNonNull("longitude")) e.setLongitud(n.get("longitude").asDouble());
                espacios.save(e);
            }
        }
    }
}

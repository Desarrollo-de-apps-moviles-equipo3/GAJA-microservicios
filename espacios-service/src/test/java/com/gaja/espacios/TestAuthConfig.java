package com.gaja.espacios;

import com.gaja.espacios.security.JwtService;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Las pruebas de API que NO son de seguridad entran como administrador:
 * a cada petición se le agrega un token válido. SeguridadApiTest NO usa esto, para probar sin token.
 */
@TestConfiguration
public class TestAuthConfig {

    @Bean
    MockMvcBuilderCustomizer tokenDeAdministrador(JwtService jwt) {
        String token = jwt.generar(1L, "admin@gaja.cl", "Administrador", "pruebas");
        return builder -> builder.defaultRequest(
                MockMvcRequestBuilders.get("/").header("Authorization", "Bearer " + token));
    }
}

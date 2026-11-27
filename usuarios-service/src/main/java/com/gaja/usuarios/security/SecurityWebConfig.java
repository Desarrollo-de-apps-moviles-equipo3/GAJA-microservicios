package com.gaja.usuarios.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Activa el AuthInterceptor para todas las rutas /api/**. */
@Configuration
public class SecurityWebConfig implements WebMvcConfigurer {

    private final AuthInterceptor interceptor;

    public SecurityWebConfig(AuthInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**");
    }
}

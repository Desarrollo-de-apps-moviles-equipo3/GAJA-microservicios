package com.gaja.usuarios.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Crea y valida los tokens de sesión (JWT firmado con HMAC).
 * Los tres servicios usan la MISMA clave (variable JWT_SECRET), así cada uno valida el token
 * por su cuenta, sin tener que preguntarle al servicio de usuarios.
 */
@Component
public class JwtService {

    private final SecretKey clave;
    private final long horasDeVida;

    public JwtService(
            @Value("${jwt.secret:}") String secreto,
            @Value("${jwt.expiration-hours:12}") long horasDeVida) {
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "Falta la variable de entorno JWT_SECRET o es muy corta (mínimo 32 caracteres).");
        }
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.horasDeVida = horasDeVida;
    }

    /** Token para quien acaba de iniciar sesión. [idSesion] identifica esa sesión. */
    public String generar(Long id, String email, String rol, String idSesion) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .id(idSesion)
                .subject(email)
                .claim("id", id)
                .claim("rol", rol)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(Duration.ofHours(horasDeVida))))
                .signWith(clave)
                .compact();
    }

    /** Devuelve quién es, o vacío si el token está mal firmado, alterado o vencido. */
    public Optional<AuthUser> validar(String token) {
        try {
            Claims datos = Jwts.parser().verifyWith(clave).build()
                    .parseSignedClaims(token).getPayload();
            Number id = datos.get("id", Number.class);
            String rol = datos.get("rol", String.class);
            if (id == null || rol == null || datos.getSubject() == null) {
                return Optional.empty();
            }
            return Optional.of(new AuthUser(id.longValue(), datos.getSubject(), rol));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

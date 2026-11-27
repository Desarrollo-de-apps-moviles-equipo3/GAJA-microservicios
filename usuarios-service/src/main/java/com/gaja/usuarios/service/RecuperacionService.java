package com.gaja.usuarios.service;

import com.gaja.usuarios.model.EstadoUsuario;
import com.gaja.usuarios.model.TokenRecuperacion;
import com.gaja.usuarios.model.Usuario;
import com.gaja.usuarios.repo.TokenRecuperacionRepository;
import com.gaja.usuarios.repo.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recuperar la contraseña con un código que llega por correo.
 * - El código tiene 8 caracteres, vale 30 minutos y sirve una sola vez.
 * - Solo se guarda su hash. Se piden máximo 3 por hora y se aceptan 5 intentos por código.
 * - Pedir el código responde siempre igual, exista o no el correo (no revela quién está registrado).
 */
@Service
public class RecuperacionService {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sin O, 0, I, 1
    private static final int LARGO_CODIGO = 8;
    private static final int MINUTOS_DE_VIGENCIA = 30;
    private static final int MAX_SOLICITUDES_POR_HORA = 3;
    private static final int MAX_INTENTOS_POR_CODIGO = 5;

    private final SecureRandom azar = new SecureRandom();
    private final UsuarioRepository usuarios;
    private final TokenRecuperacionRepository tokens;
    private final PasswordEncoder encoder;
    private final SesionService sesiones;
    private final ControlIntentosService intentos;
    private final CorreoService correo;
    private final AuditoriaService auditoria;

    public RecuperacionService(UsuarioRepository usuarios, TokenRecuperacionRepository tokens,
                               PasswordEncoder encoder, SesionService sesiones,
                               ControlIntentosService intentos, CorreoService correo,
                               AuditoriaService auditoria) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.encoder = encoder;
        this.sesiones = sesiones;
        this.intentos = intentos;
        this.correo = correo;
        this.auditoria = auditoria;
    }

    @Transactional
    public void solicitar(String emailCrudo) {
        String email = emailCrudo.trim().toLowerCase();
        Usuario u = usuarios.findByEmailIgnoreCase(email).orElse(null);
        if (u == null || u.getEstado() != EstadoUsuario.APROBADO) {
            return; // misma respuesta que si existiera
        }
        Instant ahora = Instant.now();
        if (tokens.countByUsuarioIdAndCreadoEnAfter(u.getId(), ahora.minus(Duration.ofHours(1)))
                >= MAX_SOLICITUDES_POR_HORA) {
            auditoria.registrar(email, "RECUPERACION_LIMITE", "Demasiadas solicitudes en una hora");
            return;
        }
        for (TokenRecuperacion anterior : tokens.findByUsuarioIdAndUsadoFalse(u.getId())) {
            anterior.setUsado(true);
            tokens.save(anterior);
        }
        String codigo = generarCodigo();
        tokens.save(new TokenRecuperacion(u.getId(), hash(codigo), ahora.plus(Duration.ofMinutes(MINUTOS_DE_VIGENCIA))));
        auditoria.registrar(email, "RECUPERACION_SOLICITADA", null);
        correo.enviarCodigoRecuperacion(u.getEmail(), codigo);
    }

    /** noRollbackFor: el conteo de intentos fallidos debe quedar guardado aunque se lance el error. */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void restablecer(String emailCrudo, String codigoCrudo, String nueva) {
        String email = emailCrudo.trim().toLowerCase();
        Usuario u = usuarios.findByEmailIgnoreCase(email).orElse(null);
        TokenRecuperacion token = u == null ? null
                : tokens.findFirstByUsuarioIdAndUsadoFalseOrderByCreadoEnDesc(u.getId()).orElse(null);
        if (u == null || token == null || u.getEstado() != EstadoUsuario.APROBADO
                || token.getExpira().isBefore(Instant.now())) {
            throw codigoInvalido();
        }

        String codigo = codigoCrudo.replaceAll("[\\s-]", "").toUpperCase();
        boolean coincide = MessageDigest.isEqual(
                hash(codigo).getBytes(StandardCharsets.UTF_8),
                token.getCodigoHash().getBytes(StandardCharsets.UTF_8));
        if (!coincide) {
            token.setIntentos(token.getIntentos() + 1);
            if (token.getIntentos() >= MAX_INTENTOS_POR_CODIGO) {
                token.setUsado(true);
            }
            tokens.save(token);
            auditoria.registrar(email, "RECUPERACION_CODIGO_INCORRECTO", null);
            throw codigoInvalido();
        }

        u.setPasswordHash(encoder.encode(nueva));
        usuarios.save(u);
        token.setUsado(true);
        tokens.save(token);
        sesiones.revocarTodas(u.getId());
        intentos.limpiar(email);
        auditoria.registrar(email, "PASSWORD_RECUPERADA", null);
    }

    private static IllegalArgumentException codigoInvalido() {
        return new IllegalArgumentException("El código no es válido o ya venció");
    }

    private String generarCodigo() {
        StringBuilder sb = new StringBuilder(LARGO_CODIGO);
        for (int i = 0; i < LARGO_CODIGO; i++) {
            sb.append(ALFABETO.charAt(azar.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    private static String hash(String texto) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

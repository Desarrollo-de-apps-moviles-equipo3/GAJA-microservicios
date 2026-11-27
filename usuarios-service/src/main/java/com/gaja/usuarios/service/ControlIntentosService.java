package com.gaja.usuarios.service;

import com.gaja.usuarios.model.IntentoLogin;
import com.gaja.usuarios.repo.IntentoLoginRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bloquea un correo por un rato después de varios inicios de sesión fallidos seguidos.
 * Por defecto: 5 intentos y 15 minutos (se pueden cambiar con seguridad.login.max-intentos
 * y seguridad.login.bloqueo-minutos). También cuenta los correos que no existen, para no
 * revelar cuáles están registrados.
 */
@Service
public class ControlIntentosService {

    private final IntentoLoginRepository intentos;
    private final int maximo;
    private final long minutosDeBloqueo;

    public ControlIntentosService(
            IntentoLoginRepository intentos,
            @Value("${seguridad.login.max-intentos:5}") int maximo,
            @Value("${seguridad.login.bloqueo-minutos:15}") long minutosDeBloqueo) {
        this.intentos = intentos;
        this.maximo = maximo;
        this.minutosDeBloqueo = minutosDeBloqueo;
    }

    /** Lanza DemasiadosIntentosException (429) si el correo está bloqueado en este momento. */
    @Transactional(readOnly = true)
    public void exigirNoBloqueado(String email) {
        IntentoLogin registro = intentos.findById(email).orElse(null);
        if (registro == null || registro.getBloqueadoHasta() == null) {
            return;
        }
        Instant ahora = Instant.now();
        if (registro.getBloqueadoHasta().isAfter(ahora)) {
            long segundos = Duration.between(ahora, registro.getBloqueadoHasta()).toSeconds();
            long minutos = Math.max(1, (segundos + 59) / 60);
            throw new DemasiadosIntentosException(
                    "Demasiados intentos fallidos. Intenta de nuevo en " + minutos + " minuto(s)");
        }
    }

    /** Suma un intento fallido. Devuelve true si con este intento el correo quedó bloqueado. */
    @Transactional
    public boolean registrarFallo(String email) {
        Instant ahora = Instant.now();
        IntentoLogin registro = intentos.findById(email).orElseGet(() -> new IntentoLogin(email));

        boolean bloqueoVencido = registro.getBloqueadoHasta() != null && !registro.getBloqueadoHasta().isAfter(ahora);
        Duration ventana = Duration.ofMinutes(Math.max(minutosDeBloqueo, 30));
        boolean intentosViejos = registro.getUltimoIntento().isBefore(ahora.minus(ventana));
        if (bloqueoVencido || intentosViejos) {
            registro.setIntentos(0);
            registro.setBloqueadoHasta(null);
        }

        registro.setIntentos(registro.getIntentos() + 1);
        registro.setUltimoIntento(ahora);
        boolean bloquea = registro.getIntentos() >= maximo;
        if (bloquea) {
            registro.setBloqueadoHasta(ahora.plus(Duration.ofMinutes(minutosDeBloqueo)));
        }
        intentos.save(registro);
        return bloquea;
    }

    /** Borra el contador (inicio de sesión correcto o contraseña recuperada). */
    @Transactional
    public void limpiar(String email) {
        if (intentos.existsById(email)) {
            intentos.deleteById(email);
        }
    }
}

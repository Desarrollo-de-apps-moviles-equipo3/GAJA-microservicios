package com.gaja.usuarios.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Envía correos por la API HTTP de Brevo (https://www.brevo.com). Se usa HTTP y no SMTP porque
 * Render bloquea los puertos SMTP en el plan gratis.
 * Variables de entorno: BREVO_API_KEY y MAIL_FROM (correo remitente verificado en Brevo).
 * Si no están, no se envía nada y solo queda un aviso en el log (el servicio sigue funcionando).
 */
@Service
public class CorreoService {

    private static final Logger log = LoggerFactory.getLogger(CorreoService.class);
    private static final URI API = URI.create("https://api.brevo.com/v3/smtp/email");

    private final String apiKey;
    private final String remitente;
    private final String nombreRemitente;
    private final boolean mostrarCodigoEnLog;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    public CorreoService(
            @Value("${gaja.mail.api-key:}") String apiKey,
            @Value("${gaja.mail.from:}") String remitente,
            @Value("${gaja.mail.from-name:GAJA}") String nombreRemitente,
            @Value("${gaja.mail.mostrar-codigo-en-log:false}") boolean mostrarCodigoEnLog) {
        this.apiKey = apiKey;
        this.remitente = remitente;
        this.nombreRemitente = nombreRemitente;
        this.mostrarCodigoEnLog = mostrarCodigoEnLog;
    }

    /** Envío en segundo plano: no hace esperar a la persona ni delata si el correo existe. */
    public void enviarCodigoRecuperacion(String para, String codigo) {
        if (mostrarCodigoEnLog) {
            // Solo para demostraciones sin proveedor de correo. Dejar en false en producción.
            log.warn("[SOLO DESARROLLO] Código de recuperación para {}: {}", para, codigo);
        }
        if (apiKey.isBlank() || remitente.isBlank()) {
            log.warn("Correo no configurado (BREVO_API_KEY / MAIL_FROM): no se envió el código de recuperación");
            return;
        }
        try {
            String texto = "Hola,\n\nTu código para recuperar la contraseña de GAJA es: " + codigo
                    + "\n\nVale por 30 minutos y solo se puede usar una vez."
                    + "\nSi no lo pediste tú, ignora este mensaje: tu contraseña no cambia.\n";
            Map<String, Object> cuerpo = Map.of(
                    "sender", Map.of("name", nombreRemitente, "email", remitente),
                    "to", List.of(Map.of("email", para)),
                    "subject", "Tu código para recuperar la contraseña",
                    "textContent", texto);
            HttpRequest peticion = HttpRequest.newBuilder(API)
                    .timeout(Duration.ofSeconds(15))
                    .header("api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .header("accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)))
                    .build();
            http.sendAsync(peticion, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(r -> {
                        if (r.statusCode() >= 300) {
                            log.warn("El proveedor de correo respondió con código {}", r.statusCode());
                        }
                    })
                    .exceptionally(e -> {
                        log.warn("No se pudo enviar el correo de recuperación ({})", e.getClass().getSimpleName());
                        return null;
                    });
        } catch (Exception e) {
            log.warn("No se pudo preparar el correo de recuperación ({})", e.getClass().getSimpleName());
        }
    }
}

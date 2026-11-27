package com.gaja.usuarios.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.gaja.usuarios.dto.*;
import com.gaja.usuarios.model.EstadoUsuario;
import com.gaja.usuarios.model.Sesion;
import com.gaja.usuarios.model.Usuario;
import com.gaja.usuarios.repo.SesionRepository;
import com.gaja.usuarios.repo.UsuarioRepository;
import com.gaja.usuarios.security.AuthUser;
import com.gaja.usuarios.security.JwtService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pruebas unitarias de UsuarioService: sin Spring ni base de datos, con los repositorios simulados.
 * Complementan a UsuarioApiTest, que prueba las mismas reglas a través de HTTP.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuarioServiceTest {

    @Mock
    UsuarioRepository usuarios;

    @Mock
    SesionRepository sesiones;

    PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    JwtService jwt = new JwtService("clave-solo-para-pruebas-0123456789-abcdefghij", 1);
    UsuarioService service;

    @BeforeEach
    void preparar() {
        service = new UsuarioService(usuarios, sesiones, encoder, jwt);
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(usuarios.existsByEmailIgnoreCase(any())).thenReturn(false);
    }

    // ---------- Ayudas ----------

    private Usuario usuario(long id, String email, String rol, EstadoUsuario estado) {
        Usuario u = new Usuario(email, encoder.encode("12345"), "Nombre " + id, rol, estado);
        ReflectionTestUtils.setField(u, "id", id);
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        when(usuarios.findByEmailIgnoreCase(email)).thenReturn(Optional.of(u));
        return u;
    }

    private RegistroRequest registro(String email, String rol) {
        return new RegistroRequest(email, "abcde", "Ana Pérez", rol, "  ", null);
    }

    // ---------- Login ----------

    @Test
    void login_correcto_devuelve_token_y_guarda_la_sesion() {
        usuario(1, "cadete@gaja.cl", "Cadete", EstadoUsuario.APROBADO);

        LoginResponse r = service.login(new LoginRequest("cadete@gaja.cl", "12345"));

        assertEquals("cadete@gaja.cl", r.email());
        assertEquals("APROBADO", r.estado());
        assertNotNull(r.token());
        // El token es un JWT firmado que identifica a la persona y su rol.
        AuthUser quien = jwt.validar(r.token()).orElseThrow();
        assertEquals(1L, quien.id());
        assertEquals("cadete@gaja.cl", quien.email());
        assertEquals("Cadete", quien.rol());
        ArgumentCaptor<Sesion> guardada = ArgumentCaptor.forClass(Sesion.class);
        verify(sesiones).save(guardada.capture());
        assertEquals(1L, guardada.getValue().getUsuarioId());
        // La base solo guarda el id de la sesión, no el token completo.
        assertFalse(guardada.getValue().getToken().isBlank());
        assertNotEquals(r.token(), guardada.getValue().getToken());
    }

    @Test
    void login_con_password_incorrecta_es_401_y_no_guarda_sesion() {
        usuario(1, "cadete@gaja.cl", "Cadete", EstadoUsuario.APROBADO);

        assertThrows(UnauthorizedException.class,
                () -> service.login(new LoginRequest("cadete@gaja.cl", "otra")));
        verify(sesiones, never()).save(any());
    }

    @Test
    void login_devuelve_el_rol_que_tiene_la_cuenta() {
        usuario(1, "oficial@gaja.cl", "Oficial", EstadoUsuario.APROBADO);

        LoginResponse r = service.login(new LoginRequest("oficial@gaja.cl", "12345"));

        assertEquals("Oficial", r.rol());
        verify(sesiones).save(any());
    }

    @Test
    void login_con_mala_clave_dice_correo_o_contrasena_incorrectos() {
        usuario(1, "cadete@gaja.cl", "Cadete", EstadoUsuario.APROBADO);

        UnauthorizedException e = assertThrows(UnauthorizedException.class,
                () -> service.login(new LoginRequest("cadete@gaja.cl", "mala")));
        assertEquals("Correo o contraseña incorrectos", e.getMessage());
    }

    @Test
    void login_con_correo_inexistente_da_el_mismo_mensaje_que_con_password_mala() {
        usuario(1, "cadete@gaja.cl", "Cadete", EstadoUsuario.APROBADO);
        when(usuarios.findByEmailIgnoreCase("nadie@gaja.cl")).thenReturn(Optional.empty());

        UnauthorizedException sinUsuario = assertThrows(UnauthorizedException.class,
                () -> service.login(new LoginRequest("nadie@gaja.cl", "12345")));
        UnauthorizedException passMala = assertThrows(UnauthorizedException.class,
                () -> service.login(new LoginRequest("cadete@gaja.cl", "mala")));

        assertEquals(passMala.getMessage(), sinUsuario.getMessage());
    }

    @Test
    void login_de_perfil_pendiente_es_403() {
        usuario(2, "nuevo@gaja.cl", "Cadete", EstadoUsuario.PENDIENTE);

        ForbiddenException e = assertThrows(ForbiddenException.class,
                () -> service.login(new LoginRequest("nuevo@gaja.cl", "12345")));
        assertTrue(e.getMessage().contains("pendiente"));
    }

    @Test
    void login_de_perfil_rechazado_incluye_el_motivo() {
        Usuario u = usuario(3, "rech@gaja.cl", "Cadete", EstadoUsuario.RECHAZADO);
        u.setMotivoRechazo("Datos incompletos");

        ForbiddenException e = assertThrows(ForbiddenException.class,
                () -> service.login(new LoginRequest("rech@gaja.cl", "12345")));
        assertTrue(e.getMessage().contains("Datos incompletos"));
    }

    @Test
    void login_de_perfil_desactivado_es_403() {
        usuario(4, "baja@gaja.cl", "Oficial", EstadoUsuario.DESACTIVADO);

        ForbiddenException e = assertThrows(ForbiddenException.class,
                () -> service.login(new LoginRequest("baja@gaja.cl", "12345")));
        assertTrue(e.getMessage().contains("desactivado"));
        verify(sesiones, never()).save(any());
    }

    // ---------- Alta ----------

    @Test
    void registrar_deja_el_perfil_pendiente_con_correo_en_minusculas_y_password_encriptada() {
        UsuarioResponse r = service.registrar(registro("  ANA@Gaja.cl ", "Cadete"));

        assertEquals("ana@gaja.cl", r.email());
        assertEquals("PENDIENTE", r.estado());
        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(guardado.capture());
        assertNotEquals("abcde", guardado.getValue().getPasswordHash());
        assertTrue(encoder.matches("abcde", guardado.getValue().getPasswordHash()));
        // Un teléfono en blanco se guarda como null.
        assertNull(guardado.getValue().getTelefono());
    }

    @Test
    void registrar_no_permite_crear_administradores() {
        assertThrows(IllegalArgumentException.class, () -> service.registrar(registro("a@gaja.cl", "Administrador")));
        verify(usuarios, never()).save(any());
    }

    @Test
    void registrar_una_institucion_externa_exige_el_nombre_de_la_institucion() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar(new RegistroRequest("ext@gaja.cl", "abcde", "Club Deportivo", "Institución externa", null, null)));
        verify(usuarios, never()).save(any());
    }

    @Test
    void registrar_rechaza_un_rol_que_no_existe() {
        assertThrows(IllegalArgumentException.class, () -> service.registrar(registro("a@gaja.cl", "Capitán")));
    }

    @Test
    void registrar_con_correo_repetido_es_conflicto() {
        when(usuarios.existsByEmailIgnoreCase("ana@gaja.cl")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.registrar(registro("ana@gaja.cl", "Cadete")));
        verify(usuarios, never()).save(any());
    }

    @Test
    void crear_como_admin_deja_el_perfil_aprobado_y_si_permite_administrador() {
        assertEquals("APROBADO", service.crearComoAdmin(registro("o@gaja.cl", "Oficial")).estado());
        assertEquals("Administrador", service.crearComoAdmin(registro("b@gaja.cl", "Administrador")).rol());
    }

    // ---------- Consulta ----------

    @Test
    void listar_elige_la_consulta_segun_los_filtros() {
        service.listar(null, null);
        verify(usuarios).findAllByOrderByNombreAsc();

        service.listar("pendiente", null);
        verify(usuarios).findByEstadoOrderByNombreAsc(EstadoUsuario.PENDIENTE);

        service.listar(null, "Cadete");
        verify(usuarios).findByRolOrderByNombreAsc("Cadete");

        service.listar(" aprobado ", "Oficial");
        verify(usuarios).findByEstadoAndRolOrderByNombreAsc(EstadoUsuario.APROBADO, "Oficial");
    }

    @Test
    void listar_con_un_estado_invalido_falla() {
        assertThrows(IllegalArgumentException.class, () -> service.listar("volando", null));
    }

    @Test
    void obtener_un_usuario_que_no_existe_es_404() {
        when(usuarios.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.obtener(99L));
        assertThrows(NotFoundException.class, () -> service.obtenerPorCorreo("nadie@gaja.cl"));
    }

    @Test
    void obtener_por_correo_ignora_los_espacios() {
        usuario(1, "cadete@gaja.cl", "Cadete", EstadoUsuario.APROBADO);

        assertEquals(1L, service.obtenerPorCorreo("  cadete@gaja.cl ").id());
    }

    // ---------- Edición ----------

    @Test
    void actualizar_limpia_los_textos_y_conserva_las_notificaciones_si_no_vienen() {
        Usuario u = usuario(1, "cadete@gaja.cl", "Cadete", EstadoUsuario.APROBADO);
        u.setNotifAlertas(false);

        UsuarioResponse r = service.actualizar(1L, new ActualizarRequest("  Nuevo Nombre ", " ", " +56911112222 ", null, null));

        assertEquals("Nuevo Nombre", r.nombre());
        assertNull(u.getTelefono());
        assertEquals("+56911112222", u.getInstitucion());
        assertFalse(u.isNotifAlertas());
        assertTrue(u.isNotifReservas());
    }

    @Test
    void actualizar_cambia_las_notificaciones_cuando_vienen() {
        Usuario u = usuario(1, "cadete@gaja.cl", "Cadete", EstadoUsuario.APROBADO);

        service.actualizar(1L, new ActualizarRequest("Ana", null, null, false, false));

        assertFalse(u.isNotifReservas());
        assertFalse(u.isNotifAlertas());
    }

    @Test
    void cambiar_password_con_la_actual_incorrecta_falla_y_no_cambia_nada() {
        Usuario u = usuario(1, "cadete@gaja.cl", "Cadete", EstadoUsuario.APROBADO);
        String antes = u.getPasswordHash();

        assertThrows(IllegalArgumentException.class,
                () -> service.cambiarPassword(1L, new CambiarPasswordRequest("mala", "nueva1")));
        assertEquals(antes, u.getPasswordHash());
    }

    @Test
    void cambiar_password_correcta_guarda_la_nueva_encriptada() {
        Usuario u = usuario(1, "cadete@gaja.cl", "Cadete", EstadoUsuario.APROBADO);

        service.cambiarPassword(1L, new CambiarPasswordRequest("12345", "nueva1"));

        assertTrue(encoder.matches("nueva1", u.getPasswordHash()));
        assertFalse(encoder.matches("12345", u.getPasswordHash()));
    }

    @Test
    void eliminar_borra_primero_las_sesiones_y_luego_el_usuario() {
        Usuario u = usuario(1, "cadete@gaja.cl", "Cadete", EstadoUsuario.APROBADO);

        service.eliminar(1L);

        var orden = inOrder(sesiones, usuarios);
        orden.verify(sesiones).deleteByUsuarioId(1L);
        orden.verify(usuarios).delete(u);
    }

    // ---------- Cambios de estado ----------

    @Test
    void aprobar_un_pendiente_lo_aprueba() {
        usuario(2, "nuevo@gaja.cl", "Cadete", EstadoUsuario.PENDIENTE);

        assertEquals("APROBADO", service.aprobar(2L).estado());
    }

    @Test
    void aprobar_un_perfil_que_no_esta_pendiente_es_conflicto() {
        usuario(1, "cadete@gaja.cl", "Cadete", EstadoUsuario.APROBADO);
        usuario(4, "baja@gaja.cl", "Oficial", EstadoUsuario.DESACTIVADO);

        assertThrows(ConflictException.class, () -> service.aprobar(1L));
        assertThrows(ConflictException.class, () -> service.aprobar(4L));
    }

    @Test
    void rechazar_guarda_el_motivo_sin_espacios() {
        usuario(2, "nuevo@gaja.cl", "Cadete", EstadoUsuario.PENDIENTE);

        UsuarioResponse r = service.rechazar(2L, "  No está en la nómina ");

        assertEquals("RECHAZADO", r.estado());
        assertEquals("No está en la nómina", r.motivoRechazo());
    }

    @Test
    void rechazar_solo_funciona_con_perfiles_pendientes() {
        usuario(1, "cadete@gaja.cl", "Cadete", EstadoUsuario.APROBADO);

        assertThrows(ConflictException.class, () -> service.rechazar(1L, "motivo"));
    }

    @Test
    void desactivar_solo_aprobados_y_activar_solo_desactivados() {
        usuario(1, "cadete@gaja.cl", "Cadete", EstadoUsuario.APROBADO);
        usuario(2, "nuevo@gaja.cl", "Cadete", EstadoUsuario.PENDIENTE);
        usuario(4, "baja@gaja.cl", "Oficial", EstadoUsuario.DESACTIVADO);

        assertEquals("DESACTIVADO", service.desactivar(1L).estado());
        assertEquals("APROBADO", service.activar(4L).estado());
        assertThrows(ConflictException.class, () -> service.desactivar(2L));
        assertThrows(ConflictException.class, () -> service.activar(2L));
    }
}

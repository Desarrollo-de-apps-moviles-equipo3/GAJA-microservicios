# usuarios-service

Microservicio de usuarios, perfiles, roles y sesiones de GAJA. Spring Boot 3.3 + Java 17 + Supabase (PostgreSQL), puerto **8081**.

## Cómo levantarlo

1. En Supabase (SQL Editor) ejecuta una vez `sql/schema.sql` para crear el esquema `usuarios`.
2. Define las variables de entorno (las mismas de los otros servicios): `SUPABASE_DB_URL` (debe empezar con `jdbc:postgresql://`), `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`. Opcional: `DB_SCHEMA` (por defecto `usuarios`).
3. Ejecuta `UsuariosApplication`. La primera vez crea las tablas y carga los usuarios de prueba (contraseña de todos: `12345`).

Pruebas (no usan Supabase, corren sobre H2): `mvn test`.

## Perfiles y estados

Un perfil nace `PENDIENTE` (registro público) o `APROBADO` (lo crea el administrador). El administrador puede aprobar o rechazar (con motivo) un pendiente, y desactivar o activar uno aprobado. Solo un perfil `APROBADO` puede iniciar sesión.

Roles: `Cadete`, `Oficial`, `Oficial de semana`, `Institución externa`, `Administrador`. El registro público no permite crear administradores.

## Endpoints (`/api/usuarios`)

| Método | Ruta | Qué hace |
| --- | --- | --- |
| POST | `/login` | Valida correo, contraseña y rol. 401 si no coinciden; 403 si el perfil está pendiente, rechazado o desactivado. Guarda la sesión |
| POST | `/registro` | Registro público, queda `PENDIENTE` |
| POST | `/` | El administrador crea un usuario, queda `APROBADO` |
| GET | `/?estado=&rol=` | Lista, con filtros opcionales |
| GET | `/por-correo?email=` | Perfil de la persona con ese correo (la app lo usa con el correo de la sesión) |
| GET | `/{id}` | Un perfil |
| PUT | `/{id}` | Edita nombre, teléfono, institución y notificaciones |
| PATCH | `/{id}/password` | Cambia la contraseña (pide la actual) |
| PATCH | `/{id}/aprobar` · `/rechazar` · `/desactivar` · `/activar` | Cambios de estado (`rechazar` recibe `{"motivo": "..."}`) |
| GET | `/{id}/sesiones` | Registro de sesiones, la más reciente primero |
| DELETE | `/{id}` | Elimina el usuario y sus sesiones |

Los errores salen como `{"error": "mensaje"}`. Las contraseñas se guardan con hash BCrypt y nunca se devuelven.

## Usuarios de prueba (contraseña `12345`)

| Correo | Rol | Estado |
| --- | --- | --- |
| admin@gaja.cl | Administrador | APROBADO |
| cadete@gaja.cl | Cadete | APROBADO |
| oficial@gaja.cl | Oficial | APROBADO |
| oficial.semana@gaja.cl | Oficial de semana | APROBADO |
| externo@gaja.cl | Institución externa | APROBADO |
| cadete.nuevo@gaja.cl | Cadete | PENDIENTE |
| externo.nuevo@gaja.cl | Institución externa | PENDIENTE |
| cadete.rechazado@gaja.cl | Cadete | RECHAZADO |
| oficial.baja@gaja.cl | Oficial | DESACTIVADO |

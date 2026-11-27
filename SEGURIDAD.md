# Seguridad de los microservicios

Los tres servicios (usuarios, espacios y reservas) validan un **token JWT** en cada petición a `/api/**`.

## Cómo funciona
1. `POST /api/usuarios/login` revisa correo y contraseña y devuelve un **token firmado** (dura 12 horas por defecto).
2. La app lo manda en cada petición: `Authorization: Bearer <token>`.
3. Cada servicio valida la firma con la **misma clave** (`JWT_SECRET`), sin llamar al servicio de usuarios.
4. El token lleva el correo, el id y el rol de la persona. Si falta, está alterado o venció, responde **401**. Si la persona no tiene permiso, responde **403**.

## Reglas
| Marca en el controlador | Quién puede |
|---|---|
| `@Publico` | Cualquiera, sin token (solo login y registro) |
| *(sin marca)* | Cualquier persona con sesión iniciada |
| `@RequiereAdmin` | Solo rol Administrador |
| `AuthContext.exigirMismoCorreoOAdmin(email)` / `...IdOAdmin(id)` | Cada persona solo sus propios datos; el administrador, los de todos |

**Un endpoint nuevo queda protegido por defecto.** Si debe ser público, hay que ponerle `@Publico` a propósito.

### Qué es solo de administrador
- Usuarios: crear, listar, aprobar, rechazar, activar, desactivar y eliminar.
- Espacios: crear, editar, cambiar disponibilidad y eliminar; crear y borrar bloqueos; crear, renombrar y eliminar categorías.
- Reservas: listar todas, cambiar el estado y editar las reglas de reserva.
- Incidencias: listar todas, contar pendientes y resolver.

### Datos de cada persona (solo las propias, o el administrador)
Perfil, cambio de contraseña y sesiones (`/api/usuarios/...`), reservas (crear, listar, cancelar, ocultar), alertas e incidencias propias.

## Configuración
Variable de entorno **`JWT_SECRET`** en los **tres** servicios, con el **mismo valor**, de al menos 32 caracteres.
Sin ella, el servicio no arranca (a propósito). Opcional: `JWT_EXPIRATION_HOURS` (por defecto 12).

Generar una clave (PowerShell):
```
$b = New-Object byte[] 48; [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```
La clave **nunca** va en el código ni en el repositorio: solo como variable de entorno (igual que las de Supabase).

## En Render
1. En **cada** servicio (usuarios, espacios y reservas): *Environment* → *Add Environment Variable* → `JWT_SECRET` con el **mismo valor** en los tres.
   Agregar la variable antes de subir el código es seguro: el servicio se reinicia con el código actual y no pasa nada.
2. Después se sube la seguridad a `main`. Render construye cada servicio con el `Dockerfile` (`mvn package -DskipTests`): los tests **no se ejecutan** en Render, pero el código (incluidos los tests) **sí tiene que compilar**. Por eso conviene correr `mvn test` en el computador antes de subir.
3. Las direcciones para despertar los servicios (`/api/usuarios`, `/api/espacios`, `/api/reservas/reglas`) ahora responden **401** en el navegador. Es normal: significa que el servicio está despierto.
4. Después del cambio hay que generar un APK nuevo (el anterior no manda token) y todas las personas deben iniciar sesión de nuevo.

## Límites conocidos
- Un token sigue valiendo hasta que vence aunque después desactiven a la persona (máximo 12 horas).
- No hay "refrescar token": al vencer, la app pide iniciar sesión de nuevo.
- El paquete `security` está copiado en cada servicio porque son proyectos Maven separados.

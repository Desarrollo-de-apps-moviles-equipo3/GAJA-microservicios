# Guía paso a paso: G1, microservicio de reservas

Esta guía explica la estructura que se creó para `reservas-service`, qué hace cada archivo y cómo se conectan sus partes. La app Android está escrita en Kotlin; este microservicio usa Java y Spring Boot, igual que `espacios-service`.

## 1. Cómo se organiza

Una solicitud de reserva recorre estas capas:

```text
Android
  → Controller: recibe una ruta HTTP y los datos JSON
  → DTO: valida la forma de los datos recibidos
  → Service: aplica las reglas de reserva
  → Repository: consulta o guarda mediante JPA
  → Supabase PostgreSQL: conserva los datos
```

Las entidades (`Reserva`, `Alerta`, `Incidencia`) representan los datos guardados. Los DTO representan los datos que la API recibe del usuario.

## 2. Archivos y carpetas, en el orden de creación

### Configuración y arranque

- `pom.xml`: identifica el proyecto Maven y sus dependencias. `spring-boot-starter-web` crea la API HTTP; `spring-boot-starter-data-jpa` conecta entidades con PostgreSQL; `spring-boot-starter-validation` valida solicitudes; el driver `postgresql` permite conectarse a Supabase. El proyecto apunta a Java 17.
- `src/main/java/com/gaja/reservas/ReservasApplication.java`: punto de entrada que inicia Spring Boot.
- `src/main/resources/application.properties`: indica el puerto `8083`, carga las credenciales desde variables de entorno y configura el esquema `reservas`. `ddl-auto=update` permite que Hibernate actualice las tablas según las entidades; `maximum-pool-size=3` limita las conexiones del servicio y `open-in-view=false` evita mantener la sesión de base de datos abierta durante toda la respuesta. Las contraseñas no deben escribirse en este archivo ni subirse a GitHub.

### Modelos (`model`)

- `EstadoReserva.java`: estados permitidos: `PENDIENTE`, `APROBADA`, `RECHAZADA` y `CANCELADA`.
- `Reserva.java`: entidad JPA para una reserva. Guarda usuario, cancha, fecha, horario, motivo, estado y fecha de creación.
- `Alerta.java`: entidad para un mensaje asociado a una reserva. Guarda destinatario, mensaje, fecha y si fue leída.
- `EstadoIncidencia.java`: estados de una incidencia: `PENDIENTE` y `RESUELTA`.
- `Incidencia.java`: entidad para un reporte de problema en una cancha; guarda usuario, cancha, descripción, estado y fecha.

Las anotaciones `@Entity`, `@Id` y `@Column` indican a JPA cómo guardar los campos como tablas y columnas. `@PrePersist` asigna automáticamente la fecha de creación.

### Datos recibidos (`dto`)

- `CrearReservaRequest.java`: campos que Android envía para pedir una reserva y validaciones como correo, cancha, fecha, horario y motivo obligatorio.
- `ActualizarEstadoRequest.java`: estado que una acción administrativa solicita aplicar. La persona usuaria no escribe este valor; los botones de aprobar o rechazar lo enviarán.
- `CrearIncidenciaRequest.java`: datos para informar un problema en una cancha, con validaciones de correo, cancha y descripción.

Separar DTO de entidad evita que el cliente pueda decidir campos que controla el servidor, como el identificador, el estado inicial o la fecha de creación.

### Consultas a la base (`repo`)

- `ReservaRepository.java`: busca reservas por usuario, fecha, cancha y horario; cuenta las reservas activas del día y busca por ID junto con el correo del usuario para cancelar solo una reserva propia.
- `AlertaRepository.java`: lista las alertas de un usuario, cuenta las no leídas y busca una alerta por ID y usuario.
- `IncidenciaRepository.java`: lista incidencias de una persona o todas para administración y cuenta las pendientes.

Cada interfaz extiende `JpaRepository`; Spring Data genera las consultas a partir del nombre de los métodos, por ejemplo `findByUsuarioEmailOrderByFechaAsc`.

### Reglas del sistema (`service`)

- `ReservaService.java`: crea reservas, comprueba disponibilidad, lista reservas, cancela y cambia el estado.
- `AlertaService.java`: crea, lista y cuenta alertas, y permite marcarlas como leídas.
- `IncidenciaService.java`: crea reportes, los lista y permite marcarlos como resueltos.

Los servicios llevan la lógica del negocio para que los controllers se ocupen de recibir y responder solicitudes. `@Transactional` agrupa las operaciones de base de datos; `readOnly = true` marca consultas.

### Rutas HTTP (`web`)

- `ReservaController.java`: expone las rutas de reservas.
- `AlertaController.java`: expone las rutas de alertas.
- `IncidenciaController.java`: expone las rutas de incidencias.
- `ReservaExceptionHandler.java`: convierte errores de validación y reglas en respuestas HTTP: `400` para datos inválidos y `409` para conflictos de reglas. Los elementos no encontrados usan `404` mediante `ResponseStatusException`.

## 3. Reglas implementadas actualmente

En `ReservaService.java`:

1. No acepta fechas anteriores a hoy, usando la zona `America/Santiago`.
2. **Actualmente aún limita las reservas a siete días hacia adelante** mediante `MAX_DIAS_ADELANTE = 7`.
3. Permite como máximo tres reservas activas por día. La consulta cuenta globalmente las reservas `PENDIENTE` y `APROBADA`, siguiendo el código de Android existente.
4. Impide reservar una cancha, fecha y horario que ya estén ocupados por una reserva activa.
5. Una reserva nueva queda `PENDIENTE` y genera una alerta.
6. Solo permite cancelar reservas pendientes o aprobadas y hasta dos horas antes de su inicio. Cambia el estado a `CANCELADA` en vez de borrar el registro y genera una alerta.
7. Solo permite aprobar o rechazar solicitudes que sigan pendientes; el cambio genera una alerta.

Los horarios ocupados se devuelven como una lista de textos para que Android pueda deshabilitar esos horarios. Las alertas de esta versión se guardan en la base; todavía no son notificaciones push del teléfono.

**Cambio acordado pendiente:** se pidió permitir cualquier fecha futura, bloqueando solo las pasadas y manteniendo el máximo de tres reservas diarias. El código actual todavía aplica siete días. Hay que cambiar esta regla en el microservicio y alinear la validación/calendario Android antes de considerar completada esa parte de G1.

## 4. Rutas disponibles

| Método | Ruta | Uso |
|---|---|---|
| `POST` | `/api/reservas` | Crear una solicitud de reserva |
| `GET` | `/api/reservas/usuario?email=...` | Listar reservas de un usuario |
| `GET` | `/api/reservas/disponibilidad?fecha=AAAA-MM-DD&espacio=...` | Obtener horarios ocupados |
| `PATCH` | `/api/reservas/{id}/cancelar?email=...` | Cancelar una reserva propia |
| `PATCH` | `/api/reservas/{id}/estado` | Aprobar o rechazar una solicitud pendiente |
| `GET` | `/api/alertas?email=...` | Listar alertas de un usuario |
| `GET` | `/api/alertas/no-leidas?email=...` | Contar alertas no leídas |
| `PATCH` | `/api/alertas/{id}/leida?email=...` | Marcar alerta como leída |
| `POST` | `/api/incidencias` | Informar un problema de una cancha |
| `GET` | `/api/incidencias/usuario?email=...` | Listar incidencias de una persona |
| `GET` | `/api/incidencias` | Listar incidencias para administración |
| `GET` | `/api/incidencias/pendientes/count` | Contar incidencias pendientes |
| `PATCH` | `/api/incidencias/{id}/resolver` | Marcar una incidencia como resuelta |

Las fechas de la API usan ISO, como `2026-11-25`. Una solicitud de reserva recibe JSON con `usuarioEmail`, `espacio`, `fecha`, `horario` y `motivo`.

En esta versión todavía no hay autenticación/autorización de Spring Security en estas rutas. En particular, las rutas de administración de incidencias deben protegerse al integrar roles antes de publicar el servicio.

## 5. Supabase y ejecución local

El esquema `reservas` debe existir en Supabase. Se creó con:

```sql
CREATE SCHEMA IF NOT EXISTS reservas;
```

`application.properties` espera estas variables de entorno:

```text
SUPABASE_DB_URL
SUPABASE_DB_USER
SUPABASE_DB_PASSWORD
DB_SCHEMA
```

Se configuran en la terminal de PowerShell antes de ejecutar el servicio. No guardar la contraseña en el repositorio ni incluirla en esta guía. Para usar la conexión JDBC, la URL comienza con `jdbc:postgresql://`.

Desde la carpeta `reservas-service`, los comandos son:

```powershell
mvn compile
mvn spring-boot:run
```

`mvn compile` compila; `mvn spring-boot:run` inicia la API en `http://localhost:8083`.

## 6. Comprobaciones realizadas

- `mvn compile` terminó con `BUILD SUCCESS` y compiló 19 archivos fuente con Java 17.
- `mvn spring-boot:run` inició Spring Boot, abrió una conexión PostgreSQL con Supabase y dejó Tomcat en el puerto `8083`.
- `GET /api/reservas/usuario?email=cadete%40gaja.cl` respondió `[]`. Esto significa que la ruta y consulta funcionaron, pero aún no hay reservas para ese correo.

Las pruebas detalladas de creación, cancelación, cambios de estado y validaciones corresponden al punto G7.

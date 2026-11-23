# espacios-service

Microservicio de **espacios, categorías y bloqueos de horario** de GAJA (N1). Spring Boot 3 + PostgreSQL (Supabase).
La app nunca habla con Supabase: solo con este servicio.

Corre en `http://localhost:8082`. Desde el emulador Android: `http://10.0.2.2:8082`.
Pruebas: `mvn test` (usan H2 en memoria, no necesitan Supabase).

## Endpoints

| Método | Ruta | Para qué |
|---|---|---|
| GET | `/api/espacios?categoria=Deportes&disponible=true` | Lista (filtros opcionales) |
| GET | `/api/espacios/{id}` | Un espacio |
| POST | `/api/espacios` | Crear (201). Si no mandas `id`, se genera desde el nombre |
| PUT | `/api/espacios/{id}` | Editar |
| PATCH | `/api/espacios/{id}/disponibilidad` | Deshabilitar o habilitar: `{"available": false}` |
| DELETE | `/api/espacios/{id}` | Eliminar (borra también sus bloqueos) |
| GET | `/api/categorias` | Lista de categorías |
| POST | `/api/categorias` | Crear: `{"name": "Piscinas"}` |
| PUT | `/api/categorias/{id}` | Renombrar |
| DELETE | `/api/categorias/{id}` | Eliminar (409 si tiene espacios) |
| GET | `/api/espacios/{id}/bloqueos?fecha=2026-11-25` | Bloqueos de un espacio (fecha opcional) |
| POST | `/api/espacios/{id}/bloqueos` | Bloquear un horario (201; 409 si ya está bloqueado) |
| GET | `/api/bloqueos?fecha=2026-11-25` | Todos los bloqueos de un día |
| DELETE | `/api/bloqueos/{id}` | Quitar un bloqueo |

Errores: siempre `{"error": "mensaje"}` con 400, 404 o 409.

## JSON (acordado con la app)

Espacio: **mismos campos que `Space.kt`**, más `categoryId`:

```json
{
  "id": "futbol-a",
  "name": "Cancha de Fútbol A",
  "category": "Deportes",
  "categoryId": 1,
  "description": "Cancha de fútbol 7 de pasto sintético para entrenamientos y partidos.",
  "capacity": 14,
  "available": true,
  "colorHex": "#2F6B4F",
  "tags": ["Fútbol"],
  "latitude": -33.4042,
  "longitude": -70.5781
}
```

Crear/editar espacio (`POST`/`PUT`): `name`, `category` (nombre de una categoría existente) y `capacity` son obligatorios.

Bloqueo (`timeSlot` usa el mismo formato de `ReservationValidator.TIME_SLOTS`, así Reservas lo compara directo):

```json
{ "id": 1, "spaceId": "tenis-a", "date": "2026-11-25", "timeSlot": "08:00 - 10:00", "reason": "Mantención" }
```

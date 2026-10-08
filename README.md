# BancoIAS · Uso de cupos preaprobados

Solución Full Stack mínima para utilizar cupos preaprobados conservando un estado consistente ante solicitudes simultáneas y referencias repetidas.

**Stack:** Java 21 · Spring Boot 4 WebFlux · R2DBC · PostgreSQL 16 · Angular · Docker · Testcontainers

---

## Ejecución rápida (recomendada)

Requisito: **Docker Desktop** en ejecución.

```bash
docker compose up --build -d
```

| Servicio | URL |
|---|---|
| Interfaz Angular | http://localhost:4200 |
| API REST | http://localhost:8080/api/usage-requests |
| Consola RabbitMQ | http://localhost:15672 (guest / guest) |

Para detener todo y **reiniciar los datos semilla**:

```bash
docker compose down -v
```

> Las credenciales del `docker-compose.yml` (`bancoias/bancoias`, `guest/guest`) son exclusivamente para el entorno local de la prueba. No se usan credenciales reales.

---

## Ejecutar las pruebas automatizadas

Requisitos: **Java 21** y **Docker** en ejecución (las pruebas de integración levantan un PostgreSQL temporal con Testcontainers).

```bash
cd backend
./mvnw test          # Windows CMD/PowerShell: mvnw.cmd test
```

Resultado esperado: `Tests run: 30, Failures: 0, Errors: 0`.

| Suite | Pruebas | Qué demuestra |
|---|---|---|
| `UsagePolicyTest` | 9 | Reglas de negocio de RF02 (unitarias, sin infraestructura) |
| `UsageRequestServiceIntegrationTest` | 7 | Persistencia, **concurrencia (10 solicitudes simultáneas)** e **idempotencia** contra PostgreSQL real |
| `UsageRequestControllerIntegrationTest` | 7 | Contrato HTTP: 201, 200, 409, 400, 404 |
| `PreapprovedApplicationTests` | 1 | Arranque del contexto |
| `OutboxIntegrationTest` | 4 | El evento se escribe en la misma transacción; rechazos, reintentos y carreras no generan eventos extra |
| `RabbitOutboxIntegrationTest` | 2 | Publicación real en RabbitMQ y consumidor que descarta duplicados |
---

## Desarrollo local (sin contenerizar backend y frontend)

```bash
# 1. Infraestructura
docker compose up -d postgres rabbitmq

# 2. Backend (terminal 1)
cd backend
./mvnw spring-boot:run

# 3. Frontend (terminal 2) — requiere Node 20+
cd frontend
npm install
npm start
```

---

## API

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| `POST` | `/api/usage-requests` | Procesa una solicitud de uso | `201` nueva (autorizada o rechazada) · `200` reintento de referencia ya procesada · `409` misma referencia con datos diferentes · `400` petición mal formada |
| `GET` | `/api/usage-requests/{requestReference}` | Consulta una solicitud | `200` · `404` |
| `GET` | `/api/usage-requests?limit=20` | Solicitudes recientes (máx. 100) | `200` |

Ejemplo:

```bash
curl -X POST http://localhost:8080/api/usage-requests \
  -H "Content-Type: application/json" \
  -d '{"requestReference":"REF-001","preApprovedId":"PRA-1001","customerId":"USR-10","amount":600000}'
```

Una solicitud **rechazada por regla de negocio** (monto ≤ 0, preaprobado inexistente, cliente distinto, bloqueado o cupo insuficiente) **se procesa y se registra** con `status: REJECTED` y su razón; no es un error HTTP.

### Datos semilla

| Preaprobado | Cliente | Estado | Cupo (COP) |
|---|---|---|---|
| PRA-1001 | USR-10 | ACTIVE | 1.000.000 |
| PRA-1002 | USR-10 | BLOCKED | 800.000 |
| PRA-2001 | USR-20 | ACTIVE | 2.000.000 |

---

## Estado del alcance

| ID | Requisito | Estado | Evidencia |
|---|---|---|---|
| RF01 | Procesar solicitud y registrar fecha/hora | ✅ | `UsageRequestService`, `POST /api/usage-requests` |
| RF02 | Validar reglas de negocio | ✅ | `UsagePolicy` + `UsagePolicyTest` |
| RF03 | Conservar el resultado; el rechazo no descuenta | ✅ | Tabla `usage_request`, prueba `rejectedRequestIsPersistedAndDoesNotDecrease` |
| RF04 | Solicitudes simultáneas | ✅ | UPDATE condicional atómico, prueba `concurrentRequestsNeverExceedAvailableAmount` |
| RF05 | Referencias repetidas | ✅ | UNIQUE + hash del payload, pruebas de reintento, carrera y conflicto |
| RF06 | Consultar por referencia y recientes | ✅ | `GET` endpoints + pruebas |
| RF07 | Interfaz Angular | ✅ | `frontend/` integrada vía proxy (dev) y nginx (Docker) |
| Opcional | RabbitMQ | ✅ | Outbox + publicador con confirmación + consumidor idempotente + DLQ |

---

## Arquitectura

Arquitectura hexagonal ligera: el dominio no depende de Spring ni de la base de datos.

```
backend/src/main/java/com/bancoias/preapproved/
├── domain/
│   ├── model/        # PreApproved, UsageRequest, UsageRequestCommand, enums
│   ├── service/      # UsagePolicy (reglas RF02)
│   ├── port/         # Interfaces de repositorio
│   └── exception/    # RequestReferenceConflictException
├── application/      # UsageRequestService (caso de uso transaccional)
└── infrastructure/
    ├── persistence/  # Adaptadores R2DBC (SQL explícito)
    ├── web/          # Controller, DTOs, manejo de errores
    └── config/       # Beans, transacciones, CORS
```

### Decisiones clave (detalle en [`docs/DECISIONS.md`](docs/DECISIONS.md))

- **Concurrencia:** el cupo se descuenta con un único `UPDATE ... WHERE available_amount >= :amount`. La comparación y la resta ocurren de forma atómica en PostgreSQL; un `CHECK (available_amount >= 0)` actúa como segunda defensa.
- **Idempotencia:** restricción `UNIQUE` sobre `request_reference` y huella SHA-256 del contenido. Misma huella devuelve el resultado original; huella distinta devuelve `409` con la solicitud original.
- **Transacción:** descuento y registro ocurren en la misma transacción reactiva; si el registro falla, el descuento se revierte.

---

## Limitaciones y alcance pendiente

- **RabbitMQ (opcional):** infraestructura disponible en `docker-compose`; la publicación de eventos se documenta en `docs/DECISIONS.md`.
- **Frontend sin pruebas automatizadas:** las reglas de negocio viven y se prueban en el backend; la UI se validó manualmente con los escenarios de RF01–RF07.
- **Sin autenticación ni administración de preaprobados:** fuera del alcance definido por el enunciado.
- **Consulta de recientes sin paginación:** límite máximo de 100 registros.
- Los datos persisten entre reinicios del backend; para volver al estado inicial se usa `docker compose down -v`.
- **Outbox con varias instancias:** dos instancias del backend podrían publicar el mismo evento; el consumidor lo tolera porque descarta duplicados por `eventId`. La mejora sería `SELECT ... FOR UPDATE SKIP LOCKED`.
- **Sin limpieza del outbox:** los eventos publicados permanecen en la tabla; en producción se depurarían periódicamente.

---

## RabbitMQ (punto opcional)

Cuando una solicitud se autoriza, se publica el evento `PreApprovedUsageAuthorized`.

```
Autorización ──(misma transacción)──► outbox_event
                                          │
                    OutboxPublisher (cada 2 s, con publisher confirms)
                                          ▼
                 exchange topic: preapproved.events
                 routing key:    preapproved.usage.authorized
                                          ▼
         cola preapproved.usage.authorized.audit ──► consumidor idempotente
                                          │ tras 3 intentos fallidos
                                          ▼
         cola preapproved.usage.authorized.audit.dlq
```

Para verlo: envía una solicitud autorizada y revisa `docker compose logs backend | grep -i evento`, o la consola en http://localhost:15672.

## Uso de inteligencia artificial

Ver [`docs/AI_USAGE.md`](docs/AI_USAGE.md).
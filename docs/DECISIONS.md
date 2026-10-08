# Registro de decisiones técnicas

Cada decisión sigue el formato: contexto, alternativas, decisión, trade-offs y validación.

---

## ADR-001 · Arquitectura hexagonal ligera

**Contexto.** Las reglas del core de preaprobados deben poder explicarse y probarse sin depender de la base de datos ni del framework.

**Alternativas.**
- Arquitectura en capas tradicional (controller → service → repository con entidades JPA/R2DBC).
- Hexagonal completa con módulos Maven separados.
- Hexagonal ligera en un solo módulo.

**Decisión.** Hexagonal ligera: `domain` (modelos, reglas, puertos), `application` (caso de uso) e `infrastructure` (adaptadores R2DBC, web, configuración). El dominio no tiene anotaciones de Spring.

**Trade-offs.** Más archivos que una arquitectura en capas simple. Se descartaron módulos Maven separados por el tiempo estimado de la prueba.

**Validación.** `UsagePolicyTest` prueba las reglas de negocio sin levantar Spring ni base de datos.

---

## ADR-002 · PostgreSQL con R2DBC y SQL explícito

**Contexto.** El backend es WebFlux (no bloqueante) y las solicitudes procesadas deben persistirse.

**Alternativas.**
- H2 en memoria: simple, pero no persiste entre reinicios y su comportamiento de bloqueo difiere del de un motor productivo.
- PostgreSQL con JDBC: bloquearía los hilos del event loop.
- PostgreSQL con R2DBC usando repositorios derivados de Spring Data.
- PostgreSQL con R2DBC usando `DatabaseClient` y SQL explícito.

**Decisión.** PostgreSQL 16 con R2DBC y `DatabaseClient`.

**Trade-offs.** Más código de mapeo que con repositorios derivados, a cambio de control total sobre la sentencia crítica de concurrencia (ADR-003), que no se expresa bien con métodos derivados.

**Validación.** Pruebas de integración con Testcontainers contra un PostgreSQL real.

---

## ADR-003 · Concurrencia mediante UPDATE condicional atómico (RF04)

**Contexto.** Dos solicitudes simultáneas sobre el mismo preaprobado no deben poder autorizar en total más que el cupo disponible. El patrón "leer cupo → comparar en Java → escribir" tiene una condición de carrera entre la lectura y la escritura.

**Alternativas.**
- Bloqueo en memoria (`synchronized`, `ReentrantLock`): no funciona con varias instancias y bloquea hilos en un entorno reactivo.
- Bloqueo pesimista `SELECT ... FOR UPDATE`: correcto, pero requiere dos sentencias y mantiene el bloqueo más tiempo.
- Bloqueo optimista con columna `version` y reintentos: correcto, pero agrega lógica de reintento y degrada bajo alta contención sobre un mismo preaprobado.
- UPDATE condicional atómico.

**Decisión.**

```sql
UPDATE pre_approved
SET available_amount = available_amount - :amount
WHERE id = :id AND customer_id = :customerId
  AND status = 'ACTIVE' AND available_amount >= :amount
```

Una fila afectada indica autorización; cero filas, rechazo por cupo insuficiente. PostgreSQL bloquea la fila durante la actualización, así que las solicitudes concurrentes se serializan y cada una evalúa el cupo ya actualizado. Como segunda defensa, la tabla tiene `CHECK (available_amount >= 0)`.

**Trade-offs.** La garantía depende de la base de datos, lo cual es deseable: funciona igual con una o con varias instancias del backend. La validación previa en `UsagePolicy` se hace sobre una lectura que puede quedar desactualizada; por eso es solo una verificación temprana y la decisión final la toma el UPDATE.

**Validación.** `concurrentRequestsNeverExceedAvailableAmount`: 10 solicitudes paralelas de 200.000 sobre un cupo de 1.000.000 producen exactamente 5 autorizadas, 5 rechazadas y cupo final 0.

---

## ADR-004 · Idempotencia por referencia con huella del contenido (RF05)

**Contexto.** Una `requestReference` puede llegar varias veces (reintentos de canal). No debe descontarse dos veces, y si llega con datos diferentes debe manejarse de forma controlada preservando la original.

**Alternativas.**
- Solo consultar antes de procesar: falla si dos reintentos llegan en el mismo instante.
- Solo restricción UNIQUE: no distingue un reintento legítimo de un uso indebido de la referencia.
- Tabla de idempotencia separada con expiración: más compleja de lo que el alcance requiere.
- UNIQUE más huella SHA-256 del contenido.

**Decisión.**
- `UNIQUE (request_reference)` en `usage_request`.
- Huella SHA-256 de `requestReference | preApprovedId | customerId | amount` (monto normalizado).
- Flujo: si la referencia existe y la huella coincide, se devuelve la original (`200`, `replayed: true`). Si la huella difiere, `409 Conflict` con la solicitud original en el cuerpo.
- Si dos solicitudes con la misma referencia pasan la consulta inicial, la UNIQUE rechaza la segunda; su transacción hace rollback (incluido el descuento) y se responde con la original.

**Trade-offs.** Las referencias se consideran únicas de forma global y permanente, sin expiración. Una referencia rechazada también queda "consumida": reenviarla devuelve el mismo rechazo (ver supuestos).

**Validación.** Pruebas `repeatedReferenceReturnsOriginalResult`, `concurrentRepeatedReferenceIsProcessedOnce` (5 envíos simultáneos con la misma referencia: un solo descuento) y `sameReferenceWithDifferentDataIsRejectedAsConflict`.

---

## ADR-005 · Transacción reactiva y orden descuento → registro

**Contexto.** El descuento del cupo y el registro de la solicitud deben ser consistentes entre sí.

**Decisión.** Ambas operaciones se ejecutan dentro de `TransactionalOperator`. Primero se descuenta y luego se registra; si el registro falla (por ejemplo, por la UNIQUE), la transacción revierte el descuento.

**Trade-offs.** La fila del preaprobado permanece bloqueada hasta el commit, lo que serializa las solicitudes sobre un mismo preaprobado. Es aceptable porque la operación es corta y la consistencia es prioritaria.

**Validación.** La prueba de referencia repetida concurrente verifica que el cupo final refleja un único descuento.

---

## ADR-006 · Rechazos de negocio como resultado registrado, no como error HTTP

**Contexto.** RF03 exige conservar toda solicitud procesada, incluidas las rechazadas.

**Alternativas.** Responder 4xx para reglas de negocio no cumplidas, o tratarlas como un resultado válido del procesamiento.

**Decisión.** Una solicitud que incumple una regla de negocio (monto ≤ 0, preaprobado inexistente, cliente distinto, bloqueado o cupo insuficiente) se registra con `status: REJECTED` y su razón, y responde `201`. Los `4xx` quedan para peticiones mal formadas (`400`) y conflictos de referencia (`409`).

**Trade-offs.** Un cliente HTTP debe revisar el campo `status`, no solo el código de respuesta. Se documenta en el README.

**Validación.** `zeroAmountIsProcessedAsRejected` y `rejectedRequestIsPersistedAndDoesNotDecrease`.

---

## ADR-007 · Normalización de monto y fecha

**Contexto.** En pruebas manuales se detectó que la respuesta original y la de un reintento diferían: `600000` frente a `600000.00`, y una fecha con nanosegundos frente a la misma con microsegundos. La respuesta original se construía en memoria y la del reintento desde PostgreSQL (`NUMERIC(15,2)` y `TIMESTAMPTZ` con microsegundos).

**Decisión.** Normalizar en la entrada: monto con escala 2 (`@Digits(fraction = 2)` garantiza que no hay redondeo) y fecha de procesamiento truncada a microsegundos.

**Validación.** Repetición manual de los `curl` de reintento: ambas respuestas idénticas. Suite completa en verde.

---

## ADR-008 · Estrategia de pruebas

**Decisión.**
- Unitarias para las reglas de negocio (rápidas, sin infraestructura).
- Integración con Testcontainers (PostgreSQL real) para concurrencia, idempotencia y transacciones, porque estos comportamientos dependen del motor de base de datos y no se pueden demostrar con mocks.
- Integración HTTP con `WebTestClient` para el contrato de la API.

**Trade-offs.** Las pruebas de integración requieren Docker. El frontend no tiene pruebas automatizadas: las reglas viven en el backend, y la UI se validó manualmente con los escenarios de RF01 a RF07.

---

## ADR-009 · Frontend sin librería de componentes y en el mismo origen

**Decisión.** Angular con componentes standalone, signals y SCSS propio, sin PrimeNG ni Material. El frontend usa rutas relativas (`/api`): en desarrollo, el proxy de `ng serve` las redirige al backend; en Docker, nginx hace lo mismo.

**Justificación.** El enunciado no evalúa diseño avanzado; evitar una librería reduce dependencias y configuración. El mismo origen evita depender de CORS (que igualmente se configuró para `localhost:4200`).

---

## ADR-010 · Ejecución con Docker Compose, sin despliegue en la nube

**Contexto.** El evaluador debe poder ejecutar la solución con el menor esfuerzo posible.

**Alternativas.** Desplegar el frontend en Vercel y el backend en un PaaS, o ejecutar todo localmente con Docker Compose.

**Decisión.** `docker compose up --build` levanta PostgreSQL, RabbitMQ, backend y frontend. Se descartó Vercel porque no ejecuta aplicaciones Java de larga duración, y un despliegue mixto agrega arranques en frío y gestión de credenciales sin aportar al alcance evaluado.

---

## ADR-011 · Publicación de eventos con patrón Outbox y RabbitMQ (opcional)

**Contexto.** Cuando una solicitud se autoriza, otros sistemas deben poder reaccionar. El riesgo es la doble escritura: guardar en PostgreSQL y publicar en RabbitMQ no son atómicos.

**Alternativas.**
- Publicar directamente después del commit: si RabbitMQ falla, el evento se pierde aunque el cupo ya se descontó.
- Publicar dentro de la transacción: si la transacción se revierte, ya se anunció algo que no ocurrió.
- Patrón Outbox.

**Decisión.**
- El evento se guarda en `outbox_event` **en la misma transacción** que la autorización.
- `OutboxPublisher` (cada 2 s) lee los pendientes, publica y solo marca `published_at` después de la **confirmación del broker** (`publisher-confirm-type: simple`, `waitForConfirmsOrDie`). Mensajes persistentes.
- Exchange **topic** `preapproved.events` con routing key `preapproved.usage.authorized`: nuevos consumidores se suscriben creando su propia cola, sin cambios en este servicio.
- Consumidor de ejemplo **idempotente**: registra el `eventId` en `processed_event` con `ON CONFLICT DO NOTHING`.
- Tras 3 intentos fallidos, el mensaje va a la **DLQ** `preapproved.usage.authorized.audit.dlq` en lugar de reencolarse indefinidamente.
- El publicador se ejecuta en el hilo del planificador de Spring, no en el event loop de WebFlux, por lo que usar `RabbitTemplate` (bloqueante) es seguro allí.

**Trade-offs.**
- Garantía **al menos una vez**: si el broker confirma pero falla la marca de publicado, el evento se reenvía. Por eso los consumidores deben ser idempotentes.
- Latencia de hasta ~2 s por el sondeo periódico.
- Con varias instancias, dos podrían tomar el mismo evento pendiente; se mitiga con la idempotencia del consumidor y se mejoraría con `FOR UPDATE SKIP LOCKED`.
- La tabla outbox no se depura en esta versión.
- El consumidor vive en el mismo servicio solo como demostración; en un caso real sería otro sistema.

**Validación.**
- `OutboxIntegrationTest`: una autorización genera exactamente un evento; un rechazo, ninguno; un reintento no genera un segundo evento; cinco reintentos simultáneos generan uno solo (el rollback de los perdedores también revierte su evento).
- `RabbitOutboxIntegrationTest`: publicación real con Testcontainers y descarte de un mensaje duplicado.
- Prueba manual con `docker compose`: logs de publicación y consumo, y colas visibles en la consola de RabbitMQ.

---

## Supuestos

- `requestReference` es única de forma global (no por cliente) y permanente.
- Una referencia rechazada también queda registrada; reenviarla con los mismos datos devuelve el mismo rechazo.
- El monto admite como máximo 2 decimales; montos con más decimales responden `400`.
- Los campos `requestReference`, `preApprovedId`, `customerId` y `amount` son obligatorios; su ausencia responde `400` y no se registra la solicitud.
- La fecha de procesamiento se registra en UTC.
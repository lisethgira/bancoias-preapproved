# Registro de uso de inteligencia artificial

## Herramientas utilizadas

- **Claude (Anthropic), en conversación de chat.** Asistente principal durante el desarrollo.
- **Cursor.** Editor con autocompletado asistido por IA.

## Actividades en las que se utilizó

1. **Análisis del enunciado:** identificación de RF04 (concurrencia) y RF05 (idempotencia) como los requisitos críticos.
2. **Diseño técnico:** comparación de alternativas para concurrencia (bloqueo optimista, pesimista, UPDATE condicional) e idempotencia (UNIQUE, huella del contenido).
3. **Configuración del entorno:** instalación de Java 21, Docker Desktop y WSL2 en Windows; generación del proyecto en start.spring.io.
4. **Generación de código base:** modelos de dominio, adaptadores R2DBC, caso de uso, API REST, componentes Angular y Dockerfiles.
5. **Generación de pruebas:** pruebas unitarias de reglas y pruebas de integración de concurrencia e idempotencia.
6. **Documentación:** estructura del README y de este registro, y redacción de las decisiones técnicas.
7. **Punto opcional RabbitMQ:** diseño del patrón Outbox, topología (exchange topic, DLQ), publicador con confirmación del broker, consumidor idempotente y sus pruebas.

## Resultados aprovechados

- La estrategia de UPDATE condicional atómico y el `CHECK (available_amount >= 0)`.
- La combinación UNIQUE + hash SHA-256 para distinguir reintentos de conflictos.
- La estructura hexagonal ligera y la separación dominio / aplicación / infraestructura.
- Las pruebas de concurrencia con `Flux.range(...).flatMap(...)` sobre Testcontainers.

## Cómo se validaron

- **Pruebas automatizadas:** 24 pruebas en verde, incluidas 10 solicitudes simultáneas y 5 reintentos simultáneos contra PostgreSQL real.
- **Pruebas manuales con `curl`:** solicitud nueva (201), reintento (200), conflicto (409) y consulta de recientes.
- **Pruebas manuales en la interfaz:** autorización, reintento, conflicto, preaprobado bloqueado y monto cero.
- **Revisión del código** generado antes de incorporarlo, y verificación de compilación en cada paso.
- **RabbitMQ:** verificación manual de los logs de publicación y consumo en Docker, y de las colas en la consola de administración.

## Resultados corregidos o descartados

- **Proyecto generado sin dependencias:** la primera generación en start.spring.io quedó sin las dependencias seleccionadas. Se detectó porque la aplicación arrancaba y se detenía sin levantar el servidor web. Se regeneró el `pom.xml` y se corrigió el `groupId`/`artifactId`, que habían quedado con los valores por defecto (`com.example` / `demo`).
- **Tipo de columna de fecha:** se cambió `TIMESTAMP` por `TIMESTAMPTZ` para representar correctamente `Instant` en UTC.
- **Diferencias entre respuesta original y reintento:** detectadas en pruebas manuales (escala del monto y precisión de la fecha). Se corrigió normalizando en la entrada (ADR-007).
- **Despliegue en Vercel:** descartado; no ejecuta aplicaciones Java de larga duración (ADR-010).
- **PrimeNG:** descartado para reducir dependencias (ADR-009).
- **Configuración de IA de Angular CLI (`AGENTS.md`, servidor MCP):** no se incorporó al repositorio.
- **Proxy de desarrollo:** se diagnosticó un `ECONNREFUSED` que se debía a que el backend no estaba en ejecución.

## Consideraciones para no exponer información sensible

- Solo se compartió con la IA el enunciado de la prueba y el código del propio proyecto.
- No se usaron datos reales de clientes: únicamente los datos ficticios del enunciado (`USR-10`, `PRA-1001`, etc.).
- No se compartieron credenciales reales. Las del `docker-compose.yml` son exclusivas del entorno local.
- El repositorio no contiene archivos `.env` ni secretos (cubierto por `.gitignore`).
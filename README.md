# Petzo backend

API REST de Petzo (adopción de perros y gatos en Gran Caracas): Java 21 + Spring Boot 3.5, Supabase (Postgres + Auth + Storage), Claude Haiku 5.5, n8n y un worker de Stagehand. Especificación: [`docs/SPEC.md`](docs/SPEC.md).

## Estructura

| Carpeta | Qué hay |
| --- | --- |
| `src/main/java/com/petzo` | `config` (seguridad JWT, CORS, Swagger) · `domain` (entidades JPA) · `service` (reglas) · `web` (controladores y DTOs) · `ai` (Haiku 5.5 + `ai_logs`) · `storage` (Supabase Storage) |
| `src/main/resources/db/migration` | Flyway: `V1` esquema (baseline), `V2` trigger de perfiles |
| `n8n/` | Flujos 1 (moderación) y 2 (notificaciones) + cómo importarlos |
| `stagehand-worker/` | Worker Node de importación desde Instagram |
| `http/petzo.http` | Requests de todos los endpoints (local y producción) |

## Correr en local

```bash
cp .env.example .env            # completa claves (ver tabla)
./mvnw spring-boot:run          # http://localhost:8090, Swagger en /swagger-ui.html
./mvnw test                     # Testcontainers: Docker Desktop debe estar corriendo
```

## Variables de entorno

| Variable | Para qué |
| --- | --- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Postgres de Supabase por el **session pooler** (IPv4) |
| `SUPABASE_URL` | JWKS para validar el JWT y API de Storage |
| `SUPABASE_SERVICE_ROLE_KEY` | Solo Storage (URLs firmadas, borrado) |
| `ANTHROPIC_API_KEY`, `AI_MODEL`, `AI_DAILY_TOKEN_CAP` | IA; sobre el tope diario la IA se apaga y todo queda en revisión manual |
| `N8N_MODERATION_WEBHOOK_URL`, `N8N_NOTIFY_WEBHOOK_URL` | Webhooks de n8n (vacíos = se omiten) |
| `INTERNAL_SECRET` | Header `x-internal-secret` entre API, n8n y worker |
| `PORT` | Puerto HTTP (Render lo define solo) |

## Deploy en Render (Docker)

1. New → **Web Service** → este repo → Runtime **Docker**, plan Free.
2. Variables: todas las de la tabla (sin `PORT`). Health check path: `/actuator/health`.
3. Al terminar, copia la URL pública en el nodo *Petzo callback* de n8n y en `http/http-client.env.json`.

El plan gratis se duerme: abre `/actuator/health` un minuto antes de la demo (Spring tarda ~1 min en arrancar en frío).

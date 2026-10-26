# Petzo · backend

API REST de **Petzo**, adopción de perros y gatos en Gran Caracas. Una persona publica una mascota, otra la encuentra y la solicita, el publicador aprueba y ambos se contactan por WhatsApp. La moderación anti-fraude, el resumen de solicitudes y las recomendaciones usan IA.

- **Repos:** este (API, n8n, Stagehand) y [petzo-frontend](https://github.com/CristianF4zio/petzo-frontend) (app móvil en `apps/mobile`, **panel admin en `apps/admin`**).
- **Registro de decisiones:** [`docs/DECISIONS.md`](docs/DECISIONS.md) · **Modelo de datos:** [guía](https://claude.ai/artifact/HRdiphme71C66X52KqqYym) · **Checklist:** [`docs/CHECKLIST.md`](docs/CHECKLIST.md)
- **Stagehand:** [`stagehand-worker/`](stagehand-worker/README.md) · **n8n:** [`n8n/`](n8n/README.md)

## Arquitectura

```
App (Expo) ─┐                     ┌─ Supabase: Postgres (JDBC) · Auth (GoTrue REST) · Storage (REST)
            ├─ HTTPS ─► API Spring ┼─ OpenRouter (pre-evaluación, match)
Panel (Vite)┘                     ├─ n8n staging (webhooks) ◄─ /internal (x-internal-secret)
                                  └─ Stagehand worker (Node) ◄─ /internal
```

**Solo la API habla con Supabase.** La app y el panel inician sesión con `/api/auth/*`, suben fotos con `/api/pets/{id}/photos/upload` y las ven por `/api/photos/**`. Las claves de Supabase y OpenRouter viven solo en el `.env` del backend y del worker.

## Versiones

| Pieza | Versión |
| --- | --- |
| Java | 21 (Temurin) · Maven Wrapper incluido (`./mvnw`) |
| Spring Boot | 3.5.16 (web, security, oauth2-resource-server, data-jpa, validation, actuator) · Flyway · springdoc-openapi |
| Base de datos | Postgres de Supabase (plan gratuito) |
| Tests | JUnit 5, MockMvc, Testcontainers (requiere **Docker Desktop** abierto) |
| Worker | Node ≥ 22.18, `@browserbasehq/stagehand` 4.3.0, Chrome |
| Túnel para n8n | `cloudflared` (gratis, sin cuenta) |

## 1. Preparar Supabase (una vez)

1. Crea un proyecto **gratuito** en Supabase.
2. **Storage:** crea el bucket público `pet-photos` (tipos `image/jpeg`, máx. 2 MB).
3. **Authentication → Providers:** activa **Email** (desactiva "Confirm email" para la demo) y **Google** con un cliente OAuth de Google Cloud. En Google Cloud el *Authorized redirect URI* es `https://<proyecto>.supabase.co/auth/v1/callback`.
4. **Authentication → URL Configuration → Redirect URLs:** agrega el callback de esta API, que es a donde Supabase devuelve el navegador después de Google:
   - `http://localhost:8090/**` (panel en la PC)
   - `http://<IP-de-tu-PC>:8090/**` (app en el celular por wifi)
5. **Migraciones:** no hay pasos manuales. Flyway aplica `src/main/resources/db/migration` (V1–V3) al arrancar la API: 10 tablas, enums, RLS de solo lectura, trigger que crea el perfil al registrarse y los 11 municipios de Gran Caracas.

## 2. Variables de entorno

Copia `.env.example` a `.env` (está en `.gitignore`) y completa los valores. Ningún valor real va en git.

| Variable | Para qué |
| --- | --- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Postgres de Supabase por el **session pooler** (IPv4) |
| `SUPABASE_URL` | URL del proyecto |
| `SUPABASE_ANON_KEY` | Clave publishable: la API la usa para Supabase Auth |
| `SUPABASE_SERVICE_ROLE_KEY` | Storage y escrituras del servidor (solo backend) |
| `INTERNAL_SECRET` | Secreto compartido con n8n y el worker (`x-internal-secret`) |
| `OPENROUTER_API_KEY`, `AI_MODEL` | IA (vacía = sin IA; todo queda en revisión manual) |
| `AI_DAILY_TOKEN_CAP` | Tope diario de tokens (también frena la moderación) |
| `N8N_MODERATION_WEBHOOK_URL`, `N8N_NOTIFY_WEBHOOK_URL` | Production URL de los dos flujos |
| `AUTH_REDIRECT_ALLOWLIST` | A dónde puede volver el navegador tras Google (`exp://`, `petzo://`, `http://localhost:5173`) |
| `IMPORT_ALLOWED_HOSTS` | Hosts desde los que la importación puede bajar fotos (`localhost`) |
| `PORT` | `8090` |

## 3. Arrancar

```bash
./mvnw spring-boot:run          # API en http://localhost:8090 · Swagger: http://localhost:8090/swagger-ui.html
./mvnw test                     # 27 tests (Docker Desktop abierto)
cloudflared tunnel --no-autoupdate --url http://localhost:8090   # solo para que n8n staging alcance la API
cd stagehand-worker && npm ci && npm start                       # worker de importación
```

La app y el panel se corren desde `petzo-frontend` (ver su README).

## 4. Usuario de prueba y administrador

- **Usuario de prueba:** regístrate desde la app o el panel, o con `POST /api/auth/register {"email","password","fullName"}`. Usa un correo y una contraseña inventados; no los publiques.
- **Administrador:** registra la cuenta y luego, en Supabase → **SQL Editor**:
  ```sql
  update profiles set role = 'admin' where id = (select id from auth.users where email = 'tu-correo-admin@ejemplo.com');
  ```
- **Datos ficticios:** los municipios vienen en la migración. Para mascotas: lanza desde el panel la importación `patas-del-avila` (Stagehand crea 4 borradores) o publica desde la app con las fotos de prueba de `src/main/resources/static/demo/img/`.

## 5. API

Documentación viva en **Swagger** (`/swagger-ui.html`, JSON en `/v3/api-docs`) y ejemplos en [`http/petzo.http`](http/petzo.http). Resumen:

| Grupo | Endpoints |
| --- | --- |
| Auth | `POST /api/auth/register · login · refresh · logout` · `GET /api/auth/google/start` · `GET /api/auth/google/callback` · `POST /api/auth/google/claim` |
| Público | `GET /api/municipalities` · `GET /api/pets` (filtros, 20 por página) · `GET /api/pets/{id}` · `GET /api/photos/**` |
| Usuario | `GET/PUT /api/me` · `POST /api/me/avatar` · `POST /api/me/foundation-request` · `GET/PUT /api/me/lifestyle` · `GET /api/me/pets · requests · favorites` |
| Mascotas | `POST /api/pets` (borrador) · `PUT /api/pets/{id}` · `POST /api/pets/{id}/photos/upload` · `PUT /api/pets/{id}/photos` · `PATCH /api/pets/{id}/status` · `DELETE /api/pets/{id}` (borradores) · `POST /api/pets/{id}/reports` · `POST/DELETE /api/favorites/{petId}` |
| Solicitudes | `POST/GET /api/pets/{id}/requests` · `POST /api/requests/{id}/approve · reject` · `GET /api/requests/{id}/contact` · `POST /api/match` |
| Admin (`role = admin`) | `GET /api/admin/moderation` · `POST /api/admin/pets/{id}/approve · reject` · fundaciones · usuarios · planes · reportes · importaciones · `GET /api/admin/stats` |
| Interno (`x-internal-secret`) | `POST /internal/pets/{id}/moderation-result` · `GET /internal/imports/next` · `POST /internal/imports/{id}/pets · status` |

Errores: siempre `ProblemDetail` con `code` (por ejemplo `PLAN_LIMIT`, `INVALID_CREDENTIALS`, `VALIDATION_ERROR` con `fields`).

## Limitaciones conocidas

- La demo es local: n8n staging alcanza la API por un túnel cuya URL cambia en cada arranque.
- El estado del login con Google vive en memoria 5 minutos: sirve para una sola instancia de la API.
- El `claim` de Google solo pide el secreto del flujo: si alguien logra que otra persona abra *su* enlace de inicio y entre con Google en esos 5 minutos, podría reclamar esa sesión. El arreglo (un ticket de un solo uso que solo recibe el navegador que volvió de Google) está descrito en `docs/DECISIONS.md` §14.
- Un trabajo de importación queda en `running` si el worker se cae a mitad; hay que relanzarlo desde el panel.
- El límite de publicaciones del plan se cuenta antes de escribir; dos envíos simultáneos podrían pasarlo (comentado en el código).
- El cobro del plan Pro, el chat y el visitante sin cuenta quedan para v2.
- El costo que reporta OpenRouter tarda unos segundos en actualizarse.

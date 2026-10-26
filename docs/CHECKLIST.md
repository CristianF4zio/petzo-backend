# Checklist backend

Sprints B0–B4 de SPEC §16. `[x]` hecho y probado · `[~]` parcial o depende de Cris · `[ ]` pendiente · `[-]` fuera de alcance, con su causa.

## Reto final · requisitos mínimos (revisado contra la base, los repos y GitHub el 26-oct-2026)
- [x] App React Native con flujo principal y manejo visible de errores y carga (`petzo-frontend/apps/mobile`); probada en iPhone (Expo Go) y en el emulador de Android (login por la API y catálogo)
- [x] API REST en Spring Boot con validaciones, autorización por JWT y rol, y errores `ProblemDetail` (27 tests)
- [x] Exactamente dos repos: `petzo-backend` y `petzo-frontend` (panel en `apps/admin`); `petzo-web` está archivado (la API de GitHub lo confirma: "Repository was archived", 26-oct)
- [x] Repos accesibles para quien evalúa: públicos desde el 26-oct (verificado con la API de GitHub sin sesión)
- [x] App y panel consumen solo la API; solo el backend accede a Supabase (sin SDK en clientes; fotos por `/api/photos/**`)
- [x] Proyecto Supabase propio y gratuito con BD, Storage y Auth en uso real (prueba de punta a punta del 25-oct)
- [x] Correo y contraseña y Google, ambos por la API: correo probado contra Supabase real; Google probado el 25-oct en el panel y en el iPhone (Site URL de Supabase = la IP de la API en el wifi)
- [x] Panel admin protegido por rol en el backend: 403 en vivo para usuarios normales y el admin entra con Google y lanza importaciones (25-oct)
- [x] Dos flujos de n8n en staging, propios, activos y con webhook firmado (403 sin secreto), ambos disparados por la API en el recorrido real del 26-oct: flujo 1 moderación por OpenRouter (ejecuciones 417, 426: riesgo, motivo y callback a la API por el túnel) y flujo 2 correo por SMTP de desarrollo Ethereal (ejecuciones 427–429: publicación aprobada, nueva solicitud y solicitud aprobada, "250 Accepted"). Evidencia sin secretos en `docs/evidencia/n8n/`
- [x] Importación automática (26-oct): la API encola según `IMPORT_CRON` (recomendado diario 06:00; demo cada 5 min) sin duplicar trabajos ni mascotas; probado en vivo 12:35 → 4 borradores, 12:40 → 0; 28/28 tests
- [x] Stagehand con fundación real (26-oct): `npm run real` lee en solo lectura la página de ANAA (robots.txt permite, `ai-input=yes`): observe → act (filtro Hembra + Buscar) → extract de 8 fichas y una ficha completa, verificado contra capturas; nada se guarda (regla de datos inventados). Evidencia en `docs/evidencia/stagehand-real/`
- [x] Stagehand: importación completa el 25-oct: el admin la lanza en el panel, el worker lee la página en Chrome real y la API crea 4 borradores con su foto en Storage (log y capturas en `docs/evidencia/stagehand/`). Se corrigió que el modelo no devolvía la URL de la foto: ahora la toma el código
- [x] Skills y agentes registrados con qué se les encargó y cómo se comprobó (`docs/SKILLS_LOG.md`, `docs/AGENTS_LOG.md` en ambos repos)
- [x] Modelo de Stagehand elegido y justificado con medición (`stagehand-worker/README.md`, D-11 del registro); el 26-oct se agregaron la comparación con ventana y headless, el proveedor (OpenAI), la captura en fallo y el flujo observe → act → extract con tope de llamadas y límite de gasto, como pide la guía
- [x] Auditoría final (26-oct, DECISIONS §14): reglas del reto confirmadas; 6 arreglos en el backend y 4 en los clientes; 27/27 tests, `tsc`, build, lint y export en verde. Quedan como limitación el `claim` de Google sin ticket y los imports que se quedan en `running`
- [x] Plataforma declarada: iOS con Expo Go (`docs/DECISIONS.md` §2)
- [x] Auditoría y review de cierre (26-oct, DECISIONS §15): ponytail-audit de ambos repos y ponytail-review de los cambios del día; hallazgos verificados y corregidos con 2 casos de test nuevos (27/27), `tsc`, export, build y lint en verde
- [x] Recorrido completo por la API contra Supabase real (26-oct, 20 pasos): registro y login por correo de 2 usuarios ficticios → perfil → borrador → foto a Storage → galería → revisión → moderación de n8n (la IA marcó la foto de prueba, lo correcto) → aprobación por el callback interno → catálogo público sin WhatsApp → favorito → solicitud → WhatsApp bloqueado antes de aprobar (409) → aprobación → contacto → pausa. Evidencia sin tokens: `docs/evidencia/e2e/recorrido-api-26oct.json`
- [x] IA real por OpenRouter: match verificado el 25-oct (Kaiser 85 %, razón en español, 3,4 s, registrado en `ai_logs`). La moderación de n8n y el resumen de solicitudes usan la misma clave y modelo

## Reto final · entregables
- [x] URLs de los dos repos, commit final y ubicación del panel y de Stagehand en `Desktop/petzo/ENTREGA.md` (se actualiza en cada push)
- [x] README desde cero en ambos repos (versiones, variables, Supabase, Google, arranque, usuario y admin de prueba, API, limitaciones). Probado el 26-oct con un clon limpio de GitHub: encontró y se corrigió un `npm ci` roto en la app
- [ ] Video de 3–5 min (Cris)
- [x] Enlaces y evidencia de ejecución de los 2 flujos de n8n: enlaces en `ENTREGA.md`; ejecuciones exportadas sin secretos en `docs/evidencia/n8n/` (417 y 426 moderación, 427–429 correo). Las capturas de *Executions* las toma Cris como complemento
- [x] Stagehand: ruta, comando, tarea, modelo y versión, resultado, log, captura y costo (`stagehand-worker/README.md`, `docs/evidencia/stagehand/`)
- [x] Registro de decisiones `docs/DECISIONS.md`, enlazado desde ambos README

## B0 Supabase + modelo — listo cuando la migración corre en Supabase y un registro nuevo crea su perfil
- [x] Proyecto Supabase, Google OAuth, bucket `pet-photos` (las fotos suben y se sirven por la API: `POST /pets/{id}/photos/upload`, `GET /api/photos/**`)
- [x] Repo con Spring Initializr (Boot 3.5.16, Java 21)
- [x] Flyway V1: enums, 10 tablas de §14, índices, `updated_at`, RLS solo SELECT, seed de 11 municipios (baseline del esquema existente)
- [x] Flyway V2: trigger `auth.users` → `profiles` (D-26) + backfill; RLS en `flyway_schema_history`
- [x] Verificado en la nube: registro nuevo crea su perfil
- [x] `CLAUDE.md`, `.env.example`, logs

## B1 API core — listo cuando los tests de controladores y servicios están en verde
- [x] Resource Server con JWKS (ES256), admin por `profiles.role`, suspendidos 403
- [x] Perfil (`/api/me`), solicitud de fundación, avatar
- [x] Mascotas: crear (pending_review + límite de plan), editar (vuelve a moderación), estados, fotos por la API (1 a 5, JPEG ≤ 2 MB)
- [x] Catálogo público con filtros y paginado de 20; detalle sin WhatsApp ni datos de moderación
- [x] Favoritos, reportes, endpoints admin, Swagger
- [x] Tests MockMvc + Testcontainers en verde

## B2 Solicitudes + IA — listo cuando una solicitud real devuelve resumen y puntaje
- [x] Solicitudes (una activa por mascota y adoptante), aprobar/rechazar, contacto WhatsApp solo tras aprobar
- [x] Pre-evaluación async y match con IA, `ai_logs` y tope diario (hoy por OpenRouter `gpt-4.1-mini`, DECISIONS §9)
- [x] Prueba con clave real: ya no es la de Anthropic sino la de OpenRouter; match verificado en vivo el 25-oct (Kaiser 85 %)

## B3 Automatizaciones — listo cuando publicar dispara moderación + correo y una importación crea borradores
- [x] `/internal/*` con `x-internal-secret`; resultados de moderación viejos se ignoran
- [x] Flujos n8n 1 y 2 exportados (`n8n/`), webhooks firmados con el mismo secreto
- [x] Worker Stagehand (`stagehand-worker/`) + `import_jobs`
- [x] Flujos importados en n8n staging por su API, con credenciales propias y activos
- [x] Corrida real de moderación (ejecución 417) e importación con Stagehand (4 borradores con foto, 25-oct)
- [x] Corrida real de correo: la API dispara el flujo 2 y Ethereal acepta los 3 correos del recorrido (ejecuciones 427–429). Antes fallaba con "connection timed out" porque la credencial de Gmail no tenía contraseña

## B4 API lista — paso al frontend
- [x] Suite completa en verde
- [x] Dockerfile multi-stage (probado en local contra la nube)
- [x] Colección `http/petzo.http` (local y producción)
- [x] Repo en GitHub (`CristianF4zio/petzo-backend`)
- [x] API corriendo en local (`./mvnw spring-boot:run`, `:8090`) contra Supabase; humo de endpoints públicos OK
- [-] Deploy en Render: fuera de alcance para la entrega; la demo es local y n8n llega a la API por el túnel (DECISIONS §8). El Dockerfile está listo
- [-] Colección `.http` contra una URL pública: depende del deploy. Se corrió el recorrido completo contra la API local con Supabase real (`docs/evidencia/e2e/`)
- [x] Revisión de seguridad: 10 de 11 hallazgos corregidos (V3: `version` + permisos por columna; `/internal` con el mismo matcher que MVC; admin solo aprueba `pending_review`)
- [x] D-28: alta como borrador, `PUT /api/pets/{id}/photos` (galería entera, una moderación), `DELETE /api/pets/{id}` solo para borradores; 20/20 tests y humo local contra Supabase
- [x] Auditoría ponytail corregida: edición que quedaba en revisión para siempre, IA que deshacía aprobaciones, IA fuera de transacción, tope diario también para moderación, respuestas acotadas, imports que ya no fallan por textos largos, borrado de Storage sin 500, sin correos en logs, código sobrante fuera (24/24 tests)

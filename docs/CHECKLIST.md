# Checklist backend

Sprints B0–B4 de SPEC §16. `[x]` hecho y probado · `[~]` parcial o depende de Cris · `[ ]` pendiente.

## Reto final · requisitos mínimos (estado al 25-oct-2026, revisado contra la base y los repos)
- [x] App React Native con flujo principal y manejo visible de errores y carga (`petzo-frontend/apps/mobile`); probada en iPhone (Expo Go) y en el emulador de Android (login por la API y catálogo)
- [x] API REST en Spring Boot con validaciones, autorización por JWT y rol, y errores `ProblemDetail` (27 tests)
- [~] Exactamente dos repos: `petzo-backend` y `petzo-frontend` (panel en `apps/admin`); el README de `petzo-web` ya apunta al panel nuevo. Falta que Cris archive `petzo-web` en GitHub (sigue activo)
- [x] App y panel consumen solo la API; solo el backend accede a Supabase (sin SDK en clientes; fotos por `/api/photos/**`)
- [x] Proyecto Supabase propio y gratuito con BD, Storage y Auth en uso real (prueba de punta a punta del 25-oct)
- [x] Correo y contraseña y Google, ambos por la API: correo probado contra Supabase real; Google probado el 25-oct en el panel y en el iPhone (Site URL de Supabase = la IP de la API en el wifi)
- [x] Panel admin protegido por rol en el backend: 403 en vivo para usuarios normales y el admin entra con Google y lanza importaciones (25-oct)
- [~] Dos flujos de n8n (moderación por OpenRouter, notificaciones por Gmail) listos y el túnel probado. Falta importarlos en staging, crear credenciales y ejecutarlos (Cris)
- [x] Stagehand: importación completa el 25-oct: el admin la lanza en el panel, el worker lee la página en Chrome real y la API crea 4 borradores con su foto en Storage (log y capturas en `docs/evidencia/stagehand/`). Se corrigió que el modelo no devolvía la URL de la foto: ahora la toma el código
- [x] Skills y agentes registrados con qué se les encargó y cómo se comprobó (`docs/SKILLS_LOG.md`, `docs/AGENTS_LOG.md` en ambos repos)
- [x] Modelo de Stagehand elegido y justificado con medición (`stagehand-worker/README.md`, D-11 del registro)
- [x] Plataforma declarada: iOS con Expo Go (`docs/DECISIONS.md` §2)
- [x] IA real por OpenRouter: match verificado el 25-oct (Kaiser 85 %, razón en español, 3,4 s, registrado en `ai_logs`). La moderación de n8n y el resumen de solicitudes usan la misma clave y modelo

## Reto final · entregables
- [~] URLs de los dos repos y commit final: borrador listo en `Desktop/petzo/ENTREGA.md`; falta poner el commit final al cerrar
- [x] README desde cero en ambos repos (versiones, variables, Supabase, Google, arranque, usuario y admin de prueba, API, limitaciones)
- [ ] Video de 3–5 min (Cris)
- [ ] Enlaces y evidencia de ejecución de los 2 flujos de n8n (Cris, tras importarlos)
- [x] Stagehand: ruta, comando, tarea, modelo y versión, resultado, log, captura y costo (`stagehand-worker/README.md`, `docs/evidencia/stagehand/`)
- [x] Registro de decisiones `docs/DECISIONS.md`, enlazado desde ambos README

## B0 Supabase + modelo — listo cuando la migración corre en Supabase y un registro nuevo crea su perfil
- [x] Proyecto Supabase, Google OAuth, bucket `pet-photos` (público, subida por URL firmada)
- [x] Repo con Spring Initializr (Boot 3.5.16, Java 21)
- [x] Flyway V1: enums, 10 tablas de §14, índices, `updated_at`, RLS solo SELECT, seed de 11 municipios (baseline del esquema existente)
- [x] Flyway V2: trigger `auth.users` → `profiles` (D-26) + backfill; RLS en `flyway_schema_history`
- [x] Verificado en la nube: registro nuevo crea su perfil
- [x] `CLAUDE.md`, `.env.example`, logs

## B1 API core — listo cuando los tests de controladores y servicios están en verde
- [x] Resource Server con JWKS (ES256), admin por `profiles.role`, suspendidos 403
- [x] Perfil (`/api/me`), solicitud de fundación, avatar
- [x] Mascotas: crear (pending_review + límite de plan), editar (vuelve a moderación), estados, fotos con URL firmada (1 a 5)
- [x] Catálogo público con filtros y paginado de 20; detalle sin WhatsApp ni datos de moderación
- [x] Favoritos, reportes, endpoints admin, Swagger
- [x] Tests MockMvc + Testcontainers en verde

## B2 Solicitudes + IA — listo cuando una solicitud real devuelve resumen y puntaje
- [x] Solicitudes (una activa por mascota y adoptante), aprobar/rechazar, contacto WhatsApp solo tras aprobar
- [x] Pre-evaluación async y match con Haiku 5.5 (SDK Java), `ai_logs` y tope diario
- [~] Prueba con `ANTHROPIC_API_KEY` real (Cris)

## B3 Automatizaciones — listo cuando publicar dispara moderación + correo y una importación crea borradores
- [x] `/internal/*` con `x-internal-secret`; resultados de moderación viejos se ignoran
- [x] Flujos n8n 1 y 2 exportados (`n8n/`), webhooks firmados con el mismo secreto
- [x] Worker Stagehand (`stagehand-worker/`) + `import_jobs`
- [~] Importar flujos en n8n y corrida real de moderación, correo e importación (Cris)

## B4 API lista — paso al frontend
- [x] Suite completa en verde
- [x] Dockerfile multi-stage (probado en local contra la nube)
- [x] Colección `http/petzo.http` (local y producción)
- [x] Repo en GitHub (`CristianF4zio/petzo-backend`)
- [x] API corriendo en local (`./mvnw spring-boot:run`, `:8090`) contra Supabase; humo de endpoints públicos OK
- [ ] Deploy en Render (opcional mientras la demo sea local)
- [ ] Colección `.http` corrida contra la URL pública (depende del deploy)
- [x] Revisión de seguridad: 10 de 11 hallazgos corregidos (V3: `version` + permisos por columna; `/internal` con el mismo matcher que MVC; admin solo aprueba `pending_review`)
- [x] D-28: alta como borrador, `PUT /api/pets/{id}/photos` (galería entera, una moderación), `DELETE /api/pets/{id}` solo para borradores; 20/20 tests y humo local contra Supabase
- [x] Auditoría ponytail corregida: edición que quedaba en revisión para siempre, IA que deshacía aprobaciones, IA fuera de transacción, tope diario también para moderación, respuestas acotadas, imports que ya no fallan por textos largos, borrado de Storage sin 500, sin correos en logs, código sobrante fuera (24/24 tests)

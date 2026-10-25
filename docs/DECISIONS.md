# Registro de decisiones · Petzo

Formato del reto: fecha, problema, decisión, por qué, alternativa descartada, consecuencia o límite, qué hizo la IA, qué hice yo y evidencia. Se fue llenando durante el trabajo; la fuente de las decisiones D-01…D-28 es el SPEC del MVP (registro `§10`), y los cambios de rumbo están en la bitácora de sesión. "IA" = Claude Code con skills y subagentes (detalle en `docs/SKILLS_LOG.md` y `docs/AGENTS_LOG.md` de cada repo).

Enlazado desde `README.md` de **petzo-backend** y **petzo-frontend**.

---

### 1. Idea y alcance — 22-oct-2026
- **Problema:** en Gran Caracas la adopción de perros y gatos vive dispersa en Instagram y jornadas, con fraude (fotos de internet) y DMs sin filtro.
- **Decisión:** Petzo: catálogo de mascotas con publicación moderada por IA, solicitud de adopción con resumen y puntaje IA, y WhatsApp liberado solo al aprobar (D-01, D-06, D-08, D-11).
- **Por qué:** cubre publicar → descubrir → solicitar → aprobar → contactar en pocos días.
- **Descartado:** chat dentro de la app, pagos, mascotas perdidas, todo el país (se van a v2, D-12): no caben en el plazo.
- **Límite:** solo perros y gatos, solo Gran Caracas, plan Pro asignado a mano.
- **IA:** redactó el SPEC a partir de mis notas y propuso el alcance. **Yo:** definí la idea, recorté v2 y validé el SPEC.
- **Evidencia:** `SPEC.md` (§1–§7, §10).

### 2. Plataforma móvil — 25-oct-2026
- **Problema:** el reto pide escoger iOS, Android o ambas y una forma reproducible de probar.
- **Decisión:** **iOS, con Expo Go en un iPhone físico** (D-15). El código es el mismo para Android.
- **Por qué:** es el dispositivo donde de verdad probé; Expo Go no necesita cuentas de desarrollador ni tiendas.
- **Descartado:** Android con APK de EAS: la cola de EAS tarda 20–40 min y no hay emulador listo; queda como opción, no como plataforma declarada.
- **Límite:** Android no se probó en dispositivo.
- **IA:** preparó los pasos de Expo Go y el login de Expo CLI. **Yo:** corrí la app en mi iPhone y reporté los errores (sin Bienvenida, `GO_BACK`).
- **Evidencia:** `petzo-frontend` commit `57779d9` (corrección de rutas protegidas tras mi prueba).

### 3. Backend en Java 21 + Spring Boot, datos con Flyway — 22-oct-2026
- **Problema:** el stack del reto es React Native + Spring Boot; el primer plan era una API en Node.
- **Decisión:** Spring Boot 3.5 con Spring Security (JWT de Supabase), JPA + Flyway (D-22, D-25).
- **Descartado:** Node + Express (D-04) y migraciones del CLI de Supabase: no cumplen el stack.
- **Límite:** `ddl-auto=validate`: la API no arranca si una entidad no coincide con la tabla.
- **IA:** escribió migraciones V1–V3, servicios y tests. **Yo:** revisé el modelo de datos contra el SPEC y aprobé cada sprint.
- **Evidencia:** 27 tests (`./mvnw test`), guía del modelo de datos enlazada en el README.

### 4. Exactamente dos repos; el panel en una subcarpeta — 25-oct-2026
- **Problema:** teníamos tres repos (`petzo-backend`, `petzo-frontend`, `petzo-web`); el reto pide exactamente dos.
- **Decisión:** el panel vive en `petzo-frontend/apps/admin`; la app en `petzo-frontend/apps/mobile`. `petzo-web` queda archivado.
- **Por qué:** `petzo-frontend` ya tenía `apps/`, y app y panel comparten TypeScript.
- **Descartado:** panel dentro del backend: mezcla Java con un build de Vite.
- **IA:** un subagente movió el scaffold y construyó el panel; la IA principal revisó su build y sus endpoints. **Yo:** elegí la ubicación y archivo el repo viejo en GitHub.
- **Evidencia:** `petzo-frontend` commit `e335ed7`.

### 5. Solo el backend toca Supabase (Auth incluido) — 25-oct-2026
- **Problema:** la app iniciaba sesión con el SDK de Supabase y subía fotos directo a Storage con URL firmada; el reto prohíbe que el cliente toque BD, Storage o Auth.
- **Decisión:** la API expone `/api/auth/register|login|refresh|logout`, sube las fotos (`POST /pets/{id}/photos/upload`, `POST /me/avatar`) y las sirve (`GET /api/photos/**`). Se quitó `@supabase/supabase-js` de la app.
- **Por qué:** una sola puerta a los datos; las claves de Supabase quedan solo en el `.env` del backend.
- **Descartado:** seguir leyendo las fotos desde la URL pública del bucket: un evaluador podría verlo como acceso directo a Storage.
- **Límite:** las fotos pasan por la API (≤ 2 MB, JPEG verificado por sus primeros bytes); la caché es de 1 día.
- **IA:** implementó endpoints, tests y la migración de la app (subagente). **Yo:** decidí servir las fotos por la API.
- **Evidencia:** `AuthTest`, `ApiTest` y la prueba de punta a punta del 25-oct contra Supabase: registro, login, 401 con clave mala, subida, 422 con archivo falso, foto servida por la API.

### 6. Google coordinado por el backend, sin tokens en URLs — 25-oct-2026
- **Problema:** el reto pide que el backend coordine Google y su callback, con la pantalla oficial de Google, y volver a la app o al panel sin tokens en URLs ni logs.
- **Decisión:** `GET /api/auth/google/start?flow&challenge&redirect` → Supabase → Google → `GET /api/auth/google/callback` (la API canjea el código PKCE) → el navegador vuelve a la app **sin tokens** → la app hace `POST /api/auth/google/claim {flow, secret}` y recibe la sesión una sola vez. `challenge = sha256(secret)`: el secreto solo sale del cliente en el cuerpo del POST.
- **Por qué:** ni el `flow` ni la URL de vuelta sirven para robar la sesión sin el secreto; los destinos de vuelta están en una lista blanca (sin redirección abierta).
- **Descartado:** devolver tokens en el fragmento de la URL (flujo implícito) y el PKCE en el cliente que usábamos antes.
- **Límite:** el estado del flujo vive en memoria 5 minutos (`shortcut:` comentado; sirve para una sola instancia de la API).
- **IA:** diseñó el protocolo y lo probó. **Yo:** agrego las URLs de callback en Supabase y pruebo el login real con mi cuenta de Google.
- **Evidencia:** `AuthTest.googleFlowNeverPutsTokensInUrlsAndIsOneTime`; cadena real API → Supabase → `accounts.google.com` verificada el 25-oct.

### 7. Panel admin protegido en el backend — 25-oct-2026
- **Decisión:** toda ruta `/api/admin/**` exige `profiles.role = 'admin'` en el backend; el panel además oculta su contenido si `/api/me` no es admin.
- **Por qué:** ocultar botones no basta.
- **IA:** construyó el panel (subagente). **Yo:** creo el admin con un `update` en Supabase (ver README) y pruebo la cola de moderación.
- **Evidencia:** `ApiTest.adminFlows` (403 a no-admin), `apps/admin`.

### 8. Moderación y notificaciones con n8n en staging — 25-oct-2026
- **Problema:** n8n staging corre en internet y la API en mi PC; además no hay clave de Anthropic.
- **Decisión:** flujo 1 moderación (OpenRouter `gpt-4.1-mini` con texto y fotos → `POST /internal/pets/{id}/moderation-result`) y flujo 2 notificaciones por correo (Gmail con contraseña de aplicación). La API se expone a n8n con un túnel `cloudflared` (D-34).
- **Por qué:** todo sigue local; el túnel no requiere cuenta ni deploy.
- **Descartado:** deploy en Render (tarda en despertar, no es necesario para la demo local).
- **Límite:** la URL del túnel cambia en cada arranque y hay que actualizarla en n8n; si n8n o la IA fallan, la publicación queda en revisión para el admin (RNF-08).
- **IA:** adaptó los JSON y la guía de importación. **Yo:** importo los flujos en staging, creo las credenciales y dejo la evidencia de ejecución.
- **Evidencia:** `n8n/*.json`, `n8n/README.md`, capturas de *Executions*.

### 9. Toda la IA por OpenRouter — 25-oct-2026
- **Problema:** el SPEC fijaba Claude Haiku 5.5 (D-16), pero la única clave disponible es la de OpenRouter con $5.
- **Decisión:** pre-evaluación, match, moderación y Stagehand usan OpenRouter; modelo `openai/gpt-4.1-mini`; tope diario de tokens compartido (`AI_DAILY_TOKEN_CAP`) que también frena la moderación.
- **Por qué:** una sola clave; cada moderación cuesta cerca de $0.0005, así que el saldo alcanza.
- **Descartado:** dejar la IA apagada (los flujos tienen respaldo manual, pero el valor de la idea es la IA).
- **IA:** reemplazó el SDK de Anthropic por llamadas HTTP y quitó la dependencia. **Yo:** aprobé usar el saldo también fuera de Stagehand.
- **Evidencia:** `AiService.java`, `ai_logs`.

### 10. Stagehand sobre una página demo propia — 25-oct-2026
- **Problema:** el worker leía perfiles de Instagram; Instagram pide login y sus términos prohíben el scraping (el reto pide respetar términos y no automatizar logins).
- **Decisión:** página ficticia de una fundación servida por la API (`/demo/fundaciones/patas-del-avila.html`); Stagehand la abre en Chrome real, extrae las mascotas y las manda como borradores por `/internal`.
- **Por qué:** es una tarea de navegador real dentro de la idea (importar el catálogo de una fundación autorizada, HU-14) sin violar términos.
- **Descartado:** buscar otro sitio público: no garantiza permiso ni estabilidad para la demo.
- **Límite:** la API solo descarga fotos de `IMPORT_ALLOWED_HOSTS` (protección SSRF).
- **IA:** reescribió el worker con la guía v4 y lo corrió. **Yo:** validé contra la captura que los datos extraídos coinciden con la página.
- **Evidencia:** `docs/evidencia/stagehand/` (log y capturas).

### 11. Modelo y gasto de Stagehand — 25-oct-2026
- **Decisión:** `openai/gpt-4.1-mini` (proveedor OpenAI vía OpenRouter).
- **Por qué:** con la misma página acertó 4/4 fichas, igual que `google/gemini-2.5-flash`, a menor costo ($0.001004 por corrida, leído de `/api/v1/key`) y sin tokens de razonamiento.
- **Descartado:** Gemini 2.5 Flash (≈ $0.0012, más caro por salida) y Claude Haiku 4.5 (~3× más caro, sin falla que lo justifique).
- **Límite:** tope de $0.25 por corrida, 120 s por trabajo, 2 intentos de lectura.
- **Evidencia:** tabla en `stagehand-worker/README.md`; saldo $4.998996 de $5 tras las pruebas.

### 12. Publicación como borrador y galería en un solo paso — 22-oct-2026 (D-28)
- **Problema:** subir fotos de a una dejaba mascotas atascadas en revisión y moderaba N veces.
- **Decisión:** `POST /pets` crea un borrador; la galería entera se guarda con `PUT /pets/{id}/photos`; `PATCH status` la envía a revisión.
- **IA:** el `code-review` encontró el problema; la IA lo corrigió con tests. **Yo:** detecté en el iPhone que una edición quedaba en revisión y pedí la auditoría.
- **Evidencia:** `InternalTest.editWithSameGalleryIsModeratedForTheFinalVersion` (falla si se quita el arreglo; se probó).

### 13. Recortes y errores que cambiaron el rumbo
- **Recortes:** cobro del plan Pro, chat, mascotas perdidas y APK de tiendas (v2). El visitante sin cuenta no está: la app pide sesión para todo.
- **Cambios de rumbo:** API en Node → Spring Boot (stack del reto); tres repos → dos; SDK de Supabase en la app → todo por la API; Instagram → página demo; Anthropic → OpenRouter.
- **Errores encontrados en pruebas reales:** la Bienvenida no aparecía en el iPhone (faltaba `redirectTo` en Expo Router 58); la IA podía deshacer la aprobación del dueño (guardaba la entidad entera); editar dejaba la mascota en revisión para siempre (versión subida sin moderar). Los tres se corrigieron con tests.

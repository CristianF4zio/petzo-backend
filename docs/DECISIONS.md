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
- **Decisión:** **iOS, con Expo Go en un iPhone físico** (D-15). El 25-oct también se probó en el emulador de Android Studio (Expo Go + `adb reverse`): Bienvenida, login por la API y catálogo.
- **Por qué:** es el dispositivo donde de verdad probé; Expo Go no necesita cuentas de desarrollador ni tiendas.
- **Descartado:** Android con APK de EAS: la cola de EAS tarda 20–40 min y no hay emulador listo; queda como opción, no como plataforma declarada.
- **Límite:** Android probado solo en emulador, no en un teléfono físico.
- **IA:** preparó los pasos de Expo Go y el login de Expo CLI. **Yo:** corrí la app en mi iPhone y reporté los errores (sin Bienvenida, `GO_BACK`).
- **Evidencia:** `petzo-frontend` commit `57779d9` (corrección de rutas protegidas tras mi prueba).

### 3. Backend en Java 21 + Spring Boot, datos con Flyway — 22-oct-2026
- **Problema:** el stack del reto es React Native + Spring Boot; el primer plan era una API en Node.
- **Decisión:** Spring Boot 3.5 con Spring Security (JWT de Supabase), JPA + Flyway (D-22, D-25).
- **Por qué:** es el stack que pide el reto; Flyway deja el esquema versionado en el repo y lo aplica al arrancar, así otra persona lo levanta sin pasos manuales.
- **Descartado:** Node + Express (D-04) y migraciones del CLI de Supabase: no cumplen el stack.
- **Límite:** `ddl-auto=validate`: la API no arranca si una entidad no coincide con la tabla.
- **IA:** escribió migraciones V1–V3, servicios y tests. **Yo:** revisé el modelo de datos contra el SPEC y aprobé cada sprint.
- **Evidencia:** 27 tests (`./mvnw test`), migraciones en `src/main/resources/db/migration/`.

### 4. Exactamente dos repos; el panel en una subcarpeta — 25-oct-2026
- **Problema:** teníamos tres repos (`petzo-backend`, `petzo-frontend`, `petzo-web`); el reto pide exactamente dos.
- **Decisión:** el panel vive en `petzo-frontend/apps/admin`; la app en `petzo-frontend/apps/mobile`. `petzo-web` queda archivado.
- **Por qué:** `petzo-frontend` ya tenía `apps/`, y app y panel comparten TypeScript.
- **Descartado:** panel dentro del backend: mezcla Java con un build de Vite.
- **Límite:** `petzo-frontend` tiene dos `package.json` independientes (sin workspaces); cada uno se instala por separado.
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
- **Límite:** el estado del flujo vive en memoria 5 minutos (`shortcut:` comentado; sirve para una sola instancia de la API). En la prueba con el iPhone, Supabase no aceptó la Redirect URL con la IP y devolvió el navegador al Site URL (`localhost:3000`). Se resolvió poniendo como **Site URL** la API en el wifi (`http://<IP>:8090`); si la IP cambia, hay que actualizarla.
- **IA:** diseñó el protocolo y lo probó. **Yo:** agrego las URLs de callback en Supabase y pruebo el login real con mi cuenta de Google.
- **Evidencia:** `AuthTest.googleFlowNeverPutsTokensInUrlsAndIsOneTime`; cadena real API → Supabase → `accounts.google.com` verificada el 25-oct.

### 7. Panel admin protegido en el backend — 25-oct-2026
- **Problema:** el panel modera, verifica fundaciones, suspende usuarios e importa catálogos; cualquier usuario con sesión podría llamar esas rutas.
- **Decisión:** toda ruta `/api/admin/**` exige `profiles.role = 'admin'` en el backend; el panel además oculta su contenido si `/api/me` no es admin.
- **Por qué:** ocultar botones no basta.
- **Descartado:** guardar el rol como claim en el JWT de Supabase: exige un hook de Auth y el rol cambiado no aplica hasta renovar el token; leerlo de `profiles` en cada petición aplica al instante.
- **Límite:** una consulta a `profiles` por petición admin; el primer admin se crea a mano con SQL (README §4).
- **IA:** construyó el panel (subagente). **Yo:** creo el admin con un `update` en Supabase (ver README) y pruebo la cola de moderación.
- **Evidencia:** `ApiTest.adminFlows` (403 a no-admin), `apps/admin`.

### 8. Moderación y notificaciones con n8n en staging — 25-oct-2026
- **Problema:** n8n staging corre en internet y la API en mi PC; además no hay clave de Anthropic.
- **Decisión:** flujo 1 moderación (OpenRouter `gpt-4.1-mini` con texto y fotos → `POST /internal/pets/{id}/moderation-result`) y flujo 2 notificaciones por correo (Gmail con contraseña de aplicación). La API se expone a n8n con un túnel `cloudflared` (D-34).
- **Por qué:** todo sigue local; el túnel no requiere cuenta ni deploy.
- **Descartado:** deploy en Render (tarda en despertar, no es necesario para la demo local).
- **Límite:** la URL del túnel cambia en cada arranque y hay que actualizarla en n8n; si n8n o la IA fallan, la publicación queda en revisión para el admin (RNF-08).
- **IA:** adaptó los JSON, los importó en staging por la API pública de n8n (con mi API key solo en el entorno del proceso), creó las credenciales sin la contraseña de Gmail y actualiza la URL del túnel. **Yo:** entro a n8n, pego la contraseña de aplicación de Gmail en la credencial y reviso las ejecuciones.
- **Evidencia:** `n8n/*.json`, `n8n/README.md`; flujo 1 ejecución 417 del 25-oct (≈7 s, riesgo 90 con razón de la IA: la mascota de prueba quedó en revisión, lo correcto para una ficha que se declara ficticia); webhooks sin el secreto responden 403; resumen limpio de la ejecución en `docs/evidencia/n8n/`; capturas de *Executions*.
- **26-oct · correo con cuenta de desarrollo:** el flujo 2 pasó de Gmail personal a una cuenta SMTP de prueba de Ethereal (la regla pide cuentas de desarrollo y datos ficticios; Ethereal atrapa los correos sin entregarlos). Cris creó la cuenta; la IA creó la credencial en n8n por su API, apuntó el nodo y republicó el flujo. Descartado: Gmail personal (cuenta real) y Telegram/Discord (más pasos para Cris sin ganar nada para la demo). Evidencia: ejecuciones 427–429 disparadas por la API en el recorrido E2E, "250 Accepted" (`docs/evidencia/n8n/`).
- **26-oct · túnel:** con el cambio de IP Cloudflare dio de baja el túnel rápido y la moderación no podía volver (ejecuciones 418 y 423). Además, editar un flujo activo no cambia la versión publicada: hay que desactivarlo y activarlo. Cris levanta el túnel (el permiso de la IA no incluye exponer la API) y la IA actualiza y republica el flujo; ejecuciones 424 y 426 en verde.

### 9. Toda la IA por OpenRouter — 25-oct-2026
- **Problema:** el SPEC fijaba Claude Haiku 5.5 (D-16), pero la única clave disponible es la de OpenRouter con $5.
- **Decisión:** pre-evaluación, match, moderación y Stagehand usan OpenRouter; modelo `openai/gpt-4.1-mini`; tope diario de tokens compartido (`AI_DAILY_TOKEN_CAP`) que también frena la moderación.
- **Por qué:** una sola clave; cada moderación cuesta cerca de $0.0005, así que el saldo alcanza.
- **Descartado:** dejar la IA apagada (los flujos tienen respaldo manual, pero el valor de la idea es la IA).
- **Límite:** el saldo de $5 no se recarga; al llegar al tope diario la IA se apaga y todo queda en revisión manual. Gasto total al 26-oct: $0.0075.
- **IA:** reemplazó el SDK de Anthropic por llamadas HTTP y quitó la dependencia. **Yo:** aprobé usar el saldo también fuera de Stagehand.
- **Evidencia:** `AiService.java`, `ai_logs`.

### 10. Stagehand sobre una página demo propia — 25-oct-2026
- **Problema:** el worker leía perfiles de Instagram; Instagram pide login y sus términos prohíben el scraping (el reto pide respetar términos y no automatizar logins).
- **Decisión:** página ficticia de una fundación servida por la API (`/demo/fundaciones/patas-del-avila.html`). Stagehand la abre en Chrome real, pulsa "Ver mascotas en adopción" (`observe` + `act`), extrae las mascotas (`extract`) y las manda como borradores por `/internal`. No hay una API que sirva: el catálogo de una fundación vive en su sitio web, y por eso la tarea es de navegador.
- **Por qué:** es una tarea de navegador real dentro de la idea (importar el catálogo de una fundación autorizada, HU-14) sin violar términos.
- **Descartado:** buscar otro sitio público: no garantiza permiso ni estabilidad para la demo.
- **Límite:** la API solo descarga fotos de `IMPORT_ALLOWED_HOSTS` (protección SSRF).
- **IA:** reescribió el worker con la guía v4 y lo corrió. **Yo:** validé contra la captura que los datos extraídos coinciden con la página.
- **Evidencia:** `docs/evidencia/stagehand/` (log y capturas).
- **26-oct · fundación real, solo lectura:** Cris pidió que Stagehand trabaje con una fundación real. Choca con la regla "usa solo datos inventados", así que Cris eligió **leer sin guardar**: `npm run real` abre la página de ANAA (Madrid), cuyo `robots.txt` permite todo y declara `ai-input=yes`. Filtra por hembras con `observe` → comprobación en el DOM → `act` (selector de sexo y botón Buscar), extrae 8 fichas y una ficha completa, y no escribe nada en la API ni en Supabase. Descartado: importarlas como borradores (datos reales en el proyecto y fotos de terceros) y las fundaciones venezolanas (publican en Instagram, cuyos términos prohíben el scraping). Errores que la verificación atajó: `observe` propuso el enlace de otro perro (todas las fichas dicen "Ver más detalles") y luego el selector de raza; el sitio respondía en inglés. Coste ≈ $0.025 por corrida. Evidencia: `docs/evidencia/stagehand-real/`.

### 11. Modelo y gasto de Stagehand — 25-oct-2026
- **Problema:** hay que elegir el modelo con $5 sin recarga, midiendo en la tarea real y no por el nombre.
- **Decisión:** `openai/gpt-4.1-mini` (proveedor OpenAI vía OpenRouter).
- **Por qué:** con la misma página acertó 4/4 fichas, igual que `google/gemini-2.5-flash`, a menor costo ($0.001004 por corrida, leído de `/api/v1/key`) y sin tokens de razonamiento.
- **Descartado:** Gemini 2.5 Flash (≈ $0.0012, más caro por salida) y Claude Haiku 4.5 (~3× más caro, sin falla que lo justifique).
- **Límite:** tope de $0.25 por corrida, 120 s por trabajo, 2 intentos de lectura.
- **IA:** corrió la misma página con los dos modelos (`npm run dry`) y leyó el gasto real de `/api/v1/key`. **Yo:** comparé las fichas extraídas contra la captura y aprobé el modelo más barato con igual acierto.
- **Evidencia:** tabla en `stagehand-worker/README.md`; saldo $4.998996 de $5 tras las pruebas.
- **26-oct:** se comparó con ventana y headless (mismo 4/4 y mismos campos); el log guarda el proveedor (OpenAI), el modo y una captura `*-fallo.png` cuando algo falla, como pide la guía.
- **26-oct (revisión contra la guía):** el flujo usaba solo `extract` y la guía dice que basta uno con `observe`, `act` y `extract`. La página demo ahora abre su lista con un botón, como muchos sitios reales. El worker observa el botón, comprueba que la acción sea ese clic, actúa y verifica el estado antes de extraer. Además: tope de 6 llamadas al modelo por corrida; se detiene si la clave no tiene límite de gasto o si no puede leer el saldo; el chequeo de saldo pasó dentro del `try` (antes un fallo dejaba el trabajo en `running`); los tokens se reinician por trabajo; el log registra el saldo disponible. Corrida: 3 llamadas, $0.001227, 4/4 con ventana y headless. Gasto total de la clave: $0.0097 de $5.

### 12. Publicación como borrador y galería en un solo paso — 22-oct-2026 (D-28)
- **Problema:** subir fotos de a una dejaba mascotas atascadas en revisión y moderaba N veces.
- **Decisión:** `POST /pets` crea un borrador; la galería entera se guarda con `PUT /pets/{id}/photos`; `PATCH status` la envía a revisión.
- **Por qué:** la moderación ve la publicación completa una sola vez, y un error a mitad de la subida no deja nada en revisión.
- **Descartado:** subir y moderar foto por foto (era el diseño original): N moderaciones y estados intermedios.
- **Límite:** un borrador abandonado queda en "Mis publicaciones" hasta que su dueño lo borra; las fotos subidas y no guardadas en la galería no se limpian solas.
- **IA:** el `code-review` encontró el problema; la IA lo corrigió con tests. **Yo:** detecté en el iPhone que una edición quedaba en revisión y pedí la auditoría.
- **Evidencia:** `InternalTest.editWithSameGalleryIsModeratedForTheFinalVersion` (falla si se quita el arreglo; se probó).

### 13. Recortes y errores que cambiaron el rumbo — 22 al 25-oct-2026
- **Problema:** el plazo (22 al 26-oct) no alcanza para todo el SPEC, y las pruebas reales mostraron fallas que el código no dejaba ver.
- **Decisión (recortes):** cobro del plan Pro, chat, mascotas perdidas y APK de tiendas pasan a v2. El visitante sin cuenta no está: la app pide sesión para todo.
- **Por qué:** el reto pide terminar y demostrar el recorrido completo; esas funciones no son parte del recorrido principal.
- **Descartado:** hacer el visitante sin cuenta y el APK de EAS. Duplicaban pantallas y la cola de EAS tarda 20–40 min, sin sumar a los requisitos.
- **Cambios de rumbo:** API en Node → Spring Boot (stack del reto); tres repos → dos; SDK de Supabase en la app → todo por la API; Instagram → página demo; Anthropic → OpenRouter.
- **Errores encontrados en pruebas reales:** la Bienvenida no aparecía en el iPhone (faltaba `redirectTo` en Expo Router 58); la IA podía deshacer la aprobación del dueño (guardaba la entidad entera); editar dejaba la mascota en revisión para siempre (versión subida sin moderar). Los tres se corrigieron con tests.
- **Límite:** la demo exige cuenta y corre en local (sin deploy).
- **IA:** auditó el código y corrigió los errores con tests. **Yo:** encontré los errores probando en el iPhone y decidí los recortes.
- **Evidencia:** `petzo-frontend` commit `57779d9` (Bienvenida); `InternalTest` y `RequestTest` (IA y aprobaciones); SPEC §10 (D-12, v2).

### 14. Auditoría final — 26-oct-2026
- **Problema:** antes de entregar hay que revisar seguridad y lo que puede romper la demo, sin rehacer nada.
- **Decisión:** dos subagentes auditaron los repos en paralelo (`ponytail-audit`, solo lectura). Las reglas del reto se cumplen (403 en admin, secreto en `/internal`, sin secretos en git ni en evidencia, exportaciones de n8n sin credenciales). Corregí lo pequeño y seguro: la lista de destinos de Google aceptaba `http://localhost:5173@evil.test` (ahora el prefijo debe terminar en `/`, `?` o `#`, con test); un admin podía suspenderse a sí mismo (409, con test); los 405/415 de Spring no salían como `ProblemDetail`; "correo sin confirmar" y el límite de intentos se mostraban como "servicio caído"; el usuario de fundación no admitía `-` y por eso los borradores importados de `patas-del-avila` nunca llegaban a la fundación; en la app, pantalla en blanco si la API no responde al abrir, y un refresh en curso podía deshacer el cierre de sesión; en el panel, "Retirar" fallaba en el segundo reporte de la misma mascota.
- **Por qué:** el día de la entrega solo entran cambios pequeños, con test y que no obliguen a volver a probar todo en el teléfono.
- **Límite:** lo que no se corrigió queda en "Limitaciones conocidas" del README del backend.
- **Descartado (queda como limitación conocida):** amarrar el `claim` de Google a un ticket que solo recibe el navegador que inició sesión. Hoy quien conoce el secreto de un flujo puede reclamarlo si convence a otra persona de abrir su enlace de inicio (ventana de 5 min, un solo uso). El arreglo toca la API, la app y el panel y obliga a volver a probar Google en el iPhone el día de la entrega; se prefirió no arriesgar la demo. Tampoco se arreglaron: los trabajos de importación que quedan en `running` si el worker muere, ni las subidas de fotos sin tope por mascota.
- **IA:** auditó, eligió qué entraba (le di ese rol de tech lead para el cierre) y aplicó los arreglos con tests. **Yo:** reviso este registro y vuelvo a probar el recorrido en la demo.
- **Evidencia:** `AuthTest` (destino `@evil.test` → 422), `ApiTest` (admin no suspendible → 409), suite completa en verde.
- **Prueba desde cero:** cloné los dos repos de GitHub en una carpeta vacía y seguí los README. El backend empaqueta, el panel compila y el worker instala, pero `npm ci` de la app fallaba: `react-dom` 19.3.1 (transitiva) pide `react` ^19.3.1 y Expo 58 fija `react` 19.3.0. En mi PC no se veía porque `node_modules` ya existía. Se fijó `react-dom` en 19.3.0 con `overrides`, y el clon limpio instala y pasa `tsc`.

### 15. Auditoría y review de cierre — 26-oct-2026
- **Problema:** después del rediseño y los cambios de Stagehand y n8n había que auditar de nuevo antes de entregar.
- **Decisión:** 3 agentes en solo lectura (`ponytail-audit` del backend, del frontend y `ponytail-review` de los cambios del día). Verifiqué cada hallazgo en el código y corregí los que rompen algo o tienen caso concreto: la subida de fotos retenía una de las 5 conexiones a la base mientras hablaba con Storage (ahora sin transacción); los clientes de Supabase Auth, Storage y n8n no tenían timeout (ahora 10 s de conexión y 20–30 s de lectura, el mismo patrón que la IA); pausar una mascota reportada esquivaba el rechazo del admin; recargar el callback de Google daba 500; la API aceptaba identificadores de importación que el worker rechaza; el tope de 120 s de Stagehand no cerraba el navegador; contraste del texto de error, de los contadores y del logo sobre azul; los avisos no se anunciaban a lectores de pantalla; áreas táctiles de 40 px en el panel; comentarios falsos y colores sin uso.
- **Por qué:** cambios pequeños y con test o verificación, sin tocar el comportamiento que ya funciona.
- **Descartado (para después de la entrega):** unificar botones de peligro hechos a mano con `Button variant="danger"`, reutilizar `SelectButton fill` en Publicar y perfil, etiquetas accesibles en todos los `Input`, el margen doble sobre la barra de pestañas en Publicar, quitar `async-storage` sin uso, y mapear el doble toque en solicitud/favorito a 409.
- **Límite:** los descartados son de mantenimiento o accesibilidad fina; ninguno rompe el recorrido.
- **IA:** auditó, verificó y corrigió. **Yo:** pedí la auditoría final y reviso el informe.
- **Evidencia:** 27/27 tests (nuevos casos en `ApiTest.adminFlows` y `AuthTest`), `tsc`, `expo export`, build y lint del panel en verde.

# Petzo — Especificación del MVP

2026-10-22 · @Cris

## 1. Problema y mercado

En Venezuela no hay una plataforma nacional dominante para adoptar mascotas: la oferta vive dispersa en cuentas de Instagram, Facebook y jornadas presenciales.

- **Dispersión:** los adoptantes encuentran mascotas en cuentas de rescate de Instagram ([El Diario](https://eldiario.com/2020/03/08/como-adoptar-una-mascota-cambio-nuestras-vidas/)) o en jornadas como "Yo Adopto" del refugio El Valle ([El Diario](https://eldiario.com/2022/11/10/realizaran-jornada-masiva-adopcion-mascota-caracas/)).
- **Fraude y desconfianza:** se denuncian publicaciones con fotos descargadas de internet de animales que ni siquiera están en Venezuela ([Aporrea](https://www.aporrea.org/actualidad/a305440.html)).
- **Volumen:** la Fundación Adopta un Perro o Gato sin Techo estima unos 200.000 abandonos al año ([VOA](https://www.vozdeamerica.com/a/adopten-grito-desesperado-refugio-animales-venezuela/6636078.html)); los terremotos de junio de 2026 dejaron más animales desamparados ([Noticias RCN](https://www.noticiasrcn.com/internacional/fundacion-en-venezuela-encargada-de-reencontrar-mascotas-con-familias-1042838)).
- **Referentes en la región:** Medellín (La Perla), Bogotá (Distrito Appnimal) y Buenos Aires (Animales BA) ya usan catálogo + solicitud de adopción en app o web.

**Oportunidad:** centralizar la oferta de Gran Caracas y añadir confianza con IA (moderación anti-fraude y evaluación de solicitudes).

Limitación: la búsqueda no prueba que no exista ninguna iniciativa pequeña; sí muestra que no hay un jugador de referencia.

## 2. Idea de negocio

Petzo es una app móvil donde cualquier persona o fundación de Gran Caracas publica perros y gatos en adopción, y los adoptantes los encuentran y solicitan con apoyo de IA.

**Propuesta de valor**

- Adoptante: un solo lugar para buscar, recomendaciones según su estilo de vida y publicaciones revisadas contra fraude.
- Publicador: llega a más gente y recibe solicitudes ya resumidas y puntuadas, sin filtrar DMs a mano.

**Modelo freemium para fundaciones**

| Plan | Quién | Incluye |
| --- | --- | --- |
| Gratis | Particulares y fundaciones | Hasta 3 publicaciones activas; moderación y solicitudes con IA |
| Pro | Fundaciones verificadas | Hasta 30 publicaciones activas, badge de verificada, métricas de solicitudes |

En el MVP el plan Pro se asigna desde el panel admin; el cobro llega en v2.

Límites: 3 activas en Gratis cubren a un particular con una camada pequeña; 30 en Pro cubren un refugio mediano sin abrir la puerta a spam. Las publicaciones adoptadas o pausadas no cuentan.

## 3. Alcance del MVP

El MVP cubre el ciclo publicar → descubrir → solicitar → aprobar → contactar por WhatsApp, solo perros y gatos, solo Gran Caracas.

| Dentro del MVP | Fuera (v2) |
| --- | --- |
| Registro e inicio de sesión con correo/contraseña y Google | Cobro del plan Pro (Pago Móvil/Zelle) |
| Publicación de mascotas con fotos | Donaciones |
| Moderación anti-fraude con IA (n8n) | Mascotas perdidas |
| Catálogo con filtros (especie, tamaño, edad, zona) | Seguimiento post-adopción |
| Match adoptante-mascota con IA | Chat dentro de la app |
| Solicitud de adopción con pre-evaluación IA | Publicación en Play Store y App Store |
| Liberación de WhatsApp al aprobar |  |
| Notificaciones de estado (n8n) |  |
| Importación de fundaciones desde Instagram (Stagehand) |  |
| Panel admin web |  |

Entrega: APK de Android generado con EAS y Expo Go para probar en iOS. Publicar en tiendas queda fuera.

## 4. Roles

| Rol | Dónde | Puede |
| --- | --- | --- |
| Visitante | App | Ver catálogo sin datos de contacto |
| Usuario | App | Adoptar (solicitar) y publicar mascotas; un mismo usuario hace ambas cosas |
| Fundación | App | Lo mismo que Usuario + perfil de fundación, badge si está verificada, plan Pro |
| Administrador | Panel web | Moderar publicaciones, verificar fundaciones, asignar planes, lanzar importaciones, ver métricas |

## 5. Requerimientos funcionales

| ID | Módulo | Requerimiento |
| --- | --- | --- |
| RF-01 | Auth | Registro e inicio de sesión con correo y contraseña (Supabase Auth) |
| RF-02 | Auth | Inicio de sesión con Google (Supabase Auth OAuth) |
| RF-03 | Auth | Recuperación de contraseña por correo |
| RF-04 | Perfil | Editar perfil: nombre, foto, zona de Gran Caracas, WhatsApp |
| RF-05 | Perfil | Solicitar cuenta de fundación (nombre, Instagram, descripción) |
| RF-06 | Publicación | Crear publicación: especie, nombre, edad aprox., sexo, tamaño, zona, salud (vacunas, esterilización), descripción, 1 a 5 fotos (Supabase Storage) |
| RF-07 | Publicación | Respetar el límite de publicaciones activas según el plan |
| RF-08 | Moderación IA | Toda publicación nueva entra como "en revisión"; n8n la evalúa con IA y la aprueba o la marca para el admin con motivo |
| RF-09 | Publicación | Editar, pausar o marcar como adoptada una publicación propia |
| RF-10 | Catálogo | Listar publicaciones aprobadas con filtros por especie, tamaño, edad, sexo y zona |
| RF-11 | Catálogo | Ver detalle de la mascota y del publicador (badge si es fundación verificada) |
| RF-12 | Match IA | Cuestionario de estilo de vida (vivienda, tiempo, niños, otras mascotas, experiencia) |
| RF-13 | Match IA | Recomendar mascotas compatibles con un porcentaje y una razón breve |
| RF-14 | Favoritos | Guardar mascotas en favoritos |
| RF-15 | Solicitud | Enviar solicitud de adopción con formulario y motivación |
| RF-16 | Solicitud IA | El backend genera resumen y puntaje de compatibilidad de la solicitud para el publicador |
| RF-17 | Solicitud | El publicador aprueba o rechaza; al aprobar, se libera el WhatsApp de ambos |
| RF-18 | Notificaciones | n8n envía correo en cada cambio de estado: publicación aprobada/rechazada, solicitud nueva, aprobada o rechazada |
| RF-19 | Importación | El admin lanza la importación de un perfil de Instagram autorizado; Stagehand extrae publicaciones y las guarda como borradores |
| RF-20 | Admin | Revisar cola de moderación y aprobar o rechazar con motivo |
| RF-21 | Admin | Verificar fundaciones y asignar plan Gratis o Pro |
| RF-22 | Admin | Gestionar usuarios (suspender) y ver publicaciones reportadas |
| RF-23 | Reportes | Cualquier usuario puede reportar una publicación |
| RF-24 | Admin | Dashboard con totales: publicaciones, solicitudes, adopciones, rechazos de moderación |

## 6. Requerimientos no funcionales

| ID | Atributo | Requerimiento |
| --- | --- | --- |
| RNF-01 | Seguridad | Supabase solo autentica; la API Express valida el JWT y autoriza cada acción |
| RNF-02 | Seguridad | Row Level Security activo en todas las tablas; el service key vive solo en el backend |
| RNF-03 | Restricción | Sin Edge Functions: toda lógica en la API REST o en n8n |
| RNF-04 | Privacidad | El WhatsApp solo se expone tras aprobar la solicitud |
| RNF-05 | Costo | Presupuesto de IA de 5 USD: modelo económico, prompts cortos, salida JSON, IA solo en eventos (no en cada lectura) |
| RNF-06 | Rendimiento | Catálogo paginado (20 por página); imágenes comprimidas antes de subir (máx. ~500 KB) |
| RNF-07 | Rendimiento | Respuesta de la API < 500 ms en endpoints sin IA |
| RNF-08 | Disponibilidad | Si la IA o n8n fallan, la publicación queda "en revisión" para el admin; nada se pierde |
| RNF-09 | Usabilidad | UI en español, pensada para Android de gama media y conexión lenta |
| RNF-10 | Portabilidad | Una sola base de código móvil (Expo) para Android e iOS |
| RNF-11 | Mantenibilidad | API documentada (OpenAPI/Swagger); código en inglés, UI en español |
| RNF-12 | Trazabilidad | Registrar la decisión de la IA (resultado, motivo, modelo) por publicación y solicitud |

## 7. Historias de usuario

Prioridad MoSCoW: M = Must, S = Should, C = Could.

| ID | Historia | Criterios de aceptación | RF | Prioridad |
| --- | --- | --- | --- | --- |
| HU-01 | Como usuario quiero registrarme con correo o Google para usar la app | Crea cuenta y perfil; Google abre el flujo OAuth; sesión persiste al reabrir | RF-01, RF-02 | M |
| HU-02 | Como usuario quiero completar mi perfil con zona y WhatsApp para poder publicar y adoptar | No puede publicar ni solicitar sin zona y WhatsApp válidos | RF-04 | M |
| HU-03 | Como publicador quiero publicar una mascota con fotos para encontrarle hogar | Valida campos y 1 a 5 fotos; queda "en revisión"; respeta el límite del plan | RF-06, RF-07 | M |
| HU-04 | Como publicador quiero saber si mi publicación fue aprobada o rechazada y por qué | Recibe correo con estado y motivo; el estado se ve en "Mis publicaciones" | RF-08, RF-18 | M |
| HU-05 | Como adoptante quiero filtrar el catálogo para encontrar una mascota que me sirva | Solo muestra aprobadas; filtros combinables; paginado | RF-10, RF-11 | M |
| HU-06 | Como adoptante quiero responder un cuestionario y recibir mascotas compatibles | Devuelve hasta 10 mascotas con % y razón; se puede repetir el cuestionario | RF-12, RF-13 | S |
| HU-07 | Como adoptante quiero enviar una solicitud de adopción | Una solicitud activa por mascota y usuario; el publicador la recibe por correo | RF-15, RF-18 | M |
| HU-08 | Como publicador quiero ver un resumen y puntaje de cada solicitud para decidir rápido | Cada solicitud muestra resumen IA y puntaje 0-100; si la IA falla, se ve la solicitud sin resumen | RF-16 | S |
| HU-09 | Como publicador quiero aprobar una solicitud y contactar al adoptante por WhatsApp | Al aprobar, ambos ven el botón de WhatsApp; las demás solicitudes siguen pendientes | RF-17 | M |
| HU-10 | Como publicador quiero marcar la mascota como adoptada | Sale del catálogo y libera cupo del plan | RF-09 | M |
| HU-11 | Como usuario quiero reportar una publicación sospechosa | El reporte llega a la cola del admin | RF-23 | S |
| HU-12 | Como fundación quiero solicitar verificación para tener el badge y el plan Pro | El admin la aprueba en el panel; el badge aparece en sus publicaciones | RF-05, RF-21 | S |
| HU-13 | Como admin quiero revisar la cola de moderación con el motivo de la IA | Lista con motivo y puntaje de riesgo; aprobar o rechazar con motivo | RF-20 | M |
| HU-14 | Como admin quiero importar publicaciones del Instagram de una fundación autorizada | Ingresa el perfil, Stagehand extrae posts, quedan como borradores para revisión | RF-19 | M |
| HU-15 | Como admin quiero ver métricas generales | Totales de publicaciones, solicitudes, adopciones y rechazos | RF-24 | C |
| HU-16 | Como usuario quiero guardar favoritos | Agregar y quitar; lista en el perfil | RF-14 | C |
|  |  |  |  |  |

## 8. Arquitectura y stack

App y panel solo hablan con la API REST; la API es la única que toca Supabase (por JDBC para los datos; el service key solo se usa para Storage), llama a la IA y orquesta n8n y Stagehand.

El login ocurre con el SDK de Supabase Auth en la app (correo o Google); la app envía ese JWT a la API, que lo valida y decide qué puede hacer el usuario. n8n recibe webhooks de la API y le devuelve resultados por endpoints internos con una clave compartida.

| Capa | Tecnología | Hosting sugerido |
| --- | --- | --- |
| Móvil | React Native + Expo (TypeScript) | Expo Go / APK con EAS |
| Panel | React + Vite (TypeScript) | Vercel o Netlify |
| API | Java 21 + Spring Boot (Maven), JPA + Flyway | Render (Docker) |
| Datos | Supabase: Postgres, Auth, Storage | Supabase Cloud |
| Automatización | n8n | n8n Cloud (tu cuenta) |
| Scraping | Stagehand (Node) | Worker aparte en el repo backend; corre en tu PC y le pide trabajos a la API |

## 9. Flujos de IA y automatización

Cinco flujos usan IA o automatización; la IA solo corre ante eventos (crear publicación, enviar solicitud, pedir match) para cuidar los 5 USD.

| Flujo | Disparador | Corre en | Entrada | Salida |
| --- | --- | --- | --- | --- |
| n8n #1 Moderación | Nueva publicación | n8n → IA | Texto y URLs de fotos | `aprobada` o `en revisión` + motivo + riesgo 0-100 |
| n8n #2 Notificaciones | Cambio de estado (publicación o solicitud) | n8n → correo | Evento + destinatario | Correo al usuario |
| Pre-evaluación | Nueva solicitud | API → IA | Solicitud + perfil + ficha de la mascota | Resumen de 3 líneas + puntaje 0-100 |
| Match | Usuario envía el cuestionario | API → IA | Respuestas + mascotas aprobadas (filtradas antes por especie, tamaño y zona) | Hasta 10 mascotas con % y razón |
| Stagehand | Admin lanza importación | Worker → Instagram | Perfil autorizado | Borradores de publicación con texto y fotos |

**Moderación paso a paso**

1. La app crea la publicación vía API; queda `en revisión`.
2. La API envía un webhook a n8n con el id.
3. n8n pide a la IA un JSON con riesgo y motivo (foto genérica de internet, venta encubierta, datos incoherentes).
4. Riesgo bajo: n8n llama a la API para aprobarla. Riesgo alto: queda en la cola del admin con el motivo.
5. n8n dispara el flujo #2 y el publicador recibe el correo.

Skills y agentes de Claude Code para construir todo esto se definen en el siguiente paso (ver Próximos pasos).

## 10. Registro de decisiones

| # | Decisión | Alternativas descartadas | Por qué |
| --- | --- | --- | --- |
| D-01 | Cualquier usuario puede publicar | Solo fundaciones; fundaciones + particulares | Máxima oferta desde el día 1; el riesgo de fraude se cubre con moderación IA y reportes |
| D-02 | Badge de fundación verificada | Sin distinción | Compensa D-01 con una señal de confianza y da valor al plan Pro |
| D-03 | React Native con Expo | Flutter; Kotlin solo Android | Un código para iOS y Android, mismo lenguaje que backend y panel |
| D-04 | Node.js + Express | FastAPI; NestJS | Reemplazada por D-22 |
| D-05 | React + Vite para el panel | Next.js; Angular | Liviano, sin SSR innecesario para un panel interno |
| D-06 | IA: moderación, pre-evaluación y match | Ficha desde foto | Atacan los dos problemas del mercado: fraude y filtro de adoptantes |
| D-07 | Freemium para fundaciones | Donaciones; patrocinios | Ingreso de quienes más publican; el cobro se difiere a v2 |
| D-08 | Solicitud formal + WhatsApp | Chat en la app; solo solicitud | WhatsApp es el canal real en Venezuela; evita construir un chat |
| D-09 | n8n: moderación y notificaciones | Pre-evaluación y seguimiento en n8n | Moderación es asíncrona y tolera latencia; la pre-evaluación queda en el backend |
| D-10 | Stagehand: importar desde Instagram con autorización | Búsqueda inversa de imagen; tests E2E | Resuelve el arranque en frío del catálogo; con autorización y como borrador es defendible |
| D-11 | Perros y gatos, solo Gran Caracas | Otras especies; todo el país | Piloto acotado donde se puede validar con fundaciones reales |
| D-12 | Cobro, donaciones, perdidas y seguimiento a v2 | Mantenerlos en el MVP | Protege el presupuesto de 5 USD y el tiempo de entrega |
| D-13 | Sin Edge Functions; lógica en Express y n8n | Edge Functions de Supabase | Requisito del enunciado |
| D-14 | Plan Gratis: 3 activas; Pro: 30 | Sin límite; límite mensual | Cubre al particular y deja el volumen como razón para pagar, sin abrir la puerta a spam |
| D-15 | Entrega con APK + Expo Go | Publicar en Play Store y App Store | Basta para la evaluación; evita cuentas de desarrollador y revisiones de tienda |
| D-16 | Claude Haiku 5.5 para toda la IA | Gemini 2.5 Flash-Lite; GPT-4o mini | Mismo precio que el más barato, lee imágenes, un solo proveedor para app, n8n y Stagehand |
| D-17 | 1 cuenta = 1 fundación | Organización con miembros | Menos tablas y RLS más simple; suficiente para el MVP |
| D-18 | Catálogo de municipios | Texto libre; municipio + sector | Filtros exactos por zona sin normalizar texto |
| D-19 | RLS solo de lectura; escritura solo por API | Sin RLS; políticas de escritura por rol | La anon key va en el APK; RLS evita acceso directo y mantiene a la API como única autoridad |
| D-20 | Bucket público + subida por URL firmada | Bucket privado con lectura firmada | Las fotos ya son públicas en el catálogo; menos latencia y código |
| D-21 | Construir hoy en 8 mini-sprints con recortes definidos | Sprints de varios días | La entrega es hoy; el orden por dependencias evita bloqueos |
| D-22 | Backend en Java 21 + Spring Boot | Node.js + Express (D-04) | Cambio de requisito; Spring Security, JPA y Flyway cubren JWT, datos y migraciones con librerías estándar |
| D-23 | Dos repos: petzo-backend y petzo-frontend | Monorepo | Stacks distintos (Java vs TypeScript), deploys independientes, contexto más chico para Claude Code |
| D-24 | Stagehand como worker Node que consulta trabajos pendientes a la API | La API llama al worker; worker en Render | Stagehand no tiene SDK Java; al consultar la API, el worker puede correr en tu PC sin exponer puertos y sin cargar Chromium en un Render gratis |
| D-25 | JDBC + JPA + Flyway contra Postgres de Supabase | PostgREST con service_role; supabase/migrations | Lo idiomático en Spring; el esquema queda versionado junto al código que lo usa |
| D-26 | Trigger SQL crea el perfil al registrarse | Crear el perfil en el primer /me | Todo usuario de Auth tiene perfil siempre, sin carreras entre requests |
| D-27 | Backend completo y probado antes del frontend | Construir app y API en paralelo | El frontend consume un contrato estable (Swagger) desplegado; evita rehacer pantallas |

## 11. Riesgos y próximos pasos

| Riesgo | Impacto | Mitigación |
| --- | --- | --- |
| Instagram bloquea a Stagehand o pide login | La demo de importación falla | Probar con perfiles públicos; guardar una corrida exitosa como evidencia; reintento manual desde el panel |
| Se agotan los 5 USD de IA | Moderación y match se detienen | Modelo barato, salida JSON corta, IA solo en eventos; fallback a revisión manual (RNF-08) |
| Publicaciones falsas pese a la IA | Pérdida de confianza | Reportes de usuarios + cola de admin + badge de verificadas |
| Webhook de n8n caído | Publicaciones sin moderar | Quedan "en revisión"; el admin las ve igual |
| Alcance crece durante el desarrollo | No se termina | Este documento es el contrato: lo nuevo va a v2 |

**Próximos pasos**

- [ ] Preguntas abiertas resueltas (D-14, D-15)
- [ ] Modelo de IA: Claude Haiku 5.5 (§12, D-16)
- [ ] Skills y agentes: definidos en el prompt de Claude Code (§17)
- [ ] Modelo de datos y RLS (§13 a §15)
- [ ] Diseño de pantallas (Cris, con su agente de diseño, antes de las 15:30)
- [ ] Plan de construcción de hoy (§16)

## 12. Modelo de IA y proveedor

Usaremos **Claude Haiku 5.5** (`claude-haiku-5-5`) de Anthropic para todas las tareas de IA: moderación, pre-evaluación, match y Stagehand. Cuesta 0,10 USD por millón de tokens de entrada y 0,50 USD por millón de salida en prompts de hasta 100.000 tokens ([Claude Platform Docs vía VKTR](https://www.vktr.com/ai-platforms/anthropic-cuts-smallmodel-pricing-90-with-claude-haiku-55/)).

| Modelo | Entrada / salida (USD por 1M) | Lee imágenes | Por qué sí o no |
| --- | --- | --- | --- |
| **Claude Haiku 5.5** | 0,10 / 0,50 | Sí | Elegido: mismo ecosistema que Claude Code, nodo nativo en n8n, Stagehand lo soporta |
| Gemini 2.5 Flash-Lite | 0,10 / 0,40 ([fuente](https://anotherwrapper.com/llm-pricing/gemini-2.5-flash-lite)) | Sí | Casi igual de barato; obligaba a manejar un segundo proveedor |
| GPT-4o mini | 0,15 / 0,60 ([fuente](https://anotherwrapper.com/llm-pricing/gpt-4o-mini-2024-07-18)) | Sí | Más caro y modelo más viejo |

**Costo estimado por llamada** (aproximado, prompts cortos con salida JSON):

| Tarea | Tokens aprox. (entrada / salida) | USD por llamada | Llamadas con 5 USD |
| --- | --- | --- | --- |
| Moderación (texto + hasta 3 fotos reducidas) | 4.000 / 150 | ~0,0005 | ~10.000 |
| Pre-evaluación de solicitud | 1.500 / 200 | ~0,00025 | ~20.000 |
| Match (hasta 20 candidatas) | 2.500 / 600 | ~0,00055 | ~9.000 |
| Importación Stagehand (un perfil) | 100.000 / 5.000 | ~0,013 | ~380 |

El presupuesto alcanza de sobra. Igual dejamos un tope diario en la API (se suman tokens desde `ai_logs`) y, si se pasa, la IA se apaga y todo cae en revisión manual.

Notas para el desarrollo: Haiku 5.5 rechaza parámetros de muestreo como `temperature` y el _prefill_ del asistente, que sí aceptaba Haiku 4.5 ([VKTR](https://www.vktr.com/ai-platforms/anthropic-cuts-smallmodel-pricing-90-with-claude-haiku-55/)). Su tokenizador genera cerca de 30% más tokens que el de Haiku 4.5, y eso ya está en las estimaciones. Desde Spring Boot se llama con el SDK oficial de Anthropic para Java.

## 13. Modelo de datos

Son 10 tablas alrededor de `pets` y `profiles`. Las decisiones que lo simplifican: la fundación es un tipo de perfil, la zona es un catálogo y los estados son enums.

`favorites` es N:M entre usuarios y mascotas. `ai_logs` apunta a mascotas o solicitudes por `entity_type` + `entity_id`, sin FK, para registrar cualquier tarea de IA en una sola tabla.

## 14. Tablas, columnas y por qué

Son 10 tablas en Postgres. Todos los ids son `uuid`, salvo `municipalities`, que usa `smallint`. Todas llevan `created_at`, y `pets`, `profiles` y `adoption_requests` también `updated_at`.

| Tabla | Columnas clave | Relación | Por qué existe así |
| --- | --- | --- | --- |
| `municipalities` | id, name, state | 1:N con profiles y pets | Catálogo fijo para que los filtros por zona sean exactos (Libertador, Chacao, Baruta, Sucre, El Hatillo, Vargas, Los Salias, Carrizal, Guaicaipuro, Plaza, Zamora) |
| `profiles` | id (= auth.users.id), full_name, avatar_url, whatsapp, municipality_id, role (user/admin), account_type (person/foundation), foundation_name, foundation_instagram, foundation_bio, verification_status (none/pending/verified/rejected), plan (free/pro), is_suspended | 1:1 con auth.users | 1 cuenta = 1 fundación (D-17): la fundación es un tipo de perfil, sin tabla de organizaciones ni miembros |
| `lifestyle_profiles` | user_id (PK), answers (jsonb) | 1:1 con profiles | El cuestionario se llena una vez y sirve para el match y para precargar las solicitudes; va en jsonb porque las preguntas pueden cambiar sin migración |
| `pets` | id, owner_id, species (dog/cat), name, age_months, sex, size (small/medium/large), municipality_id, vaccinated, sterilized, description, status (draft/pending_review/approved/rejected/paused/adopted), moderation_reason, risk_score, source (app/instagram), source_url | N:1 con profiles y municipalities | Entidad central. Un solo campo `status` gobierna el ciclo de vida, la moderación y el límite del plan |
| `pet_photos` | id, pet_id, storage_path, position | N:1 con pets (cascade) | Tabla aparte y no un array: permite ordenar, borrar una foto y validar de 1 a 5 |
| `adoption_requests` | id, pet_id, adopter_id, status (pending/approved/rejected/withdrawn), answers (jsonb), motivation, ai_summary, ai_score, decided_at | N:1 con pets y profiles | Índice único parcial `(pet_id, adopter_id) WHERE status = 'pending'`: una solicitud activa por mascota y adoptante |
| `favorites` | user_id, pet_id (PK compuesta) | N:M profiles-pets | La PK compuesta evita duplicados sin lógica extra |
| `reports` | id, pet_id, reporter_id, reason, status (open/resolved) | N:1 con pets y profiles | Cola de reportes para el admin |
| `ai_logs` | id, task (moderation/pre_evaluation/match/import), entity_type, entity_id, model, input_tokens, output_tokens, result (jsonb) | Polimórfica (sin FK) | Trazabilidad de cada decisión de IA (RNF-12) y control del gasto de 5 USD |
| `import_jobs` | id, instagram_handle, requested_by, status (queued/running/done/failed), pets_created, error | N:1 con profiles | Registra cada corrida de Stagehand; el panel muestra su progreso |

Los estados son `enum` de Postgres y no texto libre, para que la base rechace valores inválidos.

No se guarda el resultado del match: se calcula al pedirlo y queda registrado en `ai_logs`, porque cambia cada vez que entra o sale una mascota.

El límite del plan (3 o 30 activas) lo valida la API, contando `pets` con estado `pending_review` o `approved` del dueño. Es una regla de negocio y por eso vive en el backend. Al registrarse un usuario, un trigger SQL sobre auth.users crea su fila en profiles con el nombre y el avatar de Google; es SQL puro, no una Edge Function.

## 15. RLS y Storage

RLS va activo en las 10 tablas, con políticas solo de lectura y **ninguna de escritura**. Todo lo que se escribe pasa por la API Spring Boot, que se conecta a Postgres por JDBC con el rol `postgres` (que se salta RLS). El `service_role` key solo lo usa el backend para la API de Storage.

**Por qué si la app no consulta la base directo:** la app necesita la `anon key` para el login con Supabase Auth, y esa clave viaja dentro del APK. Sin RLS, cualquiera que la extraiga podría leer o modificar la base con PostgREST. RLS cierra esa puerta y la API queda como única vía de escritura (RNF-01, RNF-02).

| Tabla | Política SELECT | Rol | Escritura |
| --- | --- | --- | --- |
| `municipalities` | `true` | anon, authenticated | Solo migración/seed |
| `pets` | `status = 'approved' OR owner_id = auth.uid()` | anon, authenticated | Solo API |
| `pet_photos` | existe un `pets` visible con la misma regla | anon, authenticated | Solo API |
| `profiles` | `id = auth.uid()` | authenticated | Solo API |
| `lifestyle_profiles` | `user_id = auth.uid()` | authenticated | Solo API |
| `favorites` | `user_id = auth.uid()` | authenticated | Solo API |
| `adoption_requests` | `adopter_id = auth.uid()` o el usuario es dueño de la mascota | authenticated | Solo API |
| `reports`, `ai_logs`, `import_jobs` | sin política (nadie fuera de la API) | — | Solo API |

**Datos públicos del publicador:** RLS filtra filas, no columnas. Por eso `profiles` no es legible para otros, y la API expone una vista recortada (nombre, municipio, badge). El WhatsApp solo sale en la respuesta cuando existe una solicitud aprobada entre ambos (RNF-04).

**Admin:** la API verifica `profiles.role = 'admin'` en cada ruta `/admin/*`. Se descartó usar _custom claims_ en el JWT porque requieren un hook de Auth y no aportan nada a un solo rol de admin.

**Storage**

| Bucket | Lectura | Subida | Ruta |
| --- | --- | --- | --- |
| `pet-photos` (público) | Cualquiera con la URL | Solo con URL firmada que emite la API (`Storage upload/sign`) | `pets/{pet_id}/{uuid}.jpg`, `avatars/{user_id}.jpg` |

Se eligió bucket público porque las fotos son el contenido público del catálogo, y así no hace falta firmar cada lectura (menos latencia, menos código). El costo es que la foto de una publicación rechazada sigue accesible si alguien tiene la URL; para cubrirlo, la API borra esas fotos al rechazar.

## 16. Plan de construcción (hoy, desde las 16:15)

Dos repos (D-23):

- `petzo-backend`: API Spring Boot, migraciones Flyway, `stagehand-worker/` (Node), `n8n/` (flujos exportados en JSON), `http/` (colección de pruebas).
- `petzo-frontend`: `apps/mobile` (Expo) y `apps/admin` (React + Vite).

**Regla de paso:** el frontend no arranca hasta que la API esté desplegada y todas sus pruebas pasen (D-27).

| Sprint | Hora | Entregable | Listo cuando |
| --- | --- | --- | --- |
| B0 Supabase + modelo | 16:15–17:00 | Proyecto Supabase, Google OAuth, bucket `pet-photos`; repo backend con Spring Initializr; migración Flyway V1 (enums, 10 tablas, índices, trigger de perfiles, RLS, seed de municipios) | La migración corre en Supabase y un registro nuevo crea su perfil |
| B1 API core | 17:00–18:30 | Seguridad JWT de Supabase; perfiles; CRUD de mascotas y fotos (URLs firmadas); catálogo con filtros; límite de plan; endpoints admin; Swagger | Tests de controladores y servicios en verde |
| B2 Solicitudes + IA | 18:30–19:15 | Solicitudes, aprobar/rechazar, contacto WhatsApp; pre-evaluación y match con Haiku (SDK Java); `ai_logs` y tope diario | Una solicitud real devuelve resumen y puntaje |
| B3 Automatizaciones | 19:15–20:00 | Flujos n8n 1 y 2 + endpoints `/internal/*`; worker Stagehand + `import_jobs` | Publicar → moderación automática + correo; una importación crea borradores |
| B4 API lista | 20:00–20:30 | Suite completa de tests; deploy en Render (Docker); colección `.http` corrida contra la URL pública | **Paso al frontend** |
| F1 App móvil | 20:30–22:00 | Expo: auth, perfil, catálogo, detalle, publicar, solicitudes, match | Flujo completo en Expo Go contra producción |
| F2 Panel | 22:00–22:45 | React + Vite: moderación, fundaciones, usuarios, importación | El admin aprueba una publicación marcada |
| F3 Entrega | 22:45–23:30 | APK con EAS, prueba de punta a punta, READMEs y evidencias | APK instalado y funcionando |

**En paralelo (tú):** el diseño de pantallas tiene que estar listo antes de las 20:30.

**Recortes si vamos atrasados, en este orden:** HU-15 dashboard → HU-16 favoritos → HU-11 reportes → HU-06 match. No se recortan moderación, notificaciones ni Stagehand.

**Riesgos de tiempo:**

- La conexión directa a Postgres de Supabase es solo IPv6. Desde Render hay que usar la cadena del **session pooler** (Supavisor, puerto 5432), que sí acepta IPv4.
- Render gratis da 512 MB y se duerme: limitar el heap (`-Xmx350m`) y despertar la API antes de la demo. El arranque en frío de Spring tarda cerca de un minuto.
- El build de EAS puede pasar 20 a 40 minutos en cola: lanzarlo apenas la app funcione.
- Google OAuth en Expo necesita bien configurado el _redirect_. Se configura en B0 y se prueba al inicio de F1.

## 17. Prompt para Claude Code (backend)

Este prompt es solo para el repo `petzo-backend`. El del frontend se arma cuando la API pase B4, con la URL de producción y el Swagger reales.

Antes de usarlo, exporta este documento como Markdown a `docs/SPEC.md` dentro de `petzo-backend`. Después pega el prompt, y en cada sprint escribe solo "Arranca B1", "Arranca B2", etc.

```

```

**Por qué está escrito así:**

- Un prompt por repo: Claude Code carga solo el contexto de ese repo y así se gastan menos tokens.
- La lista de endpoints y los flujos fijan el contrato que después consume el frontend.
- Los logs `SKILLS_LOG.md` y `AGENTS_LOG.md` quedan en el repo y te sirven como evidencia para explicar qué skills y agentes usaste y por qué.

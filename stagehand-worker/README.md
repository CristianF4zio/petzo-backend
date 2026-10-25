# Worker de Stagehand (importación de fundaciones)

Abre en un Chrome real la página de adopción de una fundación, lee sus mascotas con Stagehand + IA y las manda a la API como **borradores** (`source = instagram`, la columna conserva su nombre). El admin las revisa y la fundación las publica desde la app. El worker **nunca toca Supabase**: reclama trabajos y entrega resultados por `/internal/*` con el header `x-internal-secret` (D-24).

| Qué | Valor |
| --- | --- |
| Código | `stagehand-worker/worker.mjs` (flujo) y `stagehand-worker/openrouter.mjs` (callback del modelo, de la guía del reto) |
| Versión | `@browserbasehq/stagehand` **4.3.0** (fijada), `zod ~4.4.3`, `openai`, `dotenv`; Node ≥ 22.18 y Chrome instalado |
| Modelo | `openai/gpt-4.1-mini` vía OpenRouter (D-32, comparación abajo) |
| Sitio | Página propia y ficticia: `GET /demo/fundaciones/patas-del-avila.html` servida por la API (`src/main/resources/static/demo/`). Instagram se descartó: exige login y sus términos prohíben el scraping |
| Resultado esperado | 4 borradores (Luna, Toby, Canela, Simón) con especie, sexo, tamaño, edad en meses, descripción y su foto subida a Storage por la API |

## Correr

```bash
cp .env.example .env        # OPENROUTER_API_KEY (llega por aparte), INTERNAL_SECRET = el del backend
npm ci
npm run dry -- patas-del-avila   # solo lee la página y registra el resultado; no escribe nada
npm run once                     # procesa 1 trabajo en cola y termina
npm start                        # consulta /internal/imports/next cada 10 s
```

Para encolar un trabajo: panel admin → **Importaciones** → identificador `patas-del-avila` (o `POST /api/admin/imports {"instagramHandle":"patas-del-avila"}` con un token de admin). `HEADLESS=false` muestra la ventana del navegador.

## Cómo cuida el saldo y evita errores

- Instrucciones atómicas: `goto` por código → espera `#adopcion article` (10 s máx.) → captura → **un** `extract` con esquema Zod.
- Límites: 120 s por corrida, 2 intentos de `extract` (solo lectura) con espera creciente, sin bucles que consulten al modelo. El cliente corta cada llamada a 30 s y no reintenta.
- Antes de cada trabajo lee el saldo con `GET https://openrouter.ai/api/v1/key` (la clave nunca se imprime) y se detiene si quedan menos de $0.25.
- Valida lo que devuelve el modelo: descarta fichas sin nombre o sin foto y resuelve las URLs de foto contra la página. La API vuelve a validar y solo descarga fotos de los hosts de `IMPORT_ALLOWED_HOSTS`.
- Cada paso queda en `evidencia/runs.log` (hora, URL, paso, modelo, duración, tokens, resultado). La carpeta `evidencia/` está fuera de git; la evidencia limpia de la entrega está en `../docs/evidencia/stagehand/`.

## Elección del modelo (25-oct-2026, misma página, `npm run dry`)

| Modelo (OpenRouter) | Precio entrada / salida por 1M | Aciertos | Tokens entrada / salida | Tiempo de `extract` | Costo |
| --- | --- | --- | --- | --- | --- |
| **openai/gpt-4.1-mini** | $0.40 / $1.60 | 4/4 fichas, todos los campos | 1 506 / 251 | 7,9 s | **$0.001004** (leído de `/api/v1/key`) |
| google/gemini-2.5-flash | $0.30 / $2.50 | 4/4 fichas, todos los campos | 1 299 / 314 | 6,5 s | ≈ $0.0012 (estimado con tokens × tarifa) |

Se eligió **gpt-4.1-mini**: mismo acierto, salida estructurada estricta (`json_schema`) sin tokens de razonamiento y menor costo por corrida en esta tarea, que es de salida corta. Claude Haiku 4.5 ($1.00 / $5.00) no se probó: costaría unas 3 veces más sin una falla que lo justifique. Saldo tras las pruebas: $4.998996 de $5.

Evidencia: `docs/evidencia/stagehand/runs.log` y las capturas `patas-del-avila-*.png` (página real que vio el navegador).

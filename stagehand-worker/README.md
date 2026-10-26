# Worker de Stagehand (importación de fundaciones)

Abre en un Chrome real la página de una fundación, pulsa "Ver mascotas en adopción" (`observe` + `act`), lee sus mascotas (`extract`) con Stagehand + IA y las manda a la API como **borradores** (`source = instagram`, la columna conserva su nombre). El admin las revisa y la fundación las publica desde la app. El worker **nunca toca Supabase**: reclama trabajos y entrega resultados por `/internal/*` con el header `x-internal-secret` (D-24).

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

- Flujo con las tres operaciones de la guía, en pasos atómicos:
  1. `goto` por código y espera a que el botón `#ver-adopcion` sea visible (10 s máx.).
  2. **`observe`** "Encuentra el botón para ver las mascotas en adopción". El código **comprueba** que la acción propuesta sea un `click` sobre ese botón antes de ejecutarla.
  3. **`act`** con esa acción y **verificación del estado** por código: la lista `#adopcion` queda visible con fichas. El clic es de solo lectura y nunca se repite a ciegas: si `act` falla o vence su tiempo, decide el estado de la página.
  4. Captura y **`extract`** con esquema Zod.
- Límites: 120 s por corrida; 30 s por llamada al modelo, sin reintentos del cliente; **máximo 6 llamadas al modelo por corrida** (una corrida normal usa 3); `observe` y `extract` se reintentan como máximo 2 veces con espera creciente y la causa queda en el log. Ningún bucle consulta al modelo: el polling (cola de trabajos y estado de la página) es código.
- Saldo: antes de cada corrida lee `GET https://openrouter.ai/api/v1/key` (la clave nunca se imprime). Se detiene si no puede leerlo, si la clave **no tiene límite de gasto** o si quedan menos de $0.25 (tope por corrida). El log registra el costo de la corrida y el saldo disponible (`saldoUsd`).
- El contenido de la página no puede cambiar las instrucciones: estas son fijas en el código, la acción se valida antes de ejecutarla y los datos se comprueban contra el DOM.
- Valida lo que devuelve el modelo: descarta fichas sin nombre o sin foto y resuelve las URLs de foto contra la página. La API vuelve a validar y solo descarga fotos de los hosts de `IMPORT_ALLOWED_HOSTS`.
- Cada paso queda en `evidencia/runs.log` (hora, URL, paso, modelo, proveedor que eligió OpenRouter, modo headless, duración, tokens, resultado). Si algo falla se guarda una captura `*-fallo.png` de la página en ese momento y el error en el log (probado con una página inexistente: `npm run dry -- no-existe` corta a los 10 s esperando el botón, sin llamar al modelo). La carpeta `evidencia/` está fuera de git; la evidencia limpia de la entrega está en `../docs/evidencia/stagehand/`.

## Elección del modelo (25-oct-2026, misma página, `npm run dry`)

| Modelo (OpenRouter) | Precio entrada / salida por 1M | Aciertos | Tokens entrada / salida | Tiempo de `extract` | Costo |
| --- | --- | --- | --- | --- | --- |
| **openai/gpt-4.1-mini** | $0.40 / $1.60 | 4/4 fichas, todos los campos | 1 506 / 251 | 7,9 s | **$0.001004** (leído de `/api/v1/key`) |
| google/gemini-2.5-flash | $0.30 / $2.50 | 4/4 fichas, todos los campos | 1 299 / 314 | 6,5 s | ≈ $0.0012 (estimado con tokens × tarifa) |

Se eligió **gpt-4.1-mini**: mismo acierto, salida estructurada estricta (`json_schema`) sin tokens de razonamiento y menor costo por corrida en esta tarea, que es de salida corta. Claude Haiku 4.5 ($1.00 / $5.00) no se probó: costaría unas 3 veces más sin una falla que lo justifique. Saldo tras las pruebas: $4.998996 de $5.

**Proveedor:** OpenRouter enruta `openai/gpt-4.1-mini` a **OpenAI** (campo `provider` de la respuesta, registrado en el log desde el 26-oct).

**Flujo observe → act → extract (26-oct-2026):** con ventana y headless dio el mismo resultado. `observe` propuso "click · Botón para ver las mascotas en adopción", `act` abrió la lista y `extract` leyó 4/4 fichas. Fueron 3 llamadas, 1 979 tokens de entrada y 275 de salida, **$0.001227** por corrida; saldo $4.99 de $5. Comprobé las 4 fichas contra la captura (`patas-del-avila-observe-act-*.png`).

**Con ventana vs. headless (26-oct-2026, versión anterior con solo `extract`):** el mismo resultado en ambos modos, 4/4 fichas con los mismos campos y fotos (1 449 tokens de entrada, 229–235 de salida). Con ventana el `extract` tardó 5,2 s y headless 9,6 s; la diferencia es de la llamada al modelo, no de la página. Costo por corrida ≈ $0.00095. Saldo usado en total: $0.0075 de $5.

Evidencia: `docs/evidencia/stagehand/runs.log`, las capturas `patas-del-avila-*.png` (página real que vio el navegador) y `no-existe-*-fallo.png` (captura de una corrida fallida).

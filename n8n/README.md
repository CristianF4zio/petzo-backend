# Flujos n8n de Petzo

Dos flujos. La API los llama por webhook; ambos exigen el header `x-internal-secret` (mismo valor que `INTERNAL_SECRET` del backend).

| Flujo | Archivo | Webhook | Qué hace |
| --- | --- | --- | --- |
| 1 Moderación | `moderation.json` | `POST /webhook/petzo-moderation` | Manda texto + hasta 3 fotos a `openai/gpt-4.1-mini` por OpenRouter, recibe `{risk, reason}` y llama a `POST /internal/pets/{id}/moderation-result`. Riesgo < 40 → aprobada; si no, queda en la cola del admin con el motivo. |
| 2 Notificaciones | `notifications.json` | `POST /webhook/petzo-notify` | Envía el correo que arma la API (`to`, `subject`, `text`). |

## Importar (una vez)

1. n8n → **Workflows → Import from file** → `moderation.json`; repite con `notifications.json`.
2. Crea 3 credenciales (**Credentials → New**):
   - **Petzo internal secret** (tipo *Header Auth*): Name `x-internal-secret`, Value = `INTERNAL_SECRET` del backend.
   - **OpenRouter API key** (tipo *Header Auth*): Name `Authorization`, Value = `Bearer <clave de OpenRouter>`.
   - **Petzo SMTP (Ethereal)** (tipo *SMTP*): cuenta de desarrollo de [ethereal.email](https://ethereal.email) (*Create Ethereal Account*): host `smtp.ethereal.email`, puerto `587`, SSL/TLS apagado, usuario y contraseña los que da Ethereal. Atrapa los correos sin entregarlos (se ven en su *Mailbox*), así no se usa una cuenta personal. Alternativa: Gmail con [contraseña de aplicación](https://myaccount.google.com/apppasswords), host `smtp.gmail.com`, puerto `465`, SSL.
3. En **Petzo 1**, nodo *Petzo callback*: reemplaza `https://REPLACE-WITH-PETZO-API` por la URL del túnel (ver abajo).
4. En **Petzo 2**, nodo *Send email*: reemplaza el remitente.
5. Activa ambos flujos y copia sus **Production URL** en el `.env` del backend:
   `N8N_MODERATION_WEBHOOK_URL=...` y `N8N_NOTIFY_WEBHOOK_URL=...`

## Si algo falla

Nada se pierde: si n8n o la IA no responden, la publicación sigue en `pending_review` y aparece en `GET /api/admin/moderation`. Resultados viejos (la mascota se editó después) se ignoran comparando `version` (la API la sube en cada cambio, incluidas las fotos). Los tokens de cada moderación se registran en `ai_logs`.

## Ambiente: n8n staging + API local (D-34)

El reto usa `https://staging-n8n.dir5ad.easypanel.host` (solo tus propios workflows; nada de credenciales ajenas ni nodos que lean variables de entorno). La API corre en tu PC, así que n8n la alcanza por un túnel gratis de Cloudflare:

```bash
cloudflared tunnel --no-autoupdate --url http://localhost:8090
# imprime https://<algo>.trycloudflare.com → pégala en el nodo "Petzo callback" (cambia en cada arranque)
```

Después de cambiar la URL en un flujo **activo**, desactívalo y actívalo de nuevo: n8n sigue ejecutando la versión publicada hasta entonces. Si la red cambia (otra IP o wifi), el túnel rápido muere ("Tunnel not found") y hay que levantarlo otra vez; comprueba que su nombre ya resuelva en DNS público antes de probar (`https://dns.google/resolve?name=<host>`).

`/internal/**` sigue protegido por `x-internal-secret` aunque el túnel sea público. El nodo *Parse result* es un Code node que solo parsea la respuesta del modelo (no lee variables del servidor).

## Disparar y dejar evidencia

1. Activa ambos flujos y pon sus **Production URL** en `petzo-backend/.env` (`N8N_MODERATION_WEBHOOK_URL`, `N8N_NOTIFY_WEBHOOK_URL`); reinicia la API.
2. **Flujo 1:** publica una mascota desde la app (o `PATCH /api/pets/{id}/status {"status":"pending_review"}`). En n8n → *Executions* verás la ejecución; la mascota pasa a `approved` (riesgo < 40) o queda en la cola del admin con el motivo.
3. **Flujo 2:** aprobar/rechazar una mascota o una solicitud envía el correo; aparece en *Executions* y llega al buzón del dueño.
4. Evidencia ya guardada: `docs/evidencia/n8n/flujo1-moderacion-ejecucion-417.json` (resumen de la ejecución del flujo 1 exportado con `GET /api/v1/executions/417` de la API pública de n8n; se omitieron headers, IPs y credenciales).
5. Evidencia sin secretos: captura de *Executions* (sin abrir credenciales) y exporta cada flujo con **Download** (n8n no incluye credenciales en el JSON; revisa igual que no haya URLs privadas antes de subirlo).

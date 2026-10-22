# Flujos n8n de Petzo

Dos flujos. La API los llama por webhook; ambos exigen el header `x-internal-secret` (mismo valor que `INTERNAL_SECRET` del backend).

| Flujo | Archivo | Webhook | Qué hace |
| --- | --- | --- | --- |
| 1 Moderación | `moderation.json` | `POST /webhook/petzo-moderation` | Manda texto + hasta 3 fotos a Claude Haiku 5.5, recibe `{risk, reason}` y llama a `POST /internal/pets/{id}/moderation-result`. Riesgo < 40 → aprobada; si no, queda en la cola del admin con el motivo. |
| 2 Notificaciones | `notifications.json` | `POST /webhook/petzo-notify` | Envía el correo que arma la API (`to`, `subject`, `text`). |

## Importar (una vez)

1. n8n → **Workflows → Import from file** → `moderation.json`; repite con `notifications.json`.
2. Crea 3 credenciales (**Credentials → New**):
   - **Petzo internal secret** (tipo *Header Auth*): Name `x-internal-secret`, Value = `INTERNAL_SECRET` del backend.
   - **Anthropic API key** (tipo *Header Auth*): Name `x-api-key`, Value = tu clave de Anthropic.
   - **Petzo SMTP** (tipo *SMTP*): servidor de correo (p. ej. Gmail con contraseña de aplicación o Resend SMTP).
3. En **Petzo 1**, nodo *Petzo callback*: reemplaza `REPLACE-WITH-PETZO-API` por la URL pública de la API (Render).
4. En **Petzo 2**, nodo *Send email*: reemplaza el remitente.
5. Activa ambos flujos y copia sus **Production URL** en el `.env` del backend:
   `N8N_MODERATION_WEBHOOK_URL=...` y `N8N_NOTIFY_WEBHOOK_URL=...`

## Si algo falla

Nada se pierde: si n8n o la IA no responden, la publicación sigue en `pending_review` y aparece en `GET /api/admin/moderation`. Resultados viejos (la mascota se editó después) se ignoran comparando `updatedAt`. Los tokens de cada moderación se registran en `ai_logs`.

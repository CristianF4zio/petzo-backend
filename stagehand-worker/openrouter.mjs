// Stagehand v4 model callback → OpenRouter Chat Completions (from the challenge's Stagehand guide).
// Asks for JSON per the schema and fails if the model doesn't return it: no hidden retries, no loops.
import "dotenv/config";
import OpenAI from "openai";

if (!process.env.OPENROUTER_API_KEY) throw Error("Falta OPENROUTER_API_KEY");
if (!process.env.OPENROUTER_MODEL) throw Error("Falta OPENROUTER_MODEL");

const client = new OpenAI({
  apiKey: process.env.OPENROUTER_API_KEY,
  baseURL: "https://openrouter.ai/api/v1",
  timeout: 30000,
  maxRetries: 0,
});

// Tokens of the current run (the worker resets it per job), and the provider OpenRouter routed to
export const usage = { calls: 0, input: 0, output: 0, provider: null };
// Hard cap of model calls per run: observe + extract with their retries fit in 6; more means something is looping
const MAX_CALLS = 6;

export async function generate(params) {
  if (usage.calls >= MAX_CALLS) throw Error(`Tope de ${MAX_CALLS} llamadas al modelo por corrida`);
  usage.calls += 1; // counted before the call: a failed call can also cost
  const f = params.responseFormat;
  if (f?.type !== "json_schema") throw Error("Se esperaba JSON schema");
  const messages = params.messages.map((m) => ({
    role: m.role,
    content: (Array.isArray(m.content) ? m.content : [m.content]).map((b) =>
      b.type === "text" ? { type: "text", text: b.text } : { type: "image_url", image_url: { url: `data:${b.mimeType};base64,${b.data}` } },
    ),
  }));
  if (params.systemPrompt) messages.unshift({ role: "system", content: params.systemPrompt });
  const r = await client.chat.completions.create({
    model: process.env.OPENROUTER_MODEL,
    messages,
    temperature: params.temperature,
    response_format: { type: "json_schema", json_schema: { name: f.name, schema: f.schema, strict: true } },
  });
  usage.input += r.usage?.prompt_tokens ?? 0;
  usage.output += r.usage?.completion_tokens ?? 0;
  usage.provider = r.provider ?? usage.provider;
  const text = r.choices[0]?.message.content;
  if (!text) throw Error("El modelo no devolvió contenido");
  return { role: "assistant", content: { type: "text", text }, outputFormat: "json_schema", structuredContent: JSON.parse(text) };
}

/** Spend so far on this key (USD), read from OpenRouter itself; the key is never printed. */
export async function keyUsage() {
  const res = await fetch("https://openrouter.ai/api/v1/key", { headers: { Authorization: `Bearer ${process.env.OPENROUTER_API_KEY}` } });
  if (!res.ok) throw Error(`No se pudo leer el saldo de la clave (${res.status})`);
  const { data } = await res.json();
  return { usage: data.usage, limit: data.limit, limitRemaining: data.limit_remaining };
}

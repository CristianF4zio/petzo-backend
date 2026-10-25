// Petzo import worker (D-24, D-32): claims a queued import from the API, opens the foundation's adoption page in a real
// local Chrome, extracts the pets with Stagehand + OpenRouter and sends them back to the API as drafts. It never
// touches Supabase. Usage: `npm start` (polls every 10 s) · `npm run once` (one job, then exit) ·
// `npm run dry -- patas-del-avila` (extract only, nothing is sent; for model comparison).
import { localBrowser, Stagehand } from "@browserbasehq/stagehand";
import { z } from "zod/v4";
import { appendFile, mkdir } from "node:fs/promises";
import { generate, keyUsage, usage } from "./openrouter.mjs";

const API = process.env.PETZO_API_URL ?? "http://localhost:8090";
const SITE = process.env.DEMO_SITE_URL ?? `${API}/demo/fundaciones`;
const SECRET = process.env.INTERNAL_SECRET ?? "";
const HEADLESS = process.env.HEADLESS !== "false";
const MAX_RUN_MS = 120_000; // whole job, browser included
const MAX_EXTRACT_TRIES = 2; // reads only; never retried blindly after a write
const MAX_COST_USD = 0.25; // per-run budget: stop before extracting if the key already spent this much more

const Pet = z.object({
  name: z.string().describe("Nombre de la mascota"),
  species: z.enum(["dog", "cat"]).describe("dog si es perro o perra, cat si es gato o gata"),
  sex: z.enum(["male", "female"]),
  size: z.enum(["small", "medium", "large"]).describe("pequeño=small, mediano=medium, grande=large"),
  ageMonths: z.number().int().min(0).max(360).describe("Edad aproximada en meses (1 año = 12)"),
  description: z.string().describe("Descripción de la ficha"),
  photoUrl: z.string().describe("Valor del atributo src de la imagen de la ficha"),
});
const Page = z.object({ pets: z.array(Pet).max(10) });

const log = async (entry) => {
  const line = JSON.stringify({ at: new Date().toISOString(), model: process.env.OPENROUTER_MODEL, ...entry });
  console.log(line);
  await appendFile("evidencia/runs.log", line + "\n");
};

async function api(path, init = {}) {
  const res = await fetch(API + path, { ...init, headers: { "Content-Type": "application/json", "x-internal-secret": SECRET, ...(init.headers ?? {}) } });
  if (!res.ok && res.status !== 204) throw Error(`${init.method ?? "GET"} ${path} → ${res.status}`);
  return res.status === 204 ? null : res.json();
}

/** Opens the page and extracts its pets. Returns pets with absolute photo URLs, validated for the business. */
async function extract(slug) {
  if (!/^[a-z0-9-]{1,40}$/.test(slug)) throw Error(`Identificador inválido: ${slug}`);
  const url = `${SITE}/${slug}.html`;
  const startedAt = Date.now();
  const browser = await localBrowser.launch({ headless: HEADLESS });
  let stagehand;
  try {
    stagehand = await Stagehand.create({ browser, model: { generate } });
    const [page] = await browser.context.pages();
    if (!page) throw Error("No hay página activa");
    await page.goto(url);
    await log({ step: "goto", url });
    // Visible condition before extracting, with a limit (no blind sleeps)
    await page.waitForSelector("#adopcion article", { timeout: 10_000 });
    const shot = `evidencia/${slug}-${startedAt}.png`;
    await page.screenshot({ path: shot });
    await log({ step: "screenshot", file: shot });

    let data;
    for (let i = 1; i <= MAX_EXTRACT_TRIES && !data; i++) {
      try {
        ({ data } = await stagehand.extract("Lee todas las fichas de mascotas en adopción de esta página. Una entrada por ficha.", Page));
      } catch (e) {
        await log({ step: "extract", attempt: i, error: String(e).slice(0, 300) });
        if (i === MAX_EXTRACT_TRIES) throw e;
        await new Promise((r) => setTimeout(r, 1000 * i));
      }
    }
    // Don't trust the model blindly: keep only complete entries and resolve photos against the page URL
    const pets = data.pets
      .filter((p) => p.name.trim() && p.photoUrl)
      .map(({ photoUrl, ...p }) => ({ ...p, photoUrls: [new URL(photoUrl, url).href], sourceUrl: url }));
    await log({ step: "extract", ms: Date.now() - startedAt, pets: pets.map((p) => `${p.name} (${p.species}, ${p.sex}, ${p.size}, ${p.ageMonths}m)`), tokens: { ...usage } });
    return pets;
  } finally {
    if (stagehand) await stagehand.close();
    await browser.close().catch(() => {});
  }
}

const withTimeout = (promise, ms) => Promise.race([promise, new Promise((_, reject) => setTimeout(() => reject(Error(`Corrida cortada a los ${ms / 1000} s`)), ms))]);

async function runJob(job) {
  const before = await keyUsage();
  if (before.limitRemaining != null && before.limitRemaining < MAX_COST_USD) throw Error("Saldo de OpenRouter insuficiente para otra corrida");
  try {
    const pets = await withTimeout(extract(job.instagramHandle), MAX_RUN_MS);
    const { created } = await api(`/internal/imports/${job.id}/pets`, { method: "POST", body: JSON.stringify({ pets }) });
    await api(`/internal/imports/${job.id}/status`, { method: "POST", body: JSON.stringify({ status: "done", inputTokens: usage.input, outputTokens: usage.output }) });
    const after = await keyUsage();
    await log({ step: "done", job: job.id, created, costUsd: +(after.usage - before.usage).toFixed(6), keyUsageUsd: after.usage });
  } catch (e) {
    await log({ step: "failed", job: job.id, error: String(e).slice(0, 300) });
    // A timeout after the POST may mean the drafts were created: the API keeps the count, so only the status is reported
    await api(`/internal/imports/${job.id}/status`, {
      method: "POST",
      body: JSON.stringify({ status: "failed", error: String(e).slice(0, 500), inputTokens: usage.input, outputTokens: usage.output }),
    }).catch(() => {});
  }
}

async function main() {
  await mkdir("evidencia", { recursive: true });
  const [mode, slug] = process.argv.slice(2);
  if (mode === "dry") {
    const before = await keyUsage();
    const pets = await withTimeout(extract(slug ?? "patas-del-avila"), MAX_RUN_MS);
    const after = await keyUsage();
    await log({ step: "dry-run", found: pets.length, costUsd: +(after.usage - before.usage).toFixed(6), keyUsageUsd: after.usage });
    return;
  }
  if (!SECRET) throw Error("Falta INTERNAL_SECRET (ver .env.example)");
  console.log(`[worker] ${API} · modelo ${process.env.OPENROUTER_MODEL} · ${HEADLESS ? "headless" : "con ventana"}`);
  for (;;) {
    try {
      const job = await api("/internal/imports/next");
      if (job) await runJob(job);
      else if (mode === "once") console.log("[worker] no hay trabajos en cola");
    } catch (e) {
      console.error("[worker]", e instanceof Error ? e.message : e);
    }
    if (mode === "once") return;
    await new Promise((r) => setTimeout(r, 10_000)); // polling with code, never asking the model
  }
}

main().then(() => process.exit(0)).catch((e) => {
  console.error(e instanceof Error ? e.message : e);
  process.exit(1);
});

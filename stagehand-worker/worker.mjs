// Petzo import worker (D-24, D-32): claims a queued import from the API, opens the foundation's adoption page in a real
// local Chrome, opens its adoption list (observe → check → act → check state) and extracts the pets with Stagehand +
// OpenRouter, then sends them back to the API as drafts. It never
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
const MAX_READ_TRIES = 2; // observe/extract only; act is never retried blindly
const MAX_COST_USD = 0.25; // per-run budget: stop before extracting if the key already spent this much more

const Pet = z.object({
  name: z.string().describe("Nombre de la mascota"),
  species: z.enum(["dog", "cat"]).describe("dog si es perro o perra, cat si es gato o gata"),
  sex: z.enum(["male", "female"]),
  size: z.enum(["small", "medium", "large"]).describe("pequeño=small, mediano=medium, grande=large"),
  ageMonths: z.number().int().min(0).max(360).describe("Edad aproximada en meses (1 año = 12)"),
  description: z.string().describe("Descripción de la ficha"),
});
const Page = z.object({ pets: z.array(Pet).max(10) });

const log = async (entry) => {
  const line = JSON.stringify({ at: new Date().toISOString(), model: process.env.OPENROUTER_MODEL, headless: HEADLESS, ...entry });
  console.log(line);
  await appendFile("evidencia/runs.log", line + "\n");
};

async function api(path, init = {}) {
  const res = await fetch(API + path, { ...init, headers: { "Content-Type": "application/json", "x-internal-secret": SECRET, ...(init.headers ?? {}) } });
  if (!res.ok && res.status !== 204) throw Error(`${init.method ?? "GET"} ${path} → ${res.status}`);
  return res.status === 204 ? null : res.json();
}

/** Reads (observe/extract) are retried at most MAX_READ_TRIES times with a growing wait, logging each cause. */
async function retry(step, fn) {
  for (let i = 1; ; i++) {
    try {
      return await fn();
    } catch (e) {
      await log({ step, attempt: i, error: String(e).slice(0, 300) });
      if (i >= MAX_READ_TRIES) throw e;
      await new Promise((r) => setTimeout(r, 1000 * i));
    }
  }
}

/** Polls the page with code (never the model) until fn is true, or fails after ms. */
async function waitFor(page, fn, ms) {
  for (const end = Date.now() + ms; Date.now() < end; await new Promise((r) => setTimeout(r, 250))) {
    if (await page.evaluate(fn)) return;
  }
  throw Error("La lista de adopción no apareció tras el clic");
}

/** Stops the run if the balance can't be read, the key has no spend limit, or less than one run's budget is left. */
async function checkBudget() {
  const k = await keyUsage();
  if (k.limit == null) throw Error("La clave de OpenRouter no tiene límite de gasto configurado");
  if (k.limitRemaining < MAX_COST_USD) throw Error("Saldo de OpenRouter insuficiente para otra corrida");
  return k;
}

/** Opens the page and extracts its pets. Returns pets with absolute photo URLs, validated for the business. */
async function extract(slug) {
  if (!/^[a-z0-9-]{1,40}$/.test(slug)) throw Error(`Identificador inválido: ${slug}`);
  const url = `${SITE}/${slug}.html`;
  const startedAt = Date.now();
  Object.assign(usage, { calls: 0, input: 0, output: 0, provider: null }); // per run, so the call cap and the token report are per job
  const browser = await localBrowser.launch({ headless: HEADLESS });
  let stagehand;
  try {
    stagehand = await Stagehand.create({ browser, model: { generate } });
    const [page] = await browser.context.pages();
    if (!page) throw Error("No hay página activa");
    await page.goto(url);
    await log({ step: "goto", url });
    // Visible condition before observing, with a limit (no blind sleeps)
    await page.waitForSelector("#ver-adopcion", { timeout: 10_000 });

    // observe proposes the click without running it; code checks it is the expected one before act
    const [action] = await retry("observe", async () => {
      const { data } = await stagehand.observe("Encuentra el botón para ver las mascotas en adopción");
      if (!data?.length) throw Error("No encontró el botón");
      return data;
    });
    if (action.method !== "click" || !/adopci/i.test(action.description ?? "")) throw Error(`Acción inesperada: ${action.method} · ${action.description}`);
    await log({ step: "observe", action: `${action.method} · ${action.description}` });
    // Read-only click, never repeated: if act fails or times out, the page state decides whether it happened
    await stagehand.act(action).catch((e) => log({ step: "act", error: String(e).slice(0, 300) }));
    await waitFor(page, () => !document.getElementById("adopcion").hidden && document.querySelectorAll("#adopcion article").length > 0, 10_000);
    const shot = `evidencia/${slug}-${startedAt}.png`;
    await page.screenshot({ path: shot });
    await log({ step: "act", result: "lista de adopción visible", screenshot: shot });

    const data = await retry("extract", async () => (await stagehand.extract("Lee todas las fichas de mascotas en adopción de esta página. Una entrada por ficha.", Page)).data);
    // Photo URLs come from code, not the model: the accessibility snapshot it reads has the alt text but not the src.
    // Each card's <img> is matched to the extracted pet by its heading.
    const photos = await page.evaluate(() =>
      Object.fromEntries([...document.querySelectorAll("#adopcion article")].map((a) => [a.querySelector("h2")?.textContent.trim(), a.querySelector("img")?.src])),
    );
    const pets = data.pets
      .filter((p) => p.name.trim() && photos[p.name.trim()]) // only cards that really exist on the page, with their photo
      .map((p) => ({ ...p, photoUrls: [photos[p.name.trim()]], sourceUrl: url }));
    await log({ step: "extract", ms: Date.now() - startedAt, pets: pets.map((p) => `${p.name} (${p.species}, ${p.sex}, ${p.size}, ${p.ageMonths}m) ${p.photoUrls[0]}`), tokens: { ...usage } });
    return pets;
  } catch (e) {
    // Capture the page as it was when it failed (the "before" one is taken after the wait)
    try {
      const [page] = await browser.context.pages();
      const file = `evidencia/${slug}-${startedAt}-fallo.png`;
      await page?.screenshot({ path: file });
      await log({ step: "error-screenshot", url, file, error: String(e).slice(0, 300) });
    } catch {}
    throw e;
  } finally {
    if (stagehand) await stagehand.close();
    await browser.close().catch(() => {});
  }
}

const withTimeout = (promise, ms) => Promise.race([promise, new Promise((_, reject) => setTimeout(() => reject(Error(`Corrida cortada a los ${ms / 1000} s`)), ms))]);

async function runJob(job) {
  try {
    // Inside the try: a budget failure marks the claimed job as failed instead of leaving it running
    const before = await checkBudget();
    const pets = await withTimeout(extract(job.instagramHandle), MAX_RUN_MS);
    const { created } = await api(`/internal/imports/${job.id}/pets`, { method: "POST", body: JSON.stringify({ pets }) });
    await api(`/internal/imports/${job.id}/status`, { method: "POST", body: JSON.stringify({ status: "done", inputTokens: usage.input, outputTokens: usage.output }) });
    const after = await keyUsage();
    await log({ step: "done", job: job.id, created, costUsd: +(after.usage - before.usage).toFixed(6), keyUsageUsd: after.usage, saldoUsd: after.limitRemaining });
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
    const before = await checkBudget();
    const pets = await withTimeout(extract(slug ?? "patas-del-avila"), MAX_RUN_MS);
    const after = await keyUsage();
    await log({ step: "dry-run", found: pets.length, costUsd: +(after.usage - before.usage).toFixed(6), keyUsageUsd: after.usage, saldoUsd: after.limitRemaining });
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

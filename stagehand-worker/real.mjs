// Read-only run against a REAL foundation site (ANAA, Madrid): filters its dogs in adoption by sex and reads them.
// Nothing is sent to the Petzo API or Supabase: the challenge asks for invented data only, so real pets stay in the
// evidence log. Allowed by the site: robots.txt "Allow: /" with "Content-Signal: ai-input=yes, use=reference".
// Usage: `npm run real` (HEADLESS=false to watch).
import { localBrowser, Stagehand } from "@browserbasehq/stagehand";
import { z } from "zod/v4";
import { appendFile, mkdir } from "node:fs/promises";
import { generate, keyUsage, limits, usage } from "./openrouter.mjs";

const LIST_URL = "https://www.anaaweb.org/perros-en-adopcion"; // the only site this script may open
const HOST = new URL(LIST_URL).host;
const HEADLESS = process.env.HEADLESS !== "false";
const MAX_RUN_MS = 150_000;
const MAX_PETS = 8;
limits.calls = 10; // 2 observe tries + Buscar + list + profile, each extract may take 2 calls

// Only the animal: sponsors' names (personal data) are never asked for
const Card = z.object({
  name: z.string().describe("Nombre del perro"),
  sex: z.enum(["male", "female"]).describe("male si es macho, female si es hembra"),
  weightKg: z.number().nullable().describe("Peso en kg"),
  birthDate: z.string().nullable().describe("Fecha de nacimiento tal como aparece (dd/mm/aaaa)"),
  breed: z.string().nullable().describe("Raza"),
  adopted: z.boolean().describe("true si la ficha dice ¡Adoptado!"),
});
const List = z.object({
  foundation: z.string().describe("Nombre de la asociación o refugio que publica la página"),
  dogs: z.array(Card).max(MAX_PETS),
});
const Detail = z.object({ name: z.string(), description: z.string().describe("Texto que describe al perro: carácter, historia, necesidades") });

const log = async (entry) => {
  const line = JSON.stringify({ at: new Date().toISOString(), model: process.env.OPENROUTER_MODEL, headless: HEADLESS, site: HOST, ...entry });
  console.log(line);
  await appendFile("evidencia/real.log", line + "\n");
};

// Size from weight with the site's own table (pequeño ≤ 12 kg, mediano ≤ 25 kg); age in months from "dd/mm/aaaa"
const size = (kg) => (kg == null ? null : kg <= 12 ? "small" : kg <= 25 ? "medium" : "large");
function ageMonths(text) {
  const m = text?.match(/(\d{1,2})\/(\d{1,2})\/(\d{4})/);
  if (!m) return null;
  const now = new Date();
  return Math.max(0, (now.getFullYear() - Number(m[3])) * 12 + (now.getMonth() + 1 - Number(m[2])));
}

async function waitFor(page, fn, ms, what) {
  for (const end = Date.now() + ms; Date.now() < end; await new Promise((r) => setTimeout(r, 300))) if (await page.evaluate(fn)) return;
  throw Error(`No apareció: ${what}`);
}

// Visible cards as the page renders them (name from the photo's alt, link to the profile)
const visibleCards = (page) =>
  page.evaluate(() =>
    [...document.querySelectorAll(".animal-card")]
      .filter((c) => c.offsetParent !== null)
      .map((c) => ({ name: (c.querySelector("img")?.alt ?? "").trim().toUpperCase(), text: c.textContent, href: c.querySelector("a[href*='/animales/']")?.href })),
  );

async function run() {
  await mkdir("evidencia", { recursive: true });
  const before = await keyUsage();
  if (before.limit == null) throw Error("La clave de OpenRouter no tiene límite de gasto configurado");
  if (before.limitRemaining < 0.25) throw Error("Saldo de OpenRouter insuficiente");
  const startedAt = Date.now();
  const browser = await localBrowser.launch({ headless: HEADLESS });
  const deadline = setTimeout(() => browser.close().catch(() => {}), MAX_RUN_MS);
  let stagehand;
  try {
    stagehand = await Stagehand.create({ browser, model: { generate } });
    const [page] = await browser.context.pages();
    // The site follows the browser language (it served /en/): ask for Spanish via its own language cookie, then reload
    await page.goto(LIST_URL);
    await page.evaluate(() => (document.cookie = "frontend_lang=es_ES; path=/"));
    await page.goto(LIST_URL);
    await waitFor(page, () => document.querySelectorAll(".animal-card").length > 0, 30_000, "fichas de perros");
    const all = await visibleCards(page);
    await log({ step: "goto", url: LIST_URL, cards: all.length });

    // 1) observe the sex filter and check in the DOM that it is that <select> before act (it once proposed the breed one).
    //    Read-only, so at most 2 tries with a growing wait, each rejection logged.
    const instructions = ['Selecciona la opción de hembras ("Hembra" o "Female") en el filtro de sexo', 'Selecciona "Hembra" en el desplegable cuya primera opción es "Todos los sexos" (no el de raza ni el de tamaño)'];
    let action, element;
    for (let i = 0; i < instructions.length; i++) {
      ({ data: [action] = [] } = await stagehand.observe(instructions[i]));
      element = action ? await page.evaluate((xp) => {
        const el = document.evaluate(xp, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;
        return el ? `${el.tagName}.${el.className}` : "null";
      }, action.selector.replace(/^xpath=/, "")) : "null";
      if (/animal-search-gender/.test(element) && /hembra|female/i.test(JSON.stringify(action?.arguments ?? []))) break;
      await log({ step: "observe", attempt: i + 1, rejected: `${action?.method} sobre ${element} con ${JSON.stringify(action?.arguments)}` });
      if (i === instructions.length - 1) throw Error("observe no propuso el filtro de sexo");
      await new Promise((r) => setTimeout(r, 1000 * (i + 1)));
    }
    await log({ step: "observe", action: `${action.method} · ${action.description}`, arguments: action.arguments, element });

    // 2) act once (never repeated blindly); the page state decides whether it worked
    await stagehand.act(action).catch((e) => log({ step: "act", error: String(e).slice(0, 200) }));
    await waitFor(page, () => document.querySelector(".animal-search-gender")?.value === "female", 5_000, "el filtro de sexo en Hembra");
    await log({ step: "act", result: 'filtro de sexo = "Hembra"' });

    // 3) the filter only applies with the page's "Buscar" button: observe it, check it's that button, act once
    const { data: [search] = [] } = await stagehand.observe('Pulsa el botón "Buscar" del buscador de animales');
    const searchEl = search ? await page.evaluate((xp) => {
      const el = document.evaluate(xp, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;
      return el ? `${el.tagName}.${el.className}` : "null";
    }, search.selector.replace(/^xpath=/, "")) : "null";
    if (!/animal-search-btn/.test(searchEl)) throw Error(`Acción inesperada: ${search?.method} sobre ${searchEl}`);
    await log({ step: "observe", action: `${search.method} · ${search.description}`, element: searchEl });
    await stagehand.act(search).catch((e) => log({ step: "act", error: String(e).slice(0, 200) }));
    await waitFor(page, () => {
      const v = [...document.querySelectorAll(".animal-card")].filter((c) => c.offsetParent !== null);
      return v.length > 0 && v.every((c) => /Hembra|Female/.test(c.textContent));
    }, 15_000, "el listado filtrado por hembras");
    const females = await visibleCards(page);
    await page.screenshot({ path: `evidencia/anaa-${startedAt}-hembras.png` });
    await log({ step: "act", result: `filtro aplicado: ${females.length} de ${all.length} fichas visibles, todas hembras` });

    // 4) extract the visible cards; keep only names that really are visible cards (no hallucinated or hidden ones)
    const lang = await page.evaluate(() => document.documentElement.lang);
    await log({ step: "idioma", lang, url: await page.evaluate(() => location.href) });
    const { data: list } = await stagehand.extract(`Lee las primeras ${MAX_PETS} fichas de perros visibles. Solo datos del animal; no incluyas nombres de padrinos ni de personas.`, List);
    const shown = new Map(females.map((c) => [c.name, c]));
    const dogs = list.dogs
      .filter((d) => shown.has(d.name.trim().toUpperCase()))
      .map((d) => ({ name: d.name.trim(), species: "dog", sex: d.sex, size: size(d.weightKg), ageMonths: ageMonths(d.birthDate), breed: d.breed, adopted: d.adopted }));
    await log({ step: "extract", foundation: list.foundation.trim(), read: list.dogs.length, kept: dogs.length, dogs: dogs.map((d) => `${d.name} (${d.sex}, ${d.size ?? "?"}, ${d.ageMonths ?? "?"}m${d.adopted ? ", adoptada" : ""})`), tokens: { ...usage } });
    if (!dogs.length) throw Error("Ninguna ficha leída coincide con las visibles");

    // 5) one profile, opened by its URL from the DOM (known step = code), same host only
    const target = dogs.find((d) => !d.adopted) ?? dogs[0];
    const href = shown.get(target.name.toUpperCase()).href;
    if (new URL(href).host !== HOST) throw Error(`Enlace fuera del sitio permitido: ${href}`);
    await page.goto(href);
    await waitFor(page, () => document.body.innerText.length > 200, 20_000, "la ficha individual");
    await page.screenshot({ path: `evidencia/anaa-${startedAt}-ficha.png` });
    const { data: detail } = await stagehand.extract("Lee el nombre y la descripción del perro de esta ficha. No incluyas datos de personas.", Detail);
    if (!detail.name.toUpperCase().includes(target.name.toUpperCase().split(" ")[0])) throw Error(`Se abrió la ficha de ${detail.name}, no la de ${target.name}`);
    await log({ step: "extract-ficha", url: href, name: detail.name, description: detail.description.slice(0, 300), tokens: { ...usage } });

    const after = await keyUsage();
    await log({ step: "done", ms: Date.now() - startedAt, saved: "nada (solo lectura)", costUsd: +(after.usage - before.usage).toFixed(6), saldoUsd: after.limitRemaining });
  } catch (e) {
    try {
      const [page] = await browser.context.pages();
      await page?.screenshot({ path: `evidencia/anaa-${startedAt}-fallo.png` });
    } catch {}
    await log({ step: "failed", error: String(e).slice(0, 300) });
    process.exitCode = 1;
  } finally {
    clearTimeout(deadline);
    if (stagehand) await stagehand.close();
    await browser.close().catch(() => {});
  }
}

run();

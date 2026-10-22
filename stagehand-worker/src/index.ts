// Stagehand import worker (D-24): polls the API for queued Instagram imports, extracts up to
// 10 posts with Claude Haiku 5.5 and sends them back as drafts. Runs on your PC: `npm start`.
import { localBrowser, Stagehand } from "@browserbasehq/stagehand";
import { z } from "zod/v4";

const API = process.env.PETZO_API_URL ?? "http://localhost:8090";
const SECRET = process.env.INTERNAL_SECRET ?? "";
const POLL_MS = 10_000;

const Post = z.object({
  name: z.string().nullable().describe("Nombre de la mascota si aparece, si no null"),
  species: z.enum(["dog", "cat"]).nullable(),
  sex: z.enum(["male", "female"]).nullable(),
  size: z.enum(["small", "medium", "large"]).nullable(),
  ageMonths: z.number().int().min(0).max(360).nullable().describe("Edad aproximada en meses"),
  description: z.string().describe("Texto del post, resumido si es largo"),
  photoUrls: z.array(z.string()).describe("URLs https de las imágenes del post (cdninstagram.com o fbcdn.net)"),
  sourceUrl: z.string().nullable().describe("Enlace al post"),
});

async function api(path: string, init: RequestInit = {}) {
  const res = await fetch(API + path, {
    ...init,
    headers: { "Content-Type": "application/json", "x-internal-secret": SECRET, ...(init.headers ?? {}) },
  });
  if (!res.ok && res.status !== 204) throw new Error(`${init.method ?? "GET"} ${path} → ${res.status}`);
  return res.status === 204 ? null : res.json();
}

async function runJob(job: { id: string; instagramHandle: string }) {
  console.log(`[import] ${job.id} @${job.instagramHandle}`);
  const browser = await localBrowser.launch({ userDataDir: "./browser-data" });
  const stagehand = await Stagehand.create({
    browser,
    model: { modelName: "anthropic/claude-haiku-5-5", apiKey: process.env.ANTHROPIC_API_KEY },
  });
  let tokens = { input: 0, output: 0 };
  try {
    const [page] = await browser.context.pages();
    await page.goto(`https://www.instagram.com/${encodeURIComponent(job.instagramHandle)}/`);
    const { data } = await stagehand.extract(
      "Extrae hasta 10 publicaciones recientes de este perfil que ofrezcan un perro o gato en adopción. Ignora las que no sean adopciones.",
      z.object({ posts: z.array(Post).max(10) }),
    );
    const pets = data.posts.map((p) => ({ ...p, photoUrls: p.photoUrls.filter((u) => u.startsWith("https://")).slice(0, 5) }));
    const res = await api(`/internal/imports/${job.id}/pets`, { method: "POST", body: JSON.stringify({ pets }) });
    console.log(`[import] ${job.id} created ${res.created} drafts`);
    tokens = await usage(stagehand);
    await api(`/internal/imports/${job.id}/status`, { method: "POST", body: JSON.stringify({ status: "done", inputTokens: tokens.input, outputTokens: tokens.output }) });
  } catch (e) {
    console.error(`[import] ${job.id} failed:`, e);
    tokens = await usage(stagehand).catch(() => tokens);
    await api(`/internal/imports/${job.id}/status`, {
      method: "POST",
      body: JSON.stringify({ status: "failed", error: String(e).slice(0, 500), inputTokens: tokens.input, outputTokens: tokens.output }),
    }).catch(() => {});
  } finally {
    await stagehand.close();
  }
}

// Sums prompt/completion tokens across act/extract/observe so the API logs them in ai_logs
async function usage(stagehand: Stagehand) {
  const m = (await stagehand.metrics()) as Record<string, number>;
  const sum = (suffix: string) => Object.entries(m).filter(([k]) => k.endsWith(suffix) && !k.startsWith("total")).reduce((a, [, v]) => a + (v || 0), 0);
  return { input: sum("PromptTokens"), output: sum("CompletionTokens") };
}

async function loop() {
  if (!SECRET || !process.env.ANTHROPIC_API_KEY) throw new Error("Set INTERNAL_SECRET and ANTHROPIC_API_KEY (see .env.example)");
  console.log(`[worker] polling ${API} every ${POLL_MS / 1000}s`);
  for (;;) {
    try {
      const job = await api("/internal/imports/next");
      if (job) await runJob(job);
    } catch (e) {
      console.error("[worker]", e instanceof Error ? e.message : e);
    }
    await new Promise((r) => setTimeout(r, POLL_MS));
  }
}

loop();

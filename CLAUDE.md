# Petzo backend

API for Petzo (dog/cat adoption, Gran Caracas). Source of truth: `docs/SPEC.md` — read only the section you need (§14 data, §15 RLS, §16 sprints, §17 endpoints/rules). Frontend lives in the `petzo-frontend` repo (do not touch it from here).

## Stack
- Java 21, Spring Boot 3.5.x (Maven wrapper), JPA (`ddl-auto=validate`), Flyway (`src/main/resources/db/migration`).
- Supabase: Postgres via session pooler (IPv4), Auth (JWT ES256 via JWKS), Storage (REST + service_role key).
- AI: Claude Haiku 5.5 (`claude-haiku-5-5`) with `com.anthropic:anthropic-java`. n8n: flow 1 moderation, flow 2 notifications. `stagehand-worker/` (Node) polls `/internal/imports/next`.

## Commands (Git Bash)
- JDK: `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"; export PATH="$JAVA_HOME/bin:$PATH"`
- Tests: `./mvnw -q test` (Testcontainers; Docker Desktop must run)
- Run: `./mvnw spring-boot:run` (reads `.env`; Swagger at `/swagger-ui.html`)

## Rules
- Supabase only authenticates; Spring Security validates the JWT. `/api/admin/**` needs `profiles.role = 'admin'`; suspended → 403.
- Public: GET `/api/pets`, `/api/pets/{id}`, `/api/municipalities`. `/internal/**` only with header `x-internal-secret`.
- All writes go through the API. RLS: SELECT-only policies (§15). Never add write policies. New schema = new Flyway version; never edit an applied one (V1 is a baseline of the CLI schema).
- Profiles are created by the `auth.users` trigger (V2, D-26).
- Plan limit: free 3 / pro 30 active (`pending_review` + `approved`). WhatsApp only with an approved request between both users.
- AI: no `temperature`/`top_p`/prefill; short JSON validated with Jackson; every call logged in `ai_logs`; daily cap `AI_DAILY_TOKEN_CAP` → skip AI, stay `pending_review`. If AI or n8n fail, nothing is lost.
- Errors: ProblemDetail. Layers: controller → service → repository; DTOs as records.
- Code/commits in English (Conventional Commits, subject only); user-facing messages in Spanish. Secrets only in `.env`.

## Workflow
- Sprints B0–B4 (§16). Short plan, then build; commit + push each piece; tests green before closing.
- Log skills in `docs/SKILLS_LOG.md` and subagents in `docs/AGENTS_LOG.md` (real uses only).

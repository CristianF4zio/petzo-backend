-- Flyway's own table lives in public, which PostgREST exposes: close it like the rest.
-- Runs after migrations (inside a migration it deadlocks on Flyway's own lock). Idempotent.
alter table if exists flyway_schema_history enable row level security;

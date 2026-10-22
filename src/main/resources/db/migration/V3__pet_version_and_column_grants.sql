-- Optimistic locking: every content change (including photos) bumps the version, so a moderation
-- result computed on an older version is ignored and concurrent writes cannot overwrite each other.
alter table pets add column version integer not null default 0;

-- The anon key ships in the APK: PostgREST must not expose moderation signals or source URLs.
revoke select on pets from anon, authenticated;
grant select (id, owner_id, species, name, age_months, sex, size, municipality_id, vaccinated, sterilized,
              dewormed, description, status, source, created_at, updated_at) on pets to anon, authenticated;

-- profiles is not readable by anon, so the suspension check needs a definer function
create function public.owner_is_active(owner uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select not coalesce((select is_suspended from public.profiles where id = owner), true)
$$;

drop policy "approved or own pets" on pets;
create policy "approved or own pets" on pets
  for select to anon, authenticated
  using ((status = 'approved' and public.owner_is_active(owner_id)) or owner_id = (select auth.uid()));

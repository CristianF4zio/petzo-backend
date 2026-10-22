create index pets_municipality_idx on pets (municipality_id);

-- D-26: every auth user gets a profile. Plain SQL trigger, not an Edge Function.
-- user_metadata is user-controlled: trim the name, keep only https avatars.
create function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles (id, full_name, avatar_url)
  values (
    new.id,
    nullif(left(trim(coalesce(new.raw_user_meta_data ->> 'full_name', new.raw_user_meta_data ->> 'name')), 80), ''),
    case when new.raw_user_meta_data ->> 'avatar_url' like 'https://%' then left(new.raw_user_meta_data ->> 'avatar_url', 500) end
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- Users created before this migration
insert into profiles (id, full_name, avatar_url)
select id,
       nullif(left(trim(coalesce(raw_user_meta_data ->> 'full_name', raw_user_meta_data ->> 'name')), 80), ''),
       case when raw_user_meta_data ->> 'avatar_url' like 'https://%' then left(raw_user_meta_data ->> 'avatar_url', 500) end
from auth.users
on conflict (id) do nothing;

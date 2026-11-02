-- Knjiga lokala: zajednička baza.
-- Pokreće se jednom u Supabase › SQL Editor (New query › nalepiti sve › Run).
-- Sme da se pokrene i ponovo; postojeći podaci ostaju.
--
-- Uloge u firmi:
--   vlasnik  – vidi i menja sve, upravlja korisnicima i pozivnicama
--   menadzer – vidi i menja sve podatke, ne upravlja korisnicima
--   smena    – samo pazar i troškovi svog lokala, i zadaci

create table if not exists public.firme (
  id uuid primary key default gen_random_uuid(),
  naziv text not null,
  napravljeno timestamptz not null default now()
);

create table if not exists public.clanovi (
  firma uuid not null references public.firme(id) on delete cascade,
  korisnik uuid not null references auth.users(id) on delete cascade,
  ime text not null default '',
  email text not null default '',
  uloga text not null check (uloga in ('vlasnik','menadzer','smena')),
  lokal int,
  napravljeno timestamptz not null default now(),
  primary key (firma, korisnik)
);

create table if not exists public.pozivnice (
  kod text primary key,
  firma uuid not null references public.firme(id) on delete cascade,
  uloga text not null check (uloga in ('menadzer','smena')),
  lokal int,
  ime text not null default '',
  napravio uuid not null default auth.uid(),
  napravljeno timestamptz not null default now(),
  iskoristio uuid
);

-- Every record of the app (pazar, trošak, račun, …) is one row; deletions are soft so other phones learn about them.
create table if not exists public.zapisi (
  firma uuid not null references public.firme(id) on delete cascade,
  id text not null,
  kolekcija text not null,
  podaci jsonb not null,
  obrisano boolean not null default false,
  izmenjeno timestamptz not null default now(),
  izmenio uuid default auth.uid(),
  primary key (firma, id)
);
create index if not exists zapisi_firma_izmenjeno on public.zapisi (firma, izmenjeno);

create or replace function public.zapisi_izmenjeno() returns trigger language plpgsql as $$
begin
  new.izmenjeno := now();
  new.izmenio := auth.uid();
  return new;
end $$;
drop trigger if exists zapisi_izmenjeno on public.zapisi;
create trigger zapisi_izmenjeno before insert or update on public.zapisi
  for each row execute function public.zapisi_izmenjeno();

-- Helpers used by the policies (security definer, so they can read clanovi without recursing into its policies).
create or replace function public.moja_uloga(f uuid) returns text
  language sql stable security definer set search_path = public as
  $$ select uloga from clanovi where firma = f and korisnik = auth.uid() $$;

create or replace function public.moj_lokal(f uuid) returns int
  language sql stable security definer set search_path = public as
  $$ select lokal from clanovi where firma = f and korisnik = auth.uid() $$;

-- What a 'smena' member may see and write: zadaci, and pazar/troškovi of their own lokal.
create or replace function public.smena_sme(f uuid, kol text, p jsonb) returns boolean
  language sql stable security definer set search_path = public as
  $$ select kol = 'zadaci'
         or (kol in ('pazari','troskovi') and p->>'lokal' = (select lokal from clanovi where firma = f and korisnik = auth.uid())::text) $$;

alter table public.firme enable row level security;
alter table public.clanovi enable row level security;
alter table public.pozivnice enable row level security;
alter table public.zapisi enable row level security;

drop policy if exists firme_citanje on public.firme;
create policy firme_citanje on public.firme for select using (public.moja_uloga(id) is not null);
drop policy if exists firme_izmena on public.firme;
create policy firme_izmena on public.firme for update using (public.moja_uloga(id) = 'vlasnik');

drop policy if exists clanovi_citanje on public.clanovi;
create policy clanovi_citanje on public.clanovi for select using (public.moja_uloga(firma) is not null);
drop policy if exists clanovi_izmena on public.clanovi;
create policy clanovi_izmena on public.clanovi for update
  using (public.moja_uloga(firma) = 'vlasnik' and korisnik <> auth.uid())
  with check (uloga in ('menadzer','smena'));
drop policy if exists clanovi_brisanje on public.clanovi;
create policy clanovi_brisanje on public.clanovi for delete
  using (public.moja_uloga(firma) = 'vlasnik' and korisnik <> auth.uid());

drop policy if exists pozivnice_vlasnik on public.pozivnice;
create policy pozivnice_vlasnik on public.pozivnice for all
  using (public.moja_uloga(firma) = 'vlasnik') with check (public.moja_uloga(firma) = 'vlasnik');

drop policy if exists zapisi_citanje on public.zapisi;
create policy zapisi_citanje on public.zapisi for select using (
  public.moja_uloga(firma) in ('vlasnik','menadzer')
  or (public.moja_uloga(firma) = 'smena' and (kolekcija = 'settings' or public.smena_sme(firma, kolekcija, podaci))));
drop policy if exists zapisi_unos on public.zapisi;
create policy zapisi_unos on public.zapisi for insert with check (
  public.moja_uloga(firma) in ('vlasnik','menadzer')
  or (public.moja_uloga(firma) = 'smena' and public.smena_sme(firma, kolekcija, podaci)));
drop policy if exists zapisi_izmena on public.zapisi;
create policy zapisi_izmena on public.zapisi for update
  using (public.moja_uloga(firma) in ('vlasnik','menadzer')
         or (public.moja_uloga(firma) = 'smena' and public.smena_sme(firma, kolekcija, podaci)))
  with check (public.moja_uloga(firma) in ('vlasnik','menadzer')
         or (public.moja_uloga(firma) = 'smena' and public.smena_sme(firma, kolekcija, podaci)));

-- Creating a firm makes the caller its owner.
create or replace function public.napravi_firmu(naziv text) returns uuid
  language plpgsql security definer set search_path = public as $$
declare f uuid;
begin
  if auth.uid() is null then raise exception 'Niste prijavljeni'; end if;
  insert into firme (naziv) values (coalesce(nullif(trim(naziv), ''), 'Moji lokali')) returning id into f;
  insert into clanovi (firma, korisnik, ime, email, uloga)
    select f, auth.uid(), coalesce(u.raw_user_meta_data->>'ime', ''), coalesce(u.email, ''), 'vlasnik' from auth.users u where u.id = auth.uid();
  return f;
end $$;

-- Owner creates an invitation code (valid 14 days, single use) to send to a worker.
create or replace function public.napravi_pozivnicu(f uuid, uloga text, lokal int, ime text) returns text
  language plpgsql security definer set search_path = public as $$
declare k text;
begin
  if public.moja_uloga(f) is distinct from 'vlasnik' then raise exception 'Samo vlasnik pravi pozivnice'; end if;
  if uloga not in ('menadzer','smena') then raise exception 'Nepoznata uloga'; end if;
  if uloga = 'smena' and lokal is null then raise exception 'Za smenu izaberite lokal'; end if;
  k := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));
  insert into pozivnice (kod, firma, uloga, lokal, ime) values (k, f, uloga, case when uloga = 'smena' then lokal end, coalesce(ime, ''));
  return k;
end $$;

-- A signed-in user joins a firm with a code.
create or replace function public.pridruzi_se(kod text, ime text) returns uuid
  language plpgsql security definer set search_path = public as $$
declare p pozivnice%rowtype;
begin
  if auth.uid() is null then raise exception 'Niste prijavljeni'; end if;
  select * into p from pozivnice where pozivnice.kod = upper(trim(pridruzi_se.kod)) for update;
  if not found or p.iskoristio is not null or p.napravljeno < now() - interval '14 days' then
    raise exception 'Pozivnica ne važi';
  end if;
  insert into clanovi (firma, korisnik, ime, email, uloga, lokal)
    select p.firma, auth.uid(), coalesce(nullif(trim(pridruzi_se.ime), ''), p.ime), coalesce(u.email, ''), p.uloga, p.lokal from auth.users u where u.id = auth.uid()
    on conflict (firma, korisnik) do update set uloga = excluded.uloga, lokal = excluded.lokal;
  update pozivnice set iskoristio = auth.uid() where pozivnice.kod = p.kod;
  return p.firma;
end $$;

revoke all on function public.napravi_firmu(text) from public, anon;
revoke all on function public.napravi_pozivnicu(uuid, text, int, text) from public, anon;
revoke all on function public.pridruzi_se(text, text) from public, anon;
grant execute on function public.napravi_firmu(text) to authenticated;
grant execute on function public.napravi_pozivnicu(uuid, text, int, text) to authenticated;
grant execute on function public.pridruzi_se(text, text) to authenticated;

grant select, update on public.firme to authenticated;
grant select, update, delete on public.clanovi to authenticated;
grant select, insert, update, delete on public.pozivnice to authenticated;
grant select, insert, update on public.zapisi to authenticated;

-- Live updates between phones.
do $$ begin
  alter publication supabase_realtime add table public.zapisi;
exception when duplicate_object then null; end $$;

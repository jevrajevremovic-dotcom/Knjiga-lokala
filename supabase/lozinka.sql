-- Knjiga lokala: vlasnik može da postavi novu lozinku radniku koji je zaboravio svoju.
-- Pokreće se jednom u Supabase › SQL Editor (New query › nalepiti › Run). Isto je dodato i na kraj setup.sql.

create or replace function public.postavi_lozinku(f uuid, korisnik_id uuid, lozinka text) returns void
  language plpgsql security definer set search_path = public, extensions as $$
begin
  if public.moja_uloga(f) is distinct from 'vlasnik' then raise exception 'Samo vlasnik menja lozinke radnicima'; end if;
  if not exists (select 1 from public.clanovi c where c.firma = f and c.korisnik = korisnik_id and c.uloga <> 'vlasnik') then
    raise exception 'Korisnik nije radnik u vašoj firmi';
  end if;
  if length(coalesce(lozinka, '')) < 6 then raise exception 'Lozinka mora imati bar 6 znakova'; end if;
  update auth.users set encrypted_password = extensions.crypt(lozinka, extensions.gen_salt('bf')), updated_at = now()
    where id = korisnik_id;
end $$;

revoke all on function public.postavi_lozinku(uuid, uuid, text) from public, anon;
grant execute on function public.postavi_lozinku(uuid, uuid, text) to authenticated;

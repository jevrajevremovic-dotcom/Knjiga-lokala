# Zajednička baza – podešavanje (jednom)

Supabase je besplatan do 500 MB podataka i 50.000 korisnika mesečno – za knjigu lokala to je dovoljno godinama.

## 1. Nalog i projekat
1. Otvorite https://supabase.com i kliknite **Start your project** (prijava preko GitHub naloga je najbrža).
2. **New project**: ime npr. `knjiga-lokala`, region **Central EU (Frankfurt)**, lozinku baze sačuvajte negde (aplikaciji ne treba).
3. Sačekajte 1–2 minuta da se projekat napravi.

## 2. Tabele i prava
1. Levo **SQL Editor** › **New query**.
2. Otvorite fajl `supabase/setup.sql` iz ovog repozitorijuma, kopirajte ceo sadržaj, nalepite i kliknite **Run**.
3. Treba da piše *Success. No rows returned*.

## 3. Prijava bez potvrde mejla (preporuka)
**Authentication › Sign In / Providers › Email** › isključite **Confirm email** › **Save**.
Tako radnik odmah može da koristi nalog. (Ako ostavite uključeno, svako mora prvo da klikne link u mejlu.)

## 4. Adresa i ključ
**Project Settings › API** (ili **Data API / API Keys**):
- **Project URL** – npr. `https://abcd1234.supabase.co`
- **Publishable key** (ili *anon public* key) – javni ključ; zaštitu podataka obezbeđuju pravila iz `setup.sql`.

Nikad ne koristite **secret / service_role** ključ u aplikaciji.

## 5. U aplikaciji
1. **Podešavanja › Zajednička baza**: unesite URL i ključ › **Poveži**.
2. **Nemate nalog? Napravite ga** › ime, mejl, lozinka.
3. **Vlasnik: napravite firmu** – podaci sa vašeg telefona postaju podaci firme.
4. **Nova pozivnica** za svakog radnika (ime, uloga, lokal) › pošaljite kod preko Vibera.
5. Radnik: instalira aplikaciju › Podešavanja › URL i ključ › napravi nalog › **Imam pozivnicu**.

Kad pošaljete URL i ključ, mogu se upisati u aplikaciju, pa radnici neće morati da ih unose.

## Uloge
| Uloga | Vidi i menja |
|---|---|
| Vlasnik | sve, plus korisnici i pozivnice |
| Menadžer | sve podatke, bez korisnika |
| Smena | samo pazar i troškove svog lokala, i zadatke |

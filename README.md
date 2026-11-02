# Knjiga lokala

Android i web aplikacija za restoran/kafić: pazari za 3 lokala, dnevni troškovi po vrsti, gotovinski računi (isplaćeno/neisplaćeno), računi i dug prema dobavljačima, krediti i lizing (preostale rate i iznos), radnici sa avansima, imenik, zadaci i kursna lista NBS.

## Kako se pravi APK

Svaki `git push` na granu `main` pokreće GitHub Actions (`.github/workflows/apk.yml`). Kad se završi, gotov APK je u odeljku **Releases** repozitorijuma (i kao artifact u **Actions**).

## Web verzija

Izmene u `www/` se automatski objavljuju na GitHub Pages (`.github/workflows/web.yml`):
https://jevrajevremovic-dotcom.github.io/Knjiga-lokala/
Podaci se čuvaju samo u pregledaču na tom uređaju.

## Vreme, berza, kripto i vesti (Pregled)

- Vreme (Open-Meteo) i kripto (CoinGecko) aplikacija preuzima direktno.
- Vesti (N1, Danas, BBC na srpskom) i berzu (Yahoo Finance) skuplja `scripts/info.py` svakih sat vremena
  (`.github/workflows/info.yml`) u `info.json` na grani `info`; aplikacija ga čita sa raw.githubusercontent.com.
  GitHub gasi zakazane poslove posle 60 dana bez izmena u repozitorijumu – tada ga ponovo uključite u Actions.

## Struktura

- `www/` – sama aplikacija (HTML/JS). Može se probati i u pregledaču: `powershell -File serve.ps1`, pa otvoriti http://localhost:8080
- `android/` – Android omotač (WebView) koji učitava `www/` iz APK-a
- `android/keystore/` – stalni ključ za potpisivanje (samo na ovom računaru, nije u git-u). Na GitHub-u je sačuvan kao tajne `KEYSTORE_B64` i `KEYSTORE_PASSWORD`. Isti ključ omogućava da se nova verzija instalira preko stare bez gubitka podataka – **sačuvajte ovaj folder**.
- `podaci-iz-claude-verzije.json` – podaci iz prve (claude.ai) verzije; uvoze se u aplikaciji preko „Vrati iz kopije“.
- `claude-verzija/` – prva verzija aplikacije objavljena na claude.ai.

## Podaci

Svi podaci se čuvaju samo na telefonu. Povremeno napravite rezervnu kopiju (Pregled → Napravi kopiju) i pošaljite je sebi.

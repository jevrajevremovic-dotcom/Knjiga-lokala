# Knjiga lokala

Android aplikacija za restoran/kafić: pazari za 3 lokala, dnevni troškovi po vrsti, zadaci, računi i dug prema dobavljačima, kursna lista NBS.

## Kako se pravi APK

Svaki `git push` na granu `main` pokreće GitHub Actions (`.github/workflows/apk.yml`). Kad se završi, gotov APK je u odeljku **Releases** repozitorijuma (i kao artifact u **Actions**).

## Web verzija

Izmene u `www/` se automatski objavljuju na GitHub Pages (`.github/workflows/web.yml`):
https://jevrajevremovic-dotcom.github.io/Knjiga-lokala/
Podaci se čuvaju samo u pregledaču na tom uređaju.

## Struktura

- `www/` – sama aplikacija (HTML/JS). Može se probati i u pregledaču: `powershell -File serve.ps1`, pa otvoriti http://localhost:8080
- `android/` – Android omotač (WebView) koji učitava `www/` iz APK-a
- `android/keystore/` – stalni ključ za potpisivanje (samo na ovom računaru, nije u git-u). Na GitHub-u je sačuvan kao tajne `KEYSTORE_B64` i `KEYSTORE_PASSWORD`. Isti ključ omogućava da se nova verzija instalira preko stare bez gubitka podataka – **sačuvajte ovaj folder**.
- `podaci-iz-claude-verzije.json` – podaci iz prve (claude.ai) verzije; uvoze se u aplikaciji preko „Vrati iz kopije“.
- `claude-verzija/` – prva verzija aplikacije objavljena na claude.ai.

## Podaci

Svi podaci se čuvaju samo na telefonu. Povremeno napravite rezervnu kopiju (Pregled → Napravi kopiju) i pošaljite je sebi.

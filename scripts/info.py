"""Collects news headlines and market quotes into info.json for the app's home page.

News sites and Yahoo Finance do not allow requests from a browser on another site (CORS),
so GitHub Actions runs this hourly and publishes the result on the `info` branch.
Standard library only.
"""
import html
import json
import re
import sys
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from xml.etree import ElementTree

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"

NEWS = {
    "srbija": [
        ("N1", "https://n1info.rs/vesti/feed/"),
        ("Danas", "https://www.danas.rs/rubrika/vesti/feed/"),
    ],
    "svet": [
        ("BBC na srpskom", "https://feeds.bbci.co.uk/serbian/lat/rss.xml"),
        ("N1", "https://n1info.rs/svet/feed/"),
        ("Danas", "https://www.danas.rs/rubrika/svet/feed/"),
    ],
}
NEWS_PER_SECTION = 14

# The whole catalog is collected; each user picks in the app which of these to follow.
MARKETS = [
    ("^GSPC", "S&P 500", "Indeksi"),
    ("^IXIC", "Nasdaq", "Indeksi"),
    ("^DJI", "Dow Jones", "Indeksi"),
    ("^GDAXI", "DAX (Nemačka)", "Indeksi"),
    ("^FTSE", "FTSE 100 (V. Britanija)", "Indeksi"),
    ("^FCHI", "CAC 40 (Francuska)", "Indeksi"),
    ("^STOXX50E", "Euro Stoxx 50", "Indeksi"),
    ("^ATX", "ATX (Austrija)", "Indeksi"),
    ("^N225", "Nikkei 225 (Japan)", "Indeksi"),
    ("^HSI", "Hang Seng (Hong Kong)", "Indeksi"),
    ("GC=F", "Zlato", "Roba"),
    ("SI=F", "Srebro", "Roba"),
    ("BZ=F", "Nafta Brent", "Roba"),
    ("CL=F", "Nafta WTI", "Roba"),
    ("NG=F", "Prirodni gas", "Roba"),
    ("KC=F", "Kafa", "Roba"),
    ("CC=F", "Kakao", "Roba"),
    ("SB=F", "Šećer", "Roba"),
    ("ZW=F", "Pšenica", "Roba"),
    ("ZC=F", "Kukuruz", "Roba"),
    ("AAPL", "Apple", "Akcije"),
    ("MSFT", "Microsoft", "Akcije"),
    ("NVDA", "Nvidia", "Akcije"),
    ("AMZN", "Amazon", "Akcije"),
    ("GOOGL", "Alphabet (Google)", "Akcije"),
    ("META", "Meta (Facebook)", "Akcije"),
    ("TSLA", "Tesla", "Akcije"),
    ("KO", "Coca-Cola", "Akcije"),
    ("PEP", "PepsiCo", "Akcije"),
    ("MCD", "McDonald's", "Akcije"),
    ("SBUX", "Starbucks", "Akcije"),
    ("NESN.SW", "Nestlé", "Akcije"),
    ("EURUSD=X", "EUR/USD", "Valute"),
    ("EURCHF=X", "EUR/CHF", "Valute"),
    ("GBPUSD=X", "GBP/USD", "Valute"),
]


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "*/*"})
    with urllib.request.urlopen(req, timeout=25) as r:
        return r.read()


def clean(text):
    text = re.sub(r"<[^>]+>", " ", html.unescape(text or ""))
    return re.sub(r"\s+", " ", text).strip()


def read_feed(source, url):
    root = ElementTree.fromstring(fetch(url))
    out = []
    for item in root.iter("item"):
        title = clean(item.findtext("title"))
        link = (item.findtext("link") or "").strip()
        if not title or not link.startswith("http"):
            continue
        try:
            when = parsedate_to_datetime(item.findtext("pubDate")).astimezone(timezone.utc).isoformat()
        except (TypeError, ValueError):
            when = ""
        out.append({"naslov": title, "link": link, "izvor": source, "vreme": when})
    return out


def news():
    result = {}
    for section, feeds in NEWS.items():
        items, seen = [], set()
        for source, url in feeds:
            try:
                for it in read_feed(source, url):
                    key = it["naslov"].lower()
                    if key not in seen:
                        seen.add(key)
                        items.append(it)
            except Exception as e:  # one broken feed must not empty the section
                print(f"vesti {source} ({url}): {e}", file=sys.stderr)
        items.sort(key=lambda x: x["vreme"], reverse=True)
        result[section] = items[:NEWS_PER_SECTION]
    return result


def quote(symbol):
    q = urllib.parse.quote(symbol)
    last_err = None
    for host in ("query1", "query2"):
        try:
            data = json.loads(fetch(f"https://{host}.finance.yahoo.com/v8/finance/chart/{q}?range=1mo&interval=1d"))
            res = data["chart"]["result"][0]
            meta = res["meta"]
            closes = [c for c in res["indicators"]["quote"][0].get("close", []) if c is not None]
            price = meta.get("regularMarketPrice") or (closes[-1] if closes else None)
            pct = meta.get("regularMarketChangePercent")
            if pct is None and len(closes) >= 2 and closes[-2]:
                pct = (closes[-1] - closes[-2]) / closes[-2] * 100
            return {"cena": price, "promena": pct, "valuta": meta.get("currency", ""), "istorija": [round(c, 2) for c in closes[-30:]]}
        except Exception as e:
            last_err = e
    raise last_err


def markets():
    out = []
    for symbol, name, group in MARKETS:
        try:
            out.append({"simbol": symbol, "naziv": name, "grupa": group, **quote(symbol)})
        except Exception as e:
            print(f"berza {symbol}: {e}", file=sys.stderr)
    return out


def main(path):
    data = {
        "azurirano": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "vesti": news(),
        "berza": markets(),
    }
    if not data["berza"] and not any(data["vesti"].values()):
        sys.exit("Nijedan izvor nije odgovorio")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    print(f"vesti: {', '.join(f'{k}={len(v)}' for k, v in data['vesti'].items())}; berza: {len(data['berza'])}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "info.json")

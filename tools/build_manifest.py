#!/usr/bin/env python3
"""content/ klasörünü tarar ve content/manifest.json üretir.

Yapı:  content/<hesap>/<tür>/<içerik>/meta.json  +  medya dosyaları
  hesap: evrim | klinik | global | almanya | boyutla | originals (Zootopia Originals YouTube kanalı)
  tür:   feed (kaydırmalı/tek görsel) | reel | story | phone (telefondan eklenen videolar)
         originals için: short (YouTube Shorts) | video (uzun video)
  YouTube alanları (meta.json, isteğe bağlı): "youtube" (yayındaysa adres), "tags",
         "media_url" (büyük videolar için GitHub sürüm dosyası adresi; depoya video konmaz)
  feed:  01.jpg, 02.jpg ... (sırayla)        reel/story: video.mp4 (+ cover.jpg) ya da 01.jpg
  meta.json: {"title", "subtitle", "caption", "order", "date" (isteğe bağlı, YYYY-MM-DD)}
"""
import json, os, sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
C = os.path.join(ROOT, 'content')
ACCOUNTS = [
    {"id": "evrim", "name": "Evrim Arşivi"},
    {"id": "klinik", "name": "Zootopia Veteriner Kliniği"},
    {"id": "global", "name": "Zootopia Global"},
    {"id": "almanya", "name": "Zootopia Almanya"},
    {"id": "boyutla", "name": "Boyutla 3D"},
    {"id": "originals", "name": "Zootopia Originals (YouTube)"},
]
TYPES = ["reel", "feed", "story", "phone", "short", "video"]
MEDIA = ('.jpg', '.jpeg', '.png', '.mp4', '.mov')


def main():
    items = []
    for a in ACCOUNTS:
        for t in TYPES:
            base = os.path.join(C, a["id"], t)
            if not os.path.isdir(base):
                continue
            for slug in sorted(os.listdir(base)):
                d = os.path.join(base, slug)
                mp = os.path.join(d, 'meta.json')
                if not os.path.isfile(mp):
                    continue
                meta = json.load(open(mp, encoding='utf-8'))
                files = sorted(f for f in os.listdir(d) if f.lower().endswith(MEDIA) and f != 'cover.jpg')
                videos = [f for f in files if f.lower().endswith(('.mp4', '.mov'))]
                media = videos[:1] if videos else files
                rel = f"content/{a['id']}/{t}/{slug}/"
                ext_media = [meta["media_url"]] if meta.get("media_url") else []
                if not media and not ext_media and not meta.get("youtube"):
                    continue
                has_cover = os.path.isfile(os.path.join(d, 'cover.jpg'))
                cover = rel + ('cover.jpg' if has_cover else (media[0] if media else ''))
                it = {
                    "id": f"{a['id']}-{t}-{slug}", "account": a["id"], "type": t,
                    "order": meta.get("order", 999), "title": meta["title"],
                    "subtitle": meta.get("subtitle", ""), "caption": meta.get("caption", ""),
                    "cover": cover, "media": ext_media or [rel + f for f in media],
                }
                for k in ("youtube", "tags"):
                    if meta.get(k):
                        it[k] = meta[k]
                if meta.get("date"):
                    it["date"] = meta["date"]
                items.append(it)
    old = {}
    p = os.path.join(C, 'manifest.json')
    if os.path.isfile(p):
        try: old = json.load(open(p, encoding='utf-8'))
        except Exception: pass
    m = {
        "version": 2,
        "app_version": old.get("app_version", 0),
        "apk_url": "https://github.com/olcaydunder/evrim-arsivi/releases/download/app/EvrimArsivi.apk",
        "accounts": ACCOUNTS,
        "items": items,
    }
    json.dump(m, open(p, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print(f"{len(items)} içerik yazıldı")


if __name__ == '__main__':
    main()

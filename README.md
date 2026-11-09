# Evrim Arşivi

Instagram paylaşım planlayıcısı (Android) ve içerik deposu.

- **Uygulama:** [EvrimArsivi.apk](https://github.com/olcaydunder/evrim-arsivi/releases/download/app/EvrimArsivi.apk) — telefona indirip kurun.
- **İçerikler:** `content/` klasörü. Uygulama `content/manifest.json` dosyasını okur; buraya eklenen her gönderi ve Reels uygulamada kendiliğinden görünür.

## iPhone (ve tarayıcı)

https://olcaydunder.github.io/evrim-arsivi/ adresini Safari'de açın → Paylaş → **Ana Ekrana Ekle**.

## İçerik eklemek

`content/<hesap>/<tür>/<içerik>/` klasörüne medya dosyalarını ve `meta.json`'u koyun, sonra `python3 tools/build_manifest.py` çalıştırın.
Hesaplar: `evrim`, `klinik`, `global`, `almanya`, `boyutla` · Türler: `reel`, `feed`, `story`.

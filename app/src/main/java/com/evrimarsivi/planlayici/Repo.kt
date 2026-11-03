package com.evrimarsivi.planlayici

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

const val BASE_URL = "https://raw.githubusercontent.com/olcaydunder/evrim-arsivi/main/"
const val MANIFEST_URL = BASE_URL + "content/manifest.json"

data class Item(
    val id: String,
    val type: String, // "carousel" | "reel"
    val order: Int,
    val title: String,
    val subtitle: String,
    val caption: String,
    val cover: String,
    val media: List<String>,
    val date: String = "", // YYYY-MM-DD: yalnızca o gün önerilir
) {
    val isReel get() = type == "reel"
}

data class Manifest(
    val version: Int,
    val appVersion: Int,
    val apkUrl: String,
    val items: List<Item>,
)

object Repo {
    private const val PREF = "evrim"

    fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ---- manifest ----
    private fun cacheFile(c: Context) = File(c.filesDir, "manifest.json")

    fun parse(text: String): Manifest {
        val o = JSONObject(text)
        val arr = o.getJSONArray("items")
        val items = (0 until arr.length()).map { i ->
            val it = arr.getJSONObject(i)
            val m = it.getJSONArray("media")
            Item(
                id = it.getString("id"),
                type = it.getString("type"),
                order = it.optInt("order", i),
                title = it.getString("title"),
                subtitle = it.optString("subtitle", ""),
                caption = it.optString("caption", ""),
                cover = it.optString("cover", m.optString(0, "")),
                media = (0 until m.length()).map { k -> m.getString(k) },
                date = it.optString("date", ""),
            )
        }.sortedWith(compareBy({ it.type }, { it.order }))
        return Manifest(o.optInt("version", 1), o.optInt("app_version", 0), o.optString("apk_url", ""), items)
    }

    fun cached(c: Context): Manifest? = try {
        val f = cacheFile(c)
        if (f.exists()) parse(f.readText()) else null
    } catch (e: Exception) { null }

    /** Ağdan çeker; başarısızsa önbelleği döndürür. */
    fun sync(c: Context): Manifest? {
        return try {
            val text = httpText(MANIFEST_URL + "?t=" + System.currentTimeMillis() / 60000)
            val m = parse(text)
            cacheFile(c).writeText(text)
            prefs(c).edit().putLong("last_sync", System.currentTimeMillis()).apply()
            m
        } catch (e: Exception) {
            cached(c)
        }
    }

    private fun httpText(url: String): String {
        val con = URL(url).openConnection() as HttpURLConnection
        con.connectTimeout = 15000; con.readTimeout = 30000
        con.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (con.responseCode != 200) throw RuntimeException("HTTP ${con.responseCode}")
            return con.inputStream.bufferedReader().use { it.readText() }
        } finally { con.disconnect() }
    }

    // ---- media ----
    fun localFile(c: Context, path: String): File {
        val f = File(c.cacheDir, "media/" + path.replace("/", "_"))
        f.parentFile?.mkdirs()
        return f
    }

    /** Dosyayı indirir (önbellekte varsa tekrar indirmez). */
    fun download(c: Context, path: String): File {
        val f = localFile(c, path)
        if (f.exists() && f.length() > 0) return f
        val tmp = File(f.path + ".part")
        val con = URL(BASE_URL + path).openConnection() as HttpURLConnection
        con.connectTimeout = 15000; con.readTimeout = 60000
        try {
            if (con.responseCode != 200) throw RuntimeException("HTTP ${con.responseCode}")
            con.inputStream.use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
        } finally { con.disconnect() }
        tmp.renameTo(f)
        return f
    }

    /** Galeriye (Resimler/EvrimArsivi veya Filmler/EvrimArsivi) kaydeder ve içerik adresini döndürür. */
    fun saveToGallery(c: Context, item: Item, index: Int, file: File): Uri {
        val key = "gal_${item.id}_$index"
        prefs(c).getString(key, null)?.let { s ->
            val u = Uri.parse(s)
            try {
                c.contentResolver.openInputStream(u)?.close()
                return u
            } catch (_: Exception) { }
        }
        val isVideo = file.name.endsWith(".mp4")
        val name = "evrim_${item.id}_${index + 1}" + if (isVideo) ".mp4" else ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, (if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/EvrimArsivi")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val coll = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = c.contentResolver.insert(coll, values) ?: throw RuntimeException("Galeriye yazılamadı")
        c.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        c.contentResolver.update(uri, values, null, null)
        prefs(c).edit().putString(key, uri.toString()).apply()
        return uri
    }

    // ---- durum ----
    fun isShared(c: Context, id: String) = prefs(c).getBoolean("shared_$id", false)
    fun setShared(c: Context, id: String, v: Boolean) = prefs(c).edit().putBoolean("shared_$id", v).apply()

    fun today(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    /** Bugüne tarihlenmiş içerik varsa onu, yoksa tarihsiz (veya tarihi geçmiş) sıradaki içeriği verir. */
    fun nextOf(c: Context, m: Manifest?, type: String): Item? {
        val open = m?.items?.filter { it.type == type && !isShared(c, it.id) } ?: return null
        val t = today()
        open.firstOrNull { it.date == t }?.let { return it }
        return open.filter { it.date.isEmpty() || it.date < t }.minByOrNull { it.order }
    }

    // Saatler: dakika cinsinden (gün başından)
    fun time(c: Context, type: String): Int =
        prefs(c).getInt("time_$type", if (type == "reel") 13 * 60 else 20 * 60)

    fun setTime(c: Context, type: String, minutes: Int) = prefs(c).edit().putInt("time_$type", minutes).apply()

    fun enabled(c: Context, type: String) = prefs(c).getBoolean("on_$type", true)
    fun setEnabled(c: Context, type: String, v: Boolean) = prefs(c).edit().putBoolean("on_$type", v).apply()

    fun hhmm(min: Int) = String.format("%02d:%02d", min / 60, min % 60)
}

package com.evrimarsivi.planlayici

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * İçerik deposuna (olcaydunder/evrim-arsivi) doğrudan yükleme.
 * Sunucu yok: dosyalar git blob'u olarak gönderilir, tek bir commit ile main dalına eklenir.
 * İçerik listesi (manifest) depodaki otomatik iş akışıyla yeniden üretilir.
 */
object GitHub {
    private const val API = "https://api.github.com/repos/olcaydunder/evrim-arsivi"

    class ApiError(val code: Int, msg: String) : Exception(msg)

    fun token(c: Context): String = Repo.prefs(c).getString("gh_token", "") ?: ""
    fun setToken(c: Context, t: String) = Repo.prefs(c).edit().putString("gh_token", t.trim()).apply()

    private fun open(token: String, method: String, path: String): HttpURLConnection {
        val con = URL(API + path).openConnection() as HttpURLConnection
        try { con.requestMethod = method } catch (e: java.net.ProtocolException) {
            con.requestMethod = "POST"; con.setRequestProperty("X-HTTP-Method-Override", method)
        }
        con.connectTimeout = 20000
        con.readTimeout = 180000
        con.setRequestProperty("Authorization", "Bearer $token")
        con.setRequestProperty("Accept", "application/vnd.github+json")
        con.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        con.setRequestProperty("User-Agent", "PaylasimPlanlayici")
        con.useCaches = false
        return con
    }

    private fun read(con: HttpURLConnection): JSONObject {
        try {
            val code = con.responseCode
            if (code !in 200..299) {
                val err = (con.errorStream ?: con.inputStream)?.bufferedReader()?.use { it.readText() } ?: ""
                throw ApiError(code, "HTTP $code ${err.take(200)}")
            }
            return JSONObject(con.inputStream.bufferedReader().use { it.readText() })
        } finally { con.disconnect() }
    }

    private fun json(token: String, method: String, path: String, body: JSONObject? = null): JSONObject {
        val con = open(token, method, path)
        if (body != null) {
            val bytes = body.toString().toByteArray()
            con.doOutput = true
            con.setRequestProperty("Content-Type", "application/json")
            con.setFixedLengthStreamingMode(bytes.size)
            con.outputStream.use { it.write(bytes) }
        }
        return read(con)
    }

    /** Anahtar geçerli ve depoya yazma izni varsa true. */
    fun canWrite(token: String): Boolean = try {
        json(token, "GET", "").optJSONObject("permissions")?.optBoolean("push", false) == true
    } catch (e: Exception) { false }

    private class Keep(out: OutputStream) : FilterOutputStream(out) {
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len) }
        override fun close() { flush() }
    }

    /** Dosyayı base64 olarak akış hâlinde gönderir (belleğe tamamını almaz); blob sha'sını döndürür. */
    fun blob(token: String, length: Long, input: () -> InputStream, progress: (Long) -> Unit = {}): String {
        val pre = "{\"encoding\":\"base64\",\"content\":\"".toByteArray()
        val post = "\"}".toByteArray()
        val b64 = 4 * ((length + 2) / 3)
        val con = open(token, "POST", "/git/blobs")
        con.doOutput = true
        con.setRequestProperty("Content-Type", "application/json")
        con.setFixedLengthStreamingMode(pre.size + b64 + post.size)
        con.outputStream.use { raw ->
            raw.write(pre)
            val enc = Base64.getEncoder().wrap(Keep(raw))
            input().use { inp ->
                val buf = ByteArray(64 * 1024)
                var sent = 0L
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    enc.write(buf, 0, n)
                    sent += n
                    progress(sent)
                }
            }
            enc.close()
            raw.write(post)
        }
        return read(con).getString("sha")
    }

    fun blobBytes(token: String, bytes: ByteArray): String =
        blob(token, bytes.size.toLong(), { bytes.inputStream() })

    private fun rawText(token: String, path: String): String {
        val con = open(token, "GET", path)
        con.setRequestProperty("Accept", "application/vnd.github.raw+json")
        try {
            if (con.responseCode !in 200..299) throw ApiError(con.responseCode, "HTTP ${con.responseCode}")
            return con.inputStream.bufferedReader().use { it.readText() }
        } finally { con.disconnect() }
    }

    /**
     * Verilen (yol → blob sha) dosyalarını tek commit ile main'e ekler; dal o arada ilerlediyse yeniden dener.
     * newItems verilirse content/manifest.json da aynı commit'te güncellenir (liste anında güncel olur);
     * güncellenmiş liste metni döndürülür.
     */
    fun commit(token: String, files: List<Pair<String, String>>, message: String, newItems: List<JSONObject> = emptyList()): String? {
        var last: Exception? = null
        repeat(6) {
            try {
                val head = json(token, "GET", "/git/ref/heads/main").getJSONObject("object").getString("sha")
                val baseTree = json(token, "GET", "/git/commits/$head").getJSONObject("tree").getString("sha")
                val tree = JSONArray()
                for ((path, sha) in files) tree.put(JSONObject().put("path", path).put("mode", "100644").put("type", "blob").put("sha", sha))
                var manifestText: String? = null
                if (newItems.isNotEmpty()) try {
                    val m = JSONObject(rawText(token, "/contents/content/manifest.json?ref=$head"))
                    val items = m.getJSONArray("items")
                    val ids = (0 until items.length()).map { items.getJSONObject(it).optString("id") }.toSet()
                    for (it in newItems) if (it.optString("id") !in ids) items.put(it)
                    val txt = m.toString(1)
                    tree.put(JSONObject().put("path", "content/manifest.json").put("mode", "100644").put("type", "blob")
                        .put("sha", blobBytes(token, txt.toByteArray())))
                    manifestText = txt
                } catch (e: ApiError) { manifestText = null }
                val t = json(token, "POST", "/git/trees", JSONObject().put("base_tree", baseTree).put("tree", tree)).getString("sha")
                val nc = json(token, "POST", "/git/commits",
                    JSONObject().put("message", message).put("tree", t).put("parents", JSONArray().put(head))).getString("sha")
                json(token, "PATCH", "/git/refs/heads/main", JSONObject().put("sha", nc))
                return manifestText
            } catch (e: ApiError) {
                last = e
                if (e.code != 422 && e.code != 409) throw e
                Thread.sleep(2000)
            }
        }
        throw last ?: RuntimeException("Depo meşgul")
    }
}

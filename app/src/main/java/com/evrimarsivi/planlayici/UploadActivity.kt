package com.evrimarsivi.planlayici

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Telefondan video ekleme: galeriden / Google Fotoğraflar'dan çoklu seçim ya da
 * Google Fotoğraflar'da "Paylaş → Paylaşım planlayıcı" ile gelen videolar.
 * Videolar seçilen hesabın "Telefon" bölümüne eklenir; diğer telefonlar da görür.
 */
class UploadActivity : Activity() {
    private val uris = ArrayList<Uri>()
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var countText: TextView
    private lateinit var titleIn: EditText
    private lateinit var capIn: EditText
    private lateinit var go: Button
    private var account = "klinik"
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        account = Repo.account(this)?.takeIf { it != YT_ACCOUNT } ?: "klinik"
        readIntent(intent)
        build()
        if (uris.isEmpty() && intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) pick()
    }

    @Suppress("DEPRECATION")
    private fun readIntent(i: Intent) {
        when (i.action) {
            Intent.ACTION_SEND -> (if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else i.getParcelableExtra(Intent.EXTRA_STREAM))?.let { uris.add(it) }
            Intent.ACTION_SEND_MULTIPLE -> (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else i.getParcelableArrayListExtra(Intent.EXTRA_STREAM))?.let { uris.addAll(it) }
        }
    }

    private fun field(hint: String, lines: Int): EditText = EditText(this).apply {
        this.hint = hint; setHintTextColor(Ui.MUTED); setTextColor(Ui.FG); textSize = 15f
        if (lines > 1) { minLines = lines; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }
        else inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        background = GradientDrawable().apply { cornerRadius = Ui.dp(this@UploadActivity, 10).toFloat(); setColor(Ui.PANEL); setStroke(Ui.dp(this@UploadActivity, 1), Ui.LINE) }
        val p = Ui.dp(this@UploadActivity, 12); setPadding(p, p, p, p)
    }

    private fun build() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG)
            val p = Ui.dp(this@UploadActivity, 16); setPadding(p, p, p, p)
        }
        root.addView(Ui.button(this, "‹ Geri", primary = false) { finish() })
        root.addView(Ui.text(this, "Telefondan video ekle", 24f, Ui.FG, true), Ui.lp(this, 14))
        root.addView(Ui.text(this, "Eklenen videolar seçtiğiniz hesabın \"Telefon\" bölümünde görünür; uygulamanın kurulu olduğu diğer telefonlar da görür. YouTube Shorts ve Instagram'da oradan paylaşırsınız.", 14f, Ui.MUTED), Ui.lp(this, 4))

        countText = Ui.text(this, "", 16f, Ui.GOLD, true)
        root.addView(countText, Ui.lp(this, 16))
        root.addView(Ui.button(this, "Video seç (galeri / Google Fotoğraflar)", primary = false) { pick() }, Ui.lp(this, 8))
        root.addView(Ui.text(this, "İpucu: Google Fotoğraflar'da videoları seçip Paylaş → \"Paylaşım planlayıcı\" diyerek de toplu ekleyebilirsiniz.", 13f, Ui.MUTED), Ui.lp(this, 6))

        root.addView(Ui.text(this, "Hangi hesap?", 16f, Ui.FG, true), Ui.lp(this, 18))
        val accs = (Repo.cached(this)?.accounts ?: DEFAULT_ACCOUNTS).filter { it.id != YT_ACCOUNT }
        val accBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun drawAccs() {
            accBox.removeAllViews()
            for (a in accs) {
                val b = Button(this).apply {
                    text = (if (a.id == account) "✓ " else "") + a.name; isAllCaps = false; textSize = 15f
                    setTextColor(if (a.id == account) Ui.BG else Ui.FG)
                    background = GradientDrawable().apply {
                        cornerRadius = Ui.dp(this@UploadActivity, 20).toFloat()
                        if (a.id == account) setColor(Ui.GOLD) else { setColor(Ui.PANEL); setStroke(Ui.dp(this@UploadActivity, 1), Ui.LINE) }
                    }
                }
                b.setOnClickListener { if (!busy) { account = a.id; drawAccs() } }
                accBox.addView(b, Ui.lp(this, 6))
            }
        }
        drawAccs()
        root.addView(accBox)

        root.addView(Ui.text(this, "Başlık (isteğe bağlı)", 16f, Ui.FG, true), Ui.lp(this, 18))
        titleIn = field("Örn. Muayene sonrası Pamuk", 1)
        root.addView(titleIn, Ui.lp(this, 6))
        root.addView(Ui.text(this, "Açıklama / etiketler (isteğe bağlı)", 16f, Ui.FG, true), Ui.lp(this, 14))
        capIn = field("Paylaşırken kopyalanacak metin", 4)
        root.addView(capIn, Ui.lp(this, 6))

        if (GitHub.token(this).isBlank()) root.addView(tokenCard(), Ui.lp(this, 18))

        go = Ui.button(this, "Yükle") { upload() }
        root.addView(go, Ui.lp(this, 20))
        status = Ui.text(this, "", 14f, Ui.MUTED)
        root.addView(status, Ui.lp(this, 10))

        if (GitHub.token(this).isNotBlank()) {
            root.addView(Ui.button(this, "Yükleme anahtarını değiştir", primary = false) {
                val card = tokenCard(); root.addView(card, root.indexOfChild(go)); card.requestFocus()
            }, Ui.lp(this, 28))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(root) })
        updateCount()
    }

    private fun tokenCard(): LinearLayout {
        val card = Ui.card(this)
        card.addView(Ui.text(this, "Yükleme anahtarı", 16f, Ui.FG, true))
        card.addView(Ui.text(this, "Bu telefondan video eklemek için bir kez gerekli. Anahtarı Olcay'dan alıp buraya yapıştırın (github_pat_ ile başlar).", 13f, Ui.MUTED), Ui.lp(this, 4))
        val tin = field("github_pat_…", 1).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        card.addView(tin, Ui.lp(this, 8))
        val msg = Ui.text(this, "", 13f, Ui.MUTED)
        card.addView(Ui.button(this, "Anahtarı kaydet", primary = false) {
            val t = tin.text.toString().trim()
            if (t.isEmpty()) return@button
            msg.text = "Denetleniyor…"; msg.setTextColor(Ui.MUTED)
            thread {
                val ok = GitHub.canWrite(t)
                runOnUiThread {
                    if (ok) { GitHub.setToken(this, t); msg.setTextColor(Ui.GREEN); msg.text = "✓ Anahtar kaydedildi."; tin.setText("") }
                    else { msg.setTextColor(Ui.GOLD); msg.text = "Anahtar çalışmadı. Doğru kopyalandığından ve süresinin dolmadığından emin olun." }
                }
            }
        }, Ui.lp(this, 8))
        card.addView(msg, Ui.lp(this, 6))
        return card
    }

    private fun updateCount() {
        countText.text = if (uris.isEmpty()) "Henüz video seçilmedi" else "${uris.size} video seçildi"
    }

    private fun pick() {
        if (busy) return
        val i = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "video/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(Intent.createChooser(i, "Videoları seçin"), 41)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 41 || resultCode != RESULT_OK || data == null) return
        val clip = data.clipData
        if (clip != null) for (k in 0 until clip.itemCount) clip.getItemAt(k).uri?.let { if (it !in uris) uris.add(it) }
        else data.data?.let { if (it !in uris) uris.add(it) }
        updateCount()
    }

    private fun say(s: String, color: Int = Ui.MUTED) = runOnUiThread { status.setTextColor(color); status.text = s }

    private fun upload() {
        if (busy) return
        val token = GitHub.token(this)
        if (uris.isEmpty()) { say("Önce video seçin.", Ui.GOLD); return }
        if (token.isBlank()) { say("Önce yükleme anahtarını kaydedin.", Ui.GOLD); return }
        busy = true; go.isEnabled = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val title = titleIn.text.toString().trim()
        val caption = capIn.text.toString().trim()
        val acc = account
        val list = ArrayList(uris)
        thread {
            var ok = 0
            val fails = ArrayList<String>()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val human = SimpleDateFormat("d MMM HH:mm", Locale("tr")).format(Date())
            for ((i, uri) in list.withIndex()) {
                val n = "${i + 1}/${list.size}"
                var tmp: File? = null
                try {
                    say("Video $n hazırlanıyor…")
                    val inf = Compressor.info(this, uri)
                    var src: () -> java.io.InputStream = { contentResolver.openInputStream(uri) ?: throw RuntimeException("Video açılamadı") }
                    var len = inf.size
                    var ext = if (inf.mime.contains("quicktime")) "mov" else "mp4"
                    if (len <= 0 || len > Compressor.COMPRESS_ABOVE) {
                        say("Video $n küçültülüyor… (uzun videolarda birkaç dakika sürebilir)")
                        val f = File(cacheDir, "up_$i.mp4"); f.delete()
                        Compressor.compress(this, uri, inf, f)
                        tmp = f; len = f.length(); ext = "mp4"
                        src = { f.inputStream() }
                    }
                    if (len > Compressor.LIMIT) throw RuntimeException("çok büyük (${len / 1048576} MB). Videoyu kısaltıp tekrar deneyin.")
                    val slug = "v$stamp-${(i + 1).toString().padStart(2, '0')}"
                    val base = "content/$acc/phone/$slug/"
                    val files = ArrayList<Pair<String, String>>()
                    val mb = len / 1048576.0
                    files.add(base + "video.$ext" to GitHub.blob(token, len, src) { sent ->
                        say("Video $n yükleniyor… %.0f / %.0f MB".format(sent / 1048576.0, mb))
                    })
                    Compressor.cover(this, uri)?.let { files.add(base + "cover.jpg" to GitHub.blobBytes(token, it)) }
                    val t = when {
                        title.isEmpty() -> "Telefon videosu · $human" + if (list.size > 1) " ($n)" else ""
                        list.size > 1 -> "$title (${i + 1})"
                        else -> title
                    }
                    val meta = JSONObject().put("title", t).put("subtitle", "Telefondan eklendi · $human")
                        .put("caption", caption).put("order", (System.currentTimeMillis() / 60000).toInt() + i)
                    files.add(base + "meta.json" to GitHub.blobBytes(token, meta.toString(1).toByteArray()))
                    say("Video $n kaydediliyor…")
                    val item = JSONObject().put("id", "$acc-phone-$slug").put("account", acc).put("type", "phone")
                        .put("order", meta.getInt("order")).put("title", t).put("subtitle", meta.getString("subtitle"))
                        .put("caption", caption)
                        .put("cover", if (files.any { it.first.endsWith("cover.jpg") }) base + "cover.jpg" else base + "video.$ext")
                        .put("media", org.json.JSONArray().put(base + "video.$ext"))
                    GitHub.commit(token, files, "Telefondan video: $t", listOf(item))?.let { Repo.saveCache(this, it) }
                    ok++
                } catch (e: Exception) {
                    fails.add("Video ${i + 1}: " + when {
                        e is GitHub.ApiError && e.code == 401 -> "yükleme anahtarı geçersiz veya süresi dolmuş"
                        e is GitHub.ApiError && e.code == 403 -> "anahtarın yazma izni yok"
                        e.message?.startsWith("çok büyük") == true -> e.message
                        e is java.io.IOException -> "internet bağlantısı koptu"
                        else -> "yüklenemedi (${e.javaClass.simpleName})"
                    })
                } finally { tmp?.delete() }
            }
            runOnUiThread {
                busy = false; go.isEnabled = true
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val sb = StringBuilder()
                if (ok > 0) sb.append("✓ $ok video eklendi. 1-2 dakika içinde \"Telefon\" bölümünde görünür.")
                if (fails.isNotEmpty()) sb.append(if (sb.isEmpty()) "" else "\n").append(fails.joinToString("\n"))
                status.setTextColor(if (fails.isEmpty()) Ui.GREEN else Ui.GOLD)
                status.text = sb.toString()
                if (ok > 0) {
                    Repo.setAccount(this, acc); Repo.setTab(this, "phone")
                    Repo.prefs(this).edit().putLong("uploaded_at", System.currentTimeMillis()).apply()
                    uris.clear(); updateCount(); titleIn.setText(""); capIn.setText("")
                }
            }
        }
    }
}

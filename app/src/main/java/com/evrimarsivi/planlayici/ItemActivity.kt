package com.evrimarsivi.planlayici

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

private const val INSTAGRAM = "com.instagram.android"
private const val YOUTUBE = "com.google.android.youtube"

class ItemActivity : Activity() {
    private var item: Item? = null
    private lateinit var status: TextView
    private lateinit var doneBtn: android.widget.Button
    private var busy = false
    private var sharedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        val id = intent.getStringExtra("id")
        item = Repo.cached(this)?.items?.firstOrNull { it.id == id }
        val it = item
        if (it == null) {
            Toast.makeText(this, "İçerik bulunamadı. Ana ekranda içerikleri yenileyin.", Toast.LENGTH_LONG).show()
            finish(); return
        }
        build(it)
    }

    private fun build(it: Item) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            val p = Ui.dp(this@ItemActivity, 16); setPadding(p, p, p, p)
        }
        val acc = Repo.accountName(Repo.cached(this), it.account).uppercase()
        val kind = when {
            it.type == "feed" && it.media.size > 1 -> "KAYDIRMALI GÖNDERİ · ${it.media.size} SAYFA"
            else -> typeName(it.type).uppercase()
        }
        root.addView(Ui.text(this, "$acc · $kind", 12f, Ui.GOLD, true))
        root.addView(Ui.text(this, it.title, 24f, Ui.FG, true), Ui.lp(this, 2))
        if (it.subtitle.isNotBlank()) root.addView(Ui.text(this, it.subtitle, 14f, Ui.MUTED))

        // görseller
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tall = it.type != "feed"
        val w = Ui.dp(this, if (tall) 180 else 220)
        val h = if (tall) w * 16 / 9 else w * 5 / 4
        val previews = if (it.isVideo) listOf(it.cover) else it.media
        for ((i, p) in previews.withIndex()) {
            val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(Ui.PANEL) }
            val lp = LinearLayout.LayoutParams(w, h).apply { if (i > 0) leftMargin = Ui.dp(this@ItemActivity, 8) }
            strip.addView(iv, lp)
            thread {
                try {
                    val f = Repo.download(this, p)
                    val bmp = Ui.loadBitmap(f, w)
                    runOnUiThread { iv.setImageBitmap(bmp) }
                } catch (_: Exception) { }
            }
        }
        root.addView(HorizontalScrollView(this).apply { addView(strip); isHorizontalScrollBarEnabled = false }, Ui.lp(this, 14))

        status = Ui.text(this, if (Repo.isShared(this, it.id)) "✓ Bu içerik paylaşıldı olarak işaretli." else "", 14f, Ui.GREEN)
        root.addView(status, Ui.lp(this, 12))

        root.addView(Ui.button(this, "Instagram'da paylaş") { share(it) }, Ui.lp(this, 10))
        if (it.isVideo) root.addView(Ui.button(this, "YouTube Shorts'ta paylaş") { share(it, YOUTUBE) }, Ui.lp(this, 10))
        root.addView(Ui.text(this, "Açıklama otomatik kopyalanır. Instagram'da açıklama alanına basılı tutup Yapıştır deyin.", 13f, Ui.MUTED), Ui.lp(this, 6))
        root.addView(Ui.button(this, "Sadece galeriye kaydet", primary = false) { saveOnly(it) }, Ui.lp(this, 10))
        root.addView(Ui.button(this, "Açıklamayı kopyala", primary = false) { copy(it) }, Ui.lp(this, 10))
        doneBtn = Ui.button(this, if (Repo.isShared(this, it.id)) "Paylaşılmadı olarak geri al" else "Paylaşıldı olarak işaretle", primary = false) {
            val v = !Repo.isShared(this, it.id)
            Repo.setShared(this, it.id, v)
            status.text = if (v) "✓ Paylaşıldı olarak işaretlendi. Listeden kaldırıldı; sıradaki içerik bir sonraki hatırlatmada gelecek." else ""
            if (v) status.postDelayed({ finish() }, 1200)
            doneBtn.text = if (v) "Paylaşılmadı olarak geri al" else "Paylaşıldı olarak işaretle"
        }
        root.addView(doneBtn, Ui.lp(this, 10))

        root.addView(Ui.text(this, "Açıklama", 16f, Ui.FG, true), Ui.lp(this, 20))
        val cap = Ui.card(this)
        cap.addView(Ui.text(this, it.caption, 14f, Ui.FG).apply { setTextIsSelectable(true) })
        root.addView(cap, Ui.lp(this, 6))

        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(root) })
    }

    private fun copy(it: Item) {
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("Evrim Arşivi açıklama", it.caption))
        Toast.makeText(this, "Açıklama kopyalandı", Toast.LENGTH_SHORT).show()
    }

    private fun prepare(it: Item, done: (List<Uri>) -> Unit) {
        if (busy) return
        busy = true
        status.setTextColor(Ui.MUTED)
        thread {
            try {
                val uris = it.media.mapIndexed { i, p ->
                    runOnUiThread { status.text = "Hazırlanıyor… ${i + 1} / ${it.media.size}" }
                    val f = Repo.download(this, p)
                    Repo.saveToGallery(this, it, i, f)
                }
                runOnUiThread { busy = false; status.setTextColor(Ui.GREEN); done(uris) }
            } catch (e: Exception) {
                runOnUiThread {
                    busy = false
                    status.setTextColor(Ui.GOLD)
                    status.text = "İndirilemedi: internet bağlantınızı kontrol edip tekrar deneyin."
                }
            }
        }
    }

    private fun saveOnly(it: Item) = prepare(it) { uris ->
        status.text = "✓ ${uris.size} dosya galeriye kaydedildi (EvrimArsivi klasörü)."
    }

    private fun share(it: Item, pkg: String = INSTAGRAM) = prepare(it) { uris ->
        copy(it)
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = if (it.isVideo) "video/*" else "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra(Intent.EXTRA_TEXT, it.caption)
        status.text = (if (pkg == YOUTUBE) "YouTube" else "Instagram") + " açılıyor… Açıklama panoda hazır."
        sharedOnce = true
        try {
            startActivity(Intent(intent).setPackage(pkg))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent.createChooser(intent, "Paylaş"))
        }
    }

    override fun onResume() {
        super.onResume()
        val it = item ?: return
        if (sharedOnce && !Repo.isShared(this, it.id) && ::status.isInitialized) {
            status.setTextColor(Ui.GOLD)
            status.text = "Paylaştıysanız aşağıdan \"Paylaşıldı olarak işaretle\"ye basın."
        }
    }
}

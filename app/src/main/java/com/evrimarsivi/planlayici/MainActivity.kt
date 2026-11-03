package com.evrimarsivi.planlayici


import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private var manifest: Manifest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        Scheduler.ensureChannel(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            val p = Ui.dp(this@MainActivity, 16)
            setPadding(p, p, p, p)
        }
        root.addView(Ui.text(this, "Evrim Arşivi", 26f, Ui.FG, true))
        root.addView(Ui.text(this, "Paylaşım planlayıcı", 14f, Ui.MUTED))
        status = Ui.text(this, "", 13f, Ui.MUTED)
        root.addView(status, Ui.lp(this, 4))

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list, Ui.lp(this, 6))

        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(root) })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
        }
        Scheduler.scheduleAll(this)
        manifest = Repo.cached(this)
        render()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) render()
    }

    private fun refresh() {
        status.text = "Yeni içerikler denetleniyor…"
        thread {
            val m = Repo.sync(this)
            runOnUiThread {
                manifest = m
                status.text = if (m == null) "İnternete bağlanılamadı. Bağlantı gelince yeniden deneyin."
                else "İçerikler güncel · ${m.items.size} içerik"
                render()
            }
        }
    }

    private fun render() {
        list.removeAllViews()
        val m = manifest

        // Uygulama güncellemesi
        if (m != null && m.appVersion > currentVersion() && m.apkUrl.isNotBlank()) {
            val card = Ui.card(this)
            card.addView(Ui.text(this, "Uygulamanın yeni sürümü var", 16f, Ui.GOLD, true))
            card.addView(Ui.button(this, "Yeni sürümü indir") {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.apkUrl)))
            }, Ui.lp(this, 8))
            list.addView(card, Ui.lp(this, 10))
        }

        for (type in listOf("carousel", "reel")) list.addView(planCard(type, m), Ui.lp(this, 12))

        list.addView(Ui.button(this, "İçerikleri yenile", primary = false) { refresh() }, Ui.lp(this, 12))

        if (m == null) return
        for (type in listOf("carousel", "reel")) {
            val items = m.items.filter { it.type == type }
            if (items.isEmpty()) continue
            val done = items.count { Repo.isShared(this, it.id) }
            list.addView(Ui.text(this, (if (type == "reel") "Reels" else "Kaydırmalı gönderiler") + "  ·  $done / ${items.size} paylaşıldı", 17f, Ui.FG, true), Ui.lp(this, 22))
            for (it in items) list.addView(itemRow(it), Ui.lp(this, 8))
        }
    }

    private fun planCard(type: String, m: Manifest?): LinearLayout {
        val card = Ui.card(this)
        val head = Ui.row(this)
        head.addView(Ui.text(this, if (type == "reel") "Reels hatırlatması" else "Gönderi hatırlatması", 16f, Ui.FG, true))
        head.addView(Ui.spacer(this))
        val sw = Switch(this).apply {
            isChecked = Repo.enabled(this@MainActivity, type)
            setOnCheckedChangeListener { _, v ->
                Repo.setEnabled(this@MainActivity, type, v); Scheduler.schedule(this@MainActivity, type); render()
            }
        }
        head.addView(sw)
        card.addView(head)

        val t = Repo.time(this, type)
        val next = Repo.nextOf(this, m, type)
        val info = when {
            !Repo.enabled(this, type) -> "Kapalı"
            next == null -> "Her gün ${Repo.hhmm(t)} · Sırada paylaşılacak içerik yok"
            else -> "Her gün ${Repo.hhmm(t)} · Sıradaki: ${next.title}"
        }
        card.addView(Ui.text(this, info, 14f, Ui.MUTED), Ui.lp(this, 4))

        val r = Ui.row(this)
        r.addView(Ui.button(this, "Saati değiştir", primary = false) {
            TimePickerDialog(this, { _, h, mi ->
                Repo.setTime(this, type, h * 60 + mi); Scheduler.schedule(this, type); render()
                Toast.makeText(this, "Her gün ${Repo.hhmm(h * 60 + mi)} için ayarlandı", Toast.LENGTH_SHORT).show()
            }, t / 60, t % 60, true).show()
        })
        if (next != null) {
            r.addView(Ui.spacer(this))
            r.addView(Ui.button(this, "Şimdi paylaş") { open(next) })
        }
        card.addView(r, Ui.lp(this, 10))
        return card
    }

    private fun itemRow(it: Item): LinearLayout {
        val card = Ui.card(this)
        val r = Ui.row(this)
        val img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(Ui.LINE) }
        val w = Ui.dp(this, 56)
        r.addView(img, LinearLayout.LayoutParams(w, if (it.isReel) w * 16 / 9 / 2 * 2 else w * 5 / 4))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(this@MainActivity, 12), 0, 0, 0) }
        col.addView(Ui.text(this, it.title, 15f, Ui.FG, true))
        if (it.subtitle.isNotBlank()) col.addView(Ui.text(this, it.subtitle, 13f, Ui.MUTED))
        val shared = Repo.isShared(this, it.id)
        col.addView(Ui.text(this, if (shared) "✓ Paylaşıldı" else "Bekliyor", 12f, if (shared) Ui.GREEN else Ui.GOLD))
        r.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(r)
        card.setOnClickListener { _ -> open(it) }
        // küçük kapak görseli
        thread {
            try {
                val f = Repo.download(this, it.cover)
                val bmp = Ui.loadBitmap(f, w * 2)
                runOnUiThread { img.setImageBitmap(bmp) }
            } catch (_: Exception) { }
        }
        return card
    }

    private fun open(it: Item) {
        startActivity(Intent(this, ItemActivity::class.java).putExtra("id", it.id))
    }

    private fun currentVersion(): Int = try {
        val pi = packageManager.getPackageInfo(packageName, 0)
        pi.longVersionCode.toInt()
    } catch (e: Exception) { 0 }
}

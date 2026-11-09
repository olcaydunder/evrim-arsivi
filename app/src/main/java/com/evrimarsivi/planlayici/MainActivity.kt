package com.evrimarsivi.planlayici

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private var manifest: Manifest? = null
    private var showShared = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        Scheduler.ensureChannel(this)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            val p = Ui.dp(this@MainActivity, 16)
            setPadding(p, p, p, p)
        }
        status = Ui.text(this, "", 13f, Ui.MUTED)
        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(root) })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
        }
        manifest = Repo.cached(this)
        Scheduler.scheduleAll(this)
        render()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) render()
        // Telefondan video eklendiyse liste depoda ~1 dakika içinde güncellenir: birkaç kez yenile
        val up = Repo.prefs(this).getLong("uploaded_at", 0L)
        if (up > 0 && System.currentTimeMillis() - up < 5 * 60_000 && ::root.isInitialized) {
            refresh()
            root.postDelayed({ if (!isFinishing) refresh() }, 45_000)
            root.postDelayed({ if (!isFinishing) refresh() }, 90_000)
        }
    }

    private fun refresh() {
        status.text = "Yeni içerikler denetleniyor…"
        thread {
            val m = Repo.sync(this)
            runOnUiThread {
                manifest = m
                status.text = if (m == null) "İnternete bağlanılamadı. Bağlantı gelince yeniden deneyin." else "İçerikler güncel"
                Scheduler.scheduleAll(this)
                render()
            }
        }
    }

    // ---------- ekranlar ----------

    private fun render() {
        root.removeAllViews()
        (status.parent as? LinearLayout)?.removeView(status)
        val acc = Repo.account(this)
        if (acc == null) renderPicker(first = true) else renderAccount(acc)
    }

    private fun renderPicker(first: Boolean) {
        root.addView(Ui.text(this, "Paylaşım planlayıcı", 26f, Ui.FG, true))
        root.addView(Ui.text(this, if (first) "Hangi hesap için paylaşım yapacaksınız?" else "Hesap seçin", 16f, Ui.MUTED), Ui.lp(this, 6))
        val accounts = manifest?.accounts ?: DEFAULT_ACCOUNTS
        for (a in accounts) {
            val waiting = manifest?.items?.count { it.account == a.id && !Repo.isDone(this, it) } ?: 0
            val card = Ui.card(this)
            card.addView(Ui.text(this, a.name, 18f, Ui.FG, true))
            card.addView(Ui.text(this, if (waiting == 0) "Bekleyen içerik yok" else "$waiting içerik paylaşılmayı bekliyor", 13f, if (waiting == 0) Ui.MUTED else Ui.GOLD), Ui.lp(this, 2))
            card.setOnClickListener { Repo.setAccount(this, a.id); render() }
            root.addView(card, Ui.lp(this, 12))
        }
        root.addView(status, Ui.lp(this, 14))
    }

    private fun renderAccount(acc: String) {
        val m = manifest
        val head = Ui.row(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(this, Repo.accountName(m, acc), 22f, Ui.FG, true))
        col.addView(Ui.text(this, "Paylaşım planlayıcı", 13f, Ui.MUTED))
        head.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(Ui.button(this, "Hesap değiştir", primary = false) {
            root.removeAllViews(); (status.parent as? LinearLayout)?.removeView(status); renderPicker(first = false)
        })
        root.addView(head)
        root.addView(status, Ui.lp(this, 6))

        if (m != null && m.appVersion > currentVersion() && m.apkUrl.isNotBlank()) {
            val card = Ui.card(this)
            card.addView(Ui.text(this, "Uygulamanın yeni sürümü var", 16f, Ui.GOLD, true))
            card.addView(Ui.button(this, "Yeni sürümü indir") {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.apkUrl)))
            }, Ui.lp(this, 8))
            root.addView(card, Ui.lp(this, 10))
        }

        // Sekmeler: Reels · Gönderi · Hikâye · Telefon  (YouTube: Shorts · Videolar)
        val yt = acc == YT_ACCOUNT
        val tab = Repo.tabFor(this, acc)
        val tabs = Ui.row(this)
        val types = typesFor(acc)
        for (t in types) {
            val n = m?.items?.count { it.account == acc && it.type == t && !Repo.isDone(this, it) } ?: 0
            val b = tabButton("${typeName(t)}${if (n > 0) " ($n)" else ""}", t == tab) { Repo.setTab(this, t); showShared = false; render() }
            tabs.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (t != types.first()) leftMargin = Ui.dp(this@MainActivity, 6)
            })
        }
        root.addView(tabs, Ui.lp(this, 14))

        if (tab == "phone") {
            root.addView(Ui.button(this, "+ Telefondan video ekle") {
                startActivity(Intent(this, UploadActivity::class.java))
            }, Ui.lp(this, 12))
        }
        root.addView(planCard(acc, tab, m), Ui.lp(this, 12))

        val all = m?.items?.filter { it.account == acc && it.type == tab } ?: emptyList()
        val waiting = all.filter { !Repo.isDone(this, it) }
        val shared = all.filter { Repo.isDone(this, it) }

        root.addView(Ui.text(this, if (yt) "Yüklenmeyi bekleyenler" else "Paylaşılmayı bekleyenler", 17f, Ui.FG, true), Ui.lp(this, 20))
        if (waiting.isEmpty()) {
            root.addView(Ui.text(this, if (m == null) "İçerikler yüklenemedi. İnternet bağlantınızı kontrol edin."
            else if (yt) "Yüklenmeyi bekleyen ${typeName(tab).lowercase()} yok. Yeni içerik hazırlandığında burada görünecek."
            else "Bu bölümde bekleyen ${if (tab == "phone") "telefon videosu" else typeName(tab).lowercase()} yok. Yeni içerik eklendiğinde burada görünecek.", 14f, Ui.MUTED), Ui.lp(this, 6))
        }
        for (it in waiting) root.addView(itemRow(it), Ui.lp(this, 8))

        if (yt && shared.isNotEmpty()) {
            // Kanalda yayında olanlar her zaman görünür
            root.addView(Ui.text(this, "Kanalda yayında (${shared.size})", 17f, Ui.FG, true), Ui.lp(this, 22))
            for (it in shared) root.addView(itemRow(it), Ui.lp(this, 8))
        } else if (shared.isNotEmpty()) {
            root.addView(Ui.button(this, if (showShared) "Paylaşılanları gizle" else "Paylaşılanları göster (${shared.size})", primary = false) {
                showShared = !showShared; render()
            }, Ui.lp(this, 16))
            if (showShared) for (it in shared) root.addView(itemRow(it), Ui.lp(this, 8))
        }

        root.addView(Ui.button(this, "İçerikleri yenile", primary = false) { refresh() }, Ui.lp(this, 16))
    }

    private fun tabButton(label: String, active: Boolean, onClick: () -> Unit): Button = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f
        setTextColor(if (active) Ui.BG else Ui.FG)
        background = GradientDrawable().apply {
            cornerRadius = Ui.dp(this@MainActivity, 20).toFloat()
            if (active) setColor(Ui.GOLD) else { setColor(Ui.PANEL); setStroke(Ui.dp(this@MainActivity, 1), Ui.LINE) }
        }
        setOnClickListener { onClick() }
    }

    private fun planCard(acc: String, type: String, m: Manifest?): LinearLayout {
        val card = Ui.card(this)
        val head = Ui.row(this)
        head.addView(Ui.text(this, "${typeName(type)} hatırlatması", 16f, Ui.FG, true))
        head.addView(Ui.spacer(this))
        head.addView(Switch(this).apply {
            isChecked = Repo.enabled(this@MainActivity, acc, type)
            setOnCheckedChangeListener { _, v ->
                Repo.setEnabled(this@MainActivity, acc, type, v); Scheduler.schedule(this@MainActivity, acc, type); render()
            }
        })
        card.addView(head)

        val t = Repo.time(this, acc, type)
        val next = Repo.nextOf(this, m, acc, type)
        val info = when {
            !Repo.enabled(this, acc, type) -> "Kapalı"
            next == null -> "Her gün ${Repo.hhmm(t)} · Sırada içerik yok, bildirim gelmez"
            else -> "Her gün ${Repo.hhmm(t)} · Sıradaki: ${next.title}"
        }
        card.addView(Ui.text(this, info, 14f, Ui.MUTED), Ui.lp(this, 4))

        val r = Ui.row(this)
        r.addView(Ui.button(this, "Saati değiştir", primary = false) {
            TimePickerDialog(this, { _, h, mi ->
                Repo.setTime(this, acc, type, h * 60 + mi); Scheduler.schedule(this, acc, type); render()
                Toast.makeText(this, "Her gün ${Repo.hhmm(h * 60 + mi)} için ayarlandı", Toast.LENGTH_SHORT).show()
            }, t / 60, t % 60, true).show()
        })
        if (next != null) {
            r.addView(Ui.spacer(this))
            r.addView(Ui.button(this, if (acc == YT_ACCOUNT) "Şimdi yükle" else "Şimdi paylaş") { open(next) })
        }
        card.addView(r, Ui.lp(this, 10))
        return card
    }

    private fun itemRow(it: Item): LinearLayout {
        val card = Ui.card(this)
        val r = Ui.row(this)
        val img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(Ui.LINE) }
        val w = Ui.dp(this, 56)
        val h = when (it.type) { "feed" -> w * 5 / 4; "video" -> w * 9 / 16; else -> w * 16 / 9 }
        r.addView(img, LinearLayout.LayoutParams(w, h))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(this@MainActivity, 12), 0, 0, 0) }
        col.addView(Ui.text(this, it.title, 15f, Ui.FG, true))
        if (it.subtitle.isNotBlank()) col.addView(Ui.text(this, it.subtitle, 13f, Ui.MUTED))
        val shared = Repo.isDone(this, it)
        val extra = if (it.date.isNotEmpty()) " · ${it.date.substring(8, 10)}.${it.date.substring(5, 7)} tarihinde önerilir" else ""
        val state = when { it.published -> "✓ Kanalda yayında"; shared && it.isYouTube -> "✓ Yüklendi"; shared -> "✓ Paylaşıldı"; it.isYouTube -> "Yüklenmeyi bekliyor"; else -> "Bekliyor" }
        col.addView(Ui.text(this, state + extra, 12f, if (shared) Ui.GREEN else Ui.GOLD))
        r.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(r)
        card.setOnClickListener { _ -> open(it) }
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
        packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
    } catch (e: Exception) { 0 }
}

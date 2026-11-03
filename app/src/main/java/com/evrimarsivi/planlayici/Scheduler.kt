package com.evrimarsivi.planlayici

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Notification
import java.util.Calendar
import kotlin.concurrent.thread

object Scheduler {
    const val CHANNEL = "paylasim"

    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "Paylaşım hatırlatmaları", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Gönderi ve Reels zamanı geldiğinde haber verir"
            nm.createNotificationChannel(ch)
        }
    }

    private fun code(account: String, type: String): Int {
        val a = DEFAULT_ACCOUNTS.indexOfFirst { it.id == account }.let { if (it < 0) (account.hashCode() and 0xff) + 10 else it }
        return 100 + a * 10 + TYPES.indexOf(type).coerceAtLeast(0)
    }

    private fun pending(c: Context, account: String, type: String): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).putExtra("type", type).putExtra("account", account)
        return PendingIntent.getBroadcast(c, code(account, type), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun nextTrigger(minutes: Int): Long {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, minutes / 60)
        cal.set(Calendar.MINUTE, minutes % 60)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= now + 30_000) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun scheduleAll(c: Context) {
        val accs = Repo.cached(c)?.accounts ?: DEFAULT_ACCOUNTS
        for (a in accs) for (t in TYPES) schedule(c, a.id, t)
    }

    fun schedule(c: Context, account: String, type: String) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = pending(c, account, type)
        am.cancel(pi)
        if (!Repo.enabled(c, account, type)) return
        val at = nextTrigger(Repo.time(c, account, type))
        // Tam saat izni gerekmeyen, uyku modunda da çalışan alarm (birkaç dakika sapabilir)
        am.setWindow(AlarmManager.RTC_WAKEUP, at, 5 * 60_000L, pi)
    }

    fun notifyItem(c: Context, item: Item, accountName: String) {
        ensureChannel(c)
        val open = Intent(c, ItemActivity::class.java).putExtra("id", item.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(c, item.id.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val kind = "Bugünkü ${typeName(item.type).lowercase()} hazır"
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setSubText(accountName)
            .setContentTitle("$kind: ${item.title}")
            .setContentText("Dokunun: açıklama kopyalanır, Instagram açılır.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            c.getSystemService(NotificationManager::class.java).notify(item.id.hashCode(), n)
        } catch (_: SecurityException) { }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val type = intent.getStringExtra("type") ?: return
        val account = intent.getStringExtra("account") ?: "evrim"
        val pr = goAsync()
        thread {
            try {
                val m = Repo.sync(c)
                Repo.nextOf(c, m, account, type)?.let { Scheduler.notifyItem(c, it, Repo.accountName(m, account)) }
                Scheduler.schedule(c, account, type)
            } finally { pr.finish() }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        Scheduler.scheduleAll(c)
    }
}

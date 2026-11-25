package com.digital.sanghaworld.hall

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.digital.sanghaworld.MainActivity
import com.digital.sanghaworld.R
import com.digital.sanghaworld.audio.GongCatalog
import com.digital.sanghaworld.auth.TokenStore

object SittingAlarms {
    private const val PREFS = "sitting_alarms"
    private const val KEY_CODES = "codes"
    private const val CHANNEL = "sitting_alerts"
    const val KIND_REMIND = "remind"
    const val KIND_START = "start"

    @Volatile
    var inHallForStart: Boolean = false

    fun reschedule(context: Context, cards: List<HallCard>) {
        val app = context.applicationContext
        val manager = app.getSystemService(AlarmManager::class.java) ?: return
        cancelStored(app, manager)
        val now = System.currentTimeMillis()
        val codes = mutableSetOf<String>()
        cards.filter { it.joined }.distinctBy { it.hall.id }.forEach { card ->
            val start = runCatching { futureStart(app, card, now) }.getOrNull() ?: return@forEach
            val remindAt = start - 15 * 60_000L
            if (remindAt > now) {
                val code = schedule(app, manager, card, remindAt, KIND_REMIND)
                codes += code.toString()
            }
            val startCode = schedule(app, manager, card, start, KIND_START)
            codes += startCode.toString()
        }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_CODES, codes)
            .apply()
    }

    fun cancelAll(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(AlarmManager::class.java) ?: return
        cancelStored(app, manager)
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_CODES).apply()
    }

    private fun schedule(
        context: Context,
        manager: AlarmManager,
        card: HallCard,
        atMillis: Long,
        kind: String
    ): Int {
        val code = requestCode(card.hall.id, kind)
        val gongId = if (card.hall.audioType == AudioType.BELL) card.hall.audioFileName else null
        val pending = pending(context, code, card.hall.id, card.hall.name, kind, gongId)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, open), pending)
        }.onFailure {
            val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
            val exact = if (exactAllowed) {
                runCatching {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
                    true
                }.getOrDefault(false)
            } else {
                false
            }
            if (!exact) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            }
        }
        return code
    }

    /** The next sitting that has not started yet. A sitting already under way schedules the one after it. */
    private fun futureStart(context: Context, card: HallCard, now: Long): Long? {
        if (card.nextStartMillis > now) return card.nextStartMillis
        val after = maxOf(now, card.nextStartMillis + card.hall.durationSeconds * 1000L)
        return HallStore(context).nextOccurrence(card.schedule, card.hall.durationSeconds, after + 1)
    }

    private fun cancelStored(context: Context, manager: AlarmManager) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val codes = prefs.getStringSet(KEY_CODES, emptySet()).orEmpty()
        codes.mapNotNull { it.toIntOrNull() }.forEach { code ->
            val pending = pending(context, code, "", "", KIND_REMIND)
            manager.cancel(pending)
        }
    }

    private fun requestCode(hallId: String, kind: String): Int {
        val base = hallId.hashCode() and 0x7fff
        return if (kind == KIND_START) base or 0x8000 else base
    }

    private fun pending(
        context: Context,
        code: Int,
        hallId: String,
        name: String,
        kind: String,
        gongId: String? = null
    ): PendingIntent {
        val intent = Intent(context, SittingAlarmReceiver::class.java).apply {
            action = "com.digital.sanghaworld.SITTING_ALARM"
            putExtra("hallId", hallId)
            putExtra("name", name)
            putExtra("kind", kind)
            putExtra("gongId", gongId)
        }
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class SittingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val power = context.getSystemService(PowerManager::class.java)
        val wake = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "esangha:sitting")
        wake?.acquire(20_000L)
        val rawName = intent.getStringExtra("name").orEmpty()
        val name = if (rawName.isBlank()) "Meditation" else camelCaseHallName(rawName)
        val kind = intent.getStringExtra("kind") ?: return
        val start = kind == SittingAlarms.KIND_START
        val title = if (start) "Meditation is starting" else "Meditation in 15 minutes"
        val body = if (start) "$name is starting now." else "$name starts in 15 minutes."
        notify(context, title, body, name.hashCode() xor kind.hashCode())
        if (start && !SittingAlarms.inHallForStart) {
            ring(context, intent.getStringExtra("gongId"))
        }
        val pendingResult = goAsync()
        Thread {
            try {
                val app = context.applicationContext
                val tokens = TokenStore(app)
                if (tokens.isSignedIn()) {
                    val disc = runCatching { HallApi(tokens).halls() }.getOrNull()
                    if (disc != null) {
                        SittingAlarms.reschedule(app, disc.sittingNow + disc.startingSoon + disc.myHalls)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun notify(context: Context, title: String, body: String, id: Int) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "sitting_alerts",
                "Sitting reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "A reminder before a sitting, and a bell when it starts."
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, "sitting_alerts")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching { manager.notify(id, notification) }
    }

    private fun ring(context: Context, gongId: String?) {
        val resId = GongCatalog.byId(gongId ?: GongCatalog.DEFAULT_ID).resId
        val player = MediaPlayer.create(context.applicationContext, resId) ?: return
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        player.setOnCompletionListener {
            it.release()
        }
        player.start()
    }
}

class SittingAlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        Thread {
            val app = context.applicationContext
            val tokens = TokenStore(app)
            val api = HallApi(tokens)
            val cards = if (tokens.isSignedIn()) {
                runCatching { api.halls() }.getOrNull()?.let { disc ->
                    (disc.sittingNow + disc.startingSoon + disc.myHalls).distinctBy { it.hall.id }
                }
            } else {
                null
            }
            if (cards != null) SittingAlarms.reschedule(app, cards)
            pending.finish()
        }.start()
    }
}

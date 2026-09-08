package com.digital.sanghaworld.logging

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailyTotal(
    val date: LocalDate,
    val totalMillis: Long,
    val sessionCount: Int = 1
)

object MeditationLogStore {
    private const val PREFS = "meditation_log_prefs"
    private const val LEGACY_FILE = "meditation_log.json"
    private const val KEY_MIGRATED = "migrated_json"
    private const val PREFIX_MILLIS = "m:"
    private const val PREFIX_COUNT = "c:"
    private val lock = Any()

    fun addSession(
        context: Context,
        durationMillis: Long,
        sessionEndTimeMillis: Long = System.currentTimeMillis()
    ) {
        if (durationMillis <= 0) return
        val date = Instant.ofEpochMilli(sessionEndTimeMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()

        synchronized(lock) {
            migrateIfNeeded(context)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val total = prefs.getLong(PREFIX_MILLIS + date, 0L) + durationMillis
            val count = prefs.getInt(PREFIX_COUNT + date, 0) + 1
            prefs.edit()
                .putLong(PREFIX_MILLIS + date, total)
                .putInt(PREFIX_COUNT + date, count)
                .commit()
        }
    }

    fun deleteDay(context: Context, date: LocalDate) {
        synchronized(lock) {
            migrateIfNeeded(context)
            val key = date.toString()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(PREFIX_MILLIS + key)
                .remove(PREFIX_COUNT + key)
                .commit()
        }
    }

    fun loadDailyTotals(context: Context): List<DailyTotal> {
        synchronized(lock) {
            migrateIfNeeded(context)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return prefs.all.mapNotNull { (key, value) ->
                if (!key.startsWith(PREFIX_MILLIS)) return@mapNotNull null
                val date = runCatching { LocalDate.parse(key.removePrefix(PREFIX_MILLIS)) }.getOrNull()
                    ?: return@mapNotNull null
                val millis = (value as? Long) ?: (value as? Int)?.toLong() ?: return@mapNotNull null
                val count = prefs.getInt(PREFIX_COUNT + date, 1).coerceAtLeast(1)
                DailyTotal(date, millis, count)
            }.sortedByDescending { it.date }
        }
    }

    private fun migrateIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        val file = File(context.filesDir, LEGACY_FILE)
        val editor = prefs.edit()
        if (file.exists()) {
            runCatching {
                val json = JSONObject(file.readText().ifBlank { "{}" })
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val millis = json.optLong(key, 0L)
                    if (millis > 0) {
                        editor.putLong(PREFIX_MILLIS + key, millis)
                        if (!prefs.contains(PREFIX_COUNT + key)) {
                            editor.putInt(PREFIX_COUNT + key, 1)
                        }
                    }
                }
            }
        }
        editor.putBoolean(KEY_MIGRATED, true)
        editor.commit()
    }
}

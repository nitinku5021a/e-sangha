package com.meditationhall.services

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class ScheduleSpec(
    val type: String,
    val startLocalTime: String,
    val timezone: String,
    val daysOfWeek: String,
    val startDate: String?,
    val durationSeconds: Int
)

object ScheduleMath {
    fun nextStartMillis(spec: ScheduleSpec, now: Instant = Instant.now()): Long? {
        val zone = runCatching { ZoneId.of(spec.timezone) }.getOrDefault(ZoneId.systemDefault())
        val time = LocalTime.parse(spec.startLocalTime)
        val allowed = spec.daysOfWeek.split(',').mapNotNull { runCatching { DayOfWeek.valueOf(it.trim()) }.getOrNull() }.toSet()
        var cursor = ZonedDateTime.ofInstant(now, zone)
        repeat(21) { offset ->
            val day = cursor.toLocalDate()
            if (matches(spec.type, allowed, spec.startDate, day)) {
                val start = ZonedDateTime.of(day, time, zone)
                val end = start.plusSeconds(spec.durationSeconds.toLong())
                if (end.toInstant().isAfter(now)) return start.toInstant().toEpochMilli()
            }
            cursor = cursor.plusDays(1).with(LocalTime.MIDNIGHT)
        }
        return null
    }

    fun remainingSeconds(startMillis: Long, durationSeconds: Int, nowMillis: Long = System.currentTimeMillis()): Long? {
        val end = startMillis + durationSeconds * 1000L
        return if (nowMillis in startMillis until end) (end - nowMillis) / 1000L else null
    }

    private fun matches(type: String, days: Set<DayOfWeek>, startDate: String?, day: LocalDate): Boolean {
        if (startDate != null && day.isBefore(LocalDate.parse(startDate))) return false
        return when (type) {
            "ONCE" -> startDate == null || LocalDate.parse(startDate) == day
            "DAILY" -> true
            "WEEKDAYS" -> day.dayOfWeek.value in 1..5
            "WEEKLY", "CUSTOM" -> days.isEmpty() || day.dayOfWeek in days
            else -> true
        }
    }
}

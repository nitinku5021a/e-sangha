package com.meditationhall.scheduler

import com.meditationhall.db.Halls
import com.meditationhall.db.Notifications
import com.meditationhall.db.now
import com.meditationhall.db.uuid
import com.meditationhall.services.HallService
import com.meditationhall.services.ScheduleMath
import com.meditationhall.services.ScheduleSpec
import com.meditationhall.services.SessionService
import java.util.UUID
import kotlin.concurrent.thread
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

class SessionScheduler(
    private val sessions: SessionService,
    private val halls: HallService
) {
    private val log = LoggerFactory.getLogger(SessionScheduler::class.java)

    fun start() {
        thread(isDaemon = true, name = "session-scheduler") {
            while (true) {
                runCatching {
                    sessions.tickLifecycle()
                    generateUpcoming()
                    reminderPass()
                }.onFailure { log.warn("scheduler tick failed", it) }
                Thread.sleep(60_000)
            }
        }
    }

    private fun generateUpcoming() {
        transaction {
            Halls.selectAll().filter { it[Halls.status] == "ACTIVE" }.forEach { hall ->
                runCatching { sessions.ensureSession(hall[Halls.id]) }
            }
        }
    }

    private fun reminderPass() {
        val now = System.currentTimeMillis()
        transaction {
            Halls.selectAll().filter { it[Halls.status] == "ACTIVE" }.forEach { hall ->
                val schedule = halls.scheduleFor(hall[Halls.id]) ?: return@forEach
                val spec = ScheduleSpec(
                    type = schedule[com.meditationhall.db.HallSchedules.scheduleType],
                    startLocalTime = schedule[com.meditationhall.db.HallSchedules.startLocalTime],
                    timezone = schedule[com.meditationhall.db.HallSchedules.timezone],
                    daysOfWeek = schedule[com.meditationhall.db.HallSchedules.daysOfWeek],
                    startDate = schedule[com.meditationhall.db.HallSchedules.startDate],
                    durationSeconds = hall[Halls.durationSeconds]
                )
                val start = ScheduleMath.nextStartMillis(spec) ?: return@forEach
                val until = start - now
                if (until in 14 * 60_000..16 * 60_000) {
                    val key = "notification:${hall[Halls.id]}:15min:$start"
                    val exists = Notifications.selectAll().any { it[Notifications.idempotencyKey] == key }
                    if (!exists) {
                        Notifications.insert {
                            it[id] = uuid()
                            it[idempotencyKey] = key
                            it[userId] = UUID.fromString("00000000-0000-0000-0000-000000000001")
                            it[type] = "REMINDER_15"
                            it[sentAt] = now()
                        }
                        log.info("15-minute reminder recorded for hall {}", hall[Halls.name])
                    }
                }
            }
        }
    }
}

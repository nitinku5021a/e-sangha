package com.meditationhall.services

import com.meditationhall.ApiException
import com.meditationhall.db.Attendance
import com.meditationhall.db.Halls
import com.meditationhall.db.MeditationLogs
import com.meditationhall.db.MeditationSessions
import com.meditationhall.db.now
import com.meditationhall.db.uuid
import com.meditationhall.dto.LogDto
import com.meditationhall.dto.SessionDto
import com.meditationhall.dto.StatsDto
import com.meditationhall.dto.WsEvent
import com.meditationhall.realtime.RealtimeHub
import io.ktor.http.HttpStatusCode
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class SessionService(private val hub: RealtimeHub) {
    fun ensureSession(hallId: UUID): UUID = transaction {
        val hall = Halls.selectAll().first { it[Halls.id] == hallId }
        val schedule = com.meditationhall.db.HallSchedules.selectAll()
            .firstOrNull { it[com.meditationhall.db.HallSchedules.hallId] == hallId }
        val spec = ScheduleSpec(
            type = schedule?.get(com.meditationhall.db.HallSchedules.scheduleType) ?: "DAILY",
            startLocalTime = schedule?.get(com.meditationhall.db.HallSchedules.startLocalTime) ?: "06:00",
            timezone = schedule?.get(com.meditationhall.db.HallSchedules.timezone) ?: hall[Halls.timezone],
            daysOfWeek = schedule?.get(com.meditationhall.db.HallSchedules.daysOfWeek) ?: "",
            startDate = schedule?.get(com.meditationhall.db.HallSchedules.startDate),
            durationSeconds = hall[Halls.durationSeconds]
        )
        val startMillis = ScheduleMath.nextStartMillis(spec)
            ?: throw ApiException(HttpStatusCode.Conflict, "NO_UPCOMING_SESSION", "No upcoming session.")
        val start = Instant.ofEpochMilli(startMillis)
        val existing = MeditationSessions.selectAll().firstOrNull {
            it[MeditationSessions.hallId] == hallId && it[MeditationSessions.scheduledStart] == start
        }
        if (existing != null) return@transaction existing[MeditationSessions.id]
        val id = uuid()
        val remaining = ScheduleMath.remainingSeconds(startMillis, hall[Halls.durationSeconds])
        MeditationSessions.insert {
            it[MeditationSessions.id] = id
            it[MeditationSessions.hallId] = hallId
            it[scheduledStart] = start
            it[actualStart] = if (remaining != null) start else null
            it[durationSeconds] = hall[Halls.durationSeconds]
            it[status] = if (remaining != null) "ACTIVE" else "SCHEDULED"
            it[createdAt] = now()
        }
        id
    }

    fun get(sessionId: UUID): SessionDto = transaction { toDto(sessionId) }

    fun join(userId: UUID, sessionId: UUID): SessionDto {
        val dto = transaction {
            val session = MeditationSessions.selectAll().firstOrNull { it[MeditationSessions.id] == sessionId }
                ?: throw ApiException(HttpStatusCode.NotFound, "SESSION_NOT_FOUND", "Session not found.")
            val open = Attendance.selectAll().any {
                it[Attendance.sessionId] == sessionId && it[Attendance.userId] == userId && it[Attendance.leftAt] == null
            }
            if (!open) {
                Attendance.insert {
                    it[id] = uuid()
                    it[Attendance.sessionId] = sessionId
                    it[Attendance.userId] = userId
                    it[hallId] = session[MeditationSessions.hallId]
                    it[joinedAt] = now()
                    it[lastSeenAt] = now()
                    it[attendedDurationSeconds] = 0
                    it[completionStatus] = "UNKNOWN"
                }
            }
            toDto(sessionId)
        }
        hub.heartbeat(sessionId, userId)
        runBlocking {
            hub.broadcast(
                WsEvent(
                    type = "PARTICIPANT_JOINED",
                    sessionId = sessionId.toString(),
                    userId = userId.toString(),
                    participantCount = hub.count(sessionId),
                    serverTimeMillis = System.currentTimeMillis()
                )
            )
        }
        return dto.copy(participantCount = hub.count(sessionId).coerceAtLeast(dto.participantCount))
    }

    fun leave(userId: UUID, sessionId: UUID) {
        transaction {
            val row = Attendance.selectAll().firstOrNull {
                it[Attendance.sessionId] == sessionId && it[Attendance.userId] == userId && it[Attendance.leftAt] == null
            } ?: return@transaction
            val joined = row[Attendance.joinedAt]
            val seconds = java.time.Duration.between(joined, Instant.now()).seconds.toInt().coerceAtLeast(0)
            val session = MeditationSessions.selectAll().first { it[MeditationSessions.id] == sessionId }
            val expected = session[MeditationSessions.durationSeconds]
            val status = if (seconds >= (expected * 0.9).toInt()) "COMPLETED" else "PARTIAL"
            Attendance.update({ Attendance.id eq row[Attendance.id] }) {
                it[leftAt] = now()
                it[lastSeenAt] = now()
                it[attendedDurationSeconds] = seconds
                it[completionStatus] = status
            }
            val hall = Halls.selectAll().first { it[Halls.id] == session[MeditationSessions.hallId] }
            MeditationLogs.insert {
                it[id] = uuid()
                it[MeditationLogs.userId] = userId
                it[MeditationLogs.sessionId] = sessionId
                it[hallId] = hall[Halls.id]
                it[hallName] = hall[Halls.name]
                it[date] = Instant.now().atZone(ZoneOffset.UTC).toLocalDate().toString()
                it[durationSeconds] = seconds
                it[completionStatus] = status
                it[createdAt] = now()
            }
        }
        hub.leave(sessionId, userId)
        runBlocking {
            hub.broadcast(
                WsEvent(
                    type = "PARTICIPANT_LEFT",
                    sessionId = sessionId.toString(),
                    userId = userId.toString(),
                    participantCount = hub.count(sessionId),
                    serverTimeMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun logs(userId: UUID): List<LogDto> = transaction {
        MeditationLogs.selectAll().filter { it[MeditationLogs.userId] == userId }
            .sortedByDescending { it[MeditationLogs.createdAt] }
            .map {
                LogDto(
                    id = it[MeditationLogs.id].toString(),
                    hallId = it[MeditationLogs.hallId].toString(),
                    hallName = it[MeditationLogs.hallName],
                    date = it[MeditationLogs.date],
                    durationSeconds = it[MeditationLogs.durationSeconds],
                    completionStatus = it[MeditationLogs.completionStatus]
                )
            }
    }

    fun stats(userId: UUID): StatsDto = transaction {
        val mine = MeditationLogs.selectAll().filter { it[MeditationLogs.userId] == userId }
        StatsDto(
            sessionsExpected = mine.size,
            sessionsAttended = mine.size,
            sessionsCompleted = mine.count { it[MeditationLogs.completionStatus] == "COMPLETED" },
            totalMeditationSeconds = mine.sumOf { it[MeditationLogs.durationSeconds].toLong() }
        )
    }

    fun tickLifecycle() {
        transaction {
            val now = Instant.now()
            MeditationSessions.selectAll().forEach { row ->
                val start = row[MeditationSessions.scheduledStart]
                val dur = row[MeditationSessions.durationSeconds]
                val end = start.plusSeconds(dur.toLong())
                val id = row[MeditationSessions.id]
                when {
                    row[MeditationSessions.status] == "SCHEDULED" && !now.isBefore(start) && now.isBefore(end) -> {
                        MeditationSessions.update({ MeditationSessions.id eq id }) {
                            it[status] = "ACTIVE"
                            it[actualStart] = start
                        }
                        runBlocking {
                            hub.broadcast(
                                WsEvent(
                                    type = "SESSION_STARTED",
                                    sessionId = id.toString(),
                                    hallId = row[MeditationSessions.hallId].toString(),
                                    status = "ACTIVE",
                                    serverTimeMillis = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    row[MeditationSessions.status] in listOf("SCHEDULED", "STARTING", "ACTIVE") && !now.isBefore(end) -> {
                        MeditationSessions.update({ MeditationSessions.id eq id }) { it[status] = "COMPLETED" }
                        runBlocking {
                            hub.broadcast(
                                WsEvent(
                                    type = "SESSION_COMPLETED",
                                    sessionId = id.toString(),
                                    hallId = row[MeditationSessions.hallId].toString(),
                                    status = "COMPLETED",
                                    serverTimeMillis = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun toDto(sessionId: UUID): SessionDto {
        val row = MeditationSessions.selectAll().first { it[MeditationSessions.id] == sessionId }
        val start = (row[MeditationSessions.actualStart] ?: row[MeditationSessions.scheduledStart]).toEpochMilli()
        val remaining = ScheduleMath.remainingSeconds(start, row[MeditationSessions.durationSeconds]) ?: 0
        val count = Attendance.selectAll().count {
            it[Attendance.sessionId] == sessionId && it[Attendance.leftAt] == null
        }.toInt()
        return SessionDto(
            id = sessionId.toString(),
            hallId = row[MeditationSessions.hallId].toString(),
            scheduledStartMillis = row[MeditationSessions.scheduledStart].toEpochMilli(),
            actualStartMillis = row[MeditationSessions.actualStart]?.toEpochMilli(),
            durationSeconds = row[MeditationSessions.durationSeconds],
            status = row[MeditationSessions.status],
            participantCount = count,
            remainingSeconds = remaining
        )
    }
}

package com.meditationhall.services

import com.meditationhall.ApiException
import com.meditationhall.db.Attendance
import com.meditationhall.db.HallMemberships
import com.meditationhall.db.Halls
import com.meditationhall.db.MeditationLogs
import com.meditationhall.db.MeditationSessions
import com.meditationhall.db.now
import com.meditationhall.db.uuid
import com.meditationhall.db.Users
import com.meditationhall.dto.HallStatsDto
import com.meditationhall.dto.LogDto
import com.meditationhall.dto.ParticipantDto
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
    fun ensureSession(userId: UUID, hallId: UUID): UUID = transaction {
        assertMember(userId, hallId)
        openUpcoming(hallId)
    }

    /** Scheduler path. Creates the next sitting without a member request. */
    fun ensureUpcoming(hallId: UUID): UUID = transaction { openUpcoming(hallId) }

    private fun openUpcoming(hallId: UUID): UUID {
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
        if (existing != null) return existing[MeditationSessions.id]
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

    fun get(userId: UUID, sessionId: UUID): SessionDto = transaction {
        assertCanObserve(userId, sessionId)
        toDto(sessionId)
    }

    /** Hall creator and the instant the scheduled sitting ends. */
    fun voiceGate(sessionId: UUID): Pair<UUID, Long> = transaction {
        val session = MeditationSessions.selectAll().firstOrNull { it[MeditationSessions.id] == sessionId }
            ?: throw ApiException(HttpStatusCode.NotFound, "SESSION_NOT_FOUND", "Session not found.")
        val hall = Halls.selectAll().first { it[Halls.id] == session[MeditationSessions.hallId] }
        val startMillis = session[MeditationSessions.scheduledStart].toEpochMilli()
        if (System.currentTimeMillis() < startMillis) {
            throw ApiException(HttpStatusCode.Conflict, "VOICE_NOT_STARTED", "The sitting has not started.")
        }
        val endMillis = startMillis + session[MeditationSessions.durationSeconds] * 1000L
        hall[Halls.creatorId] to endMillis
    }

    fun participants(userId: UUID, sessionId: UUID): List<ParticipantDto> = transaction {
        assertCanObserve(userId, sessionId)
        MeditationSessions.selectAll().firstOrNull { it[MeditationSessions.id] == sessionId }
            ?: throw ApiException(HttpStatusCode.NotFound, "SESSION_NOT_FOUND", "Session not found.")
        Attendance.selectAll()
            .filter { it[Attendance.sessionId] == sessionId && it[Attendance.leftAt] == null }
            .map { row ->
                val user = Users.selectAll().firstOrNull { it[Users.id] == row[Attendance.userId] }
                ParticipantDto(
                    userId = row[Attendance.userId].toString(),
                    displayName = user?.get(Users.displayName) ?: "Practitioner",
                    displayMode = "AVATAR"
                )
            }
    }

    fun join(userId: UUID, sessionId: UUID): SessionDto {
        val dto = transaction {
            val session = MeditationSessions.selectAll().firstOrNull { it[MeditationSessions.id] == sessionId }
                ?: throw ApiException(HttpStatusCode.NotFound, "SESSION_NOT_FOUND", "Session not found.")
            assertMember(userId, session[MeditationSessions.hallId])
            val start = session[MeditationSessions.scheduledStart]
            val end = start.plusSeconds(session[MeditationSessions.durationSeconds].toLong())
            val now = Instant.now()
            val arriveFrom = start.minusSeconds(15 * 60)
            if (now.isBefore(arriveFrom) || now.isAfter(end)) {
                throw ApiException(
                    HttpStatusCode.Conflict,
                    "NOT_IN_ARRIVAL_WINDOW",
                    "You can enter this sitting from 15 minutes before it starts until it ends."
                )
            }
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
            fanout(
                WsEvent(
                    type = "PARTICIPANT_JOINED",
                    sessionId = sessionId.toString(),
                    hallId = dto.hallId,
                    userId = userId.toString(),
                    participantCount = hub.count(sessionId),
                    serverTimeMillis = System.currentTimeMillis()
                )
            )
        }
        return dto.copy(participantCount = hub.count(sessionId).coerceAtLeast(dto.participantCount))
    }

    fun leave(userId: UUID, sessionId: UUID, attendedSeconds: Int? = null, clockFinished: Boolean = false) {
        transaction {
            val row = Attendance.selectAll().firstOrNull {
                it[Attendance.sessionId] == sessionId && it[Attendance.userId] == userId && it[Attendance.leftAt] == null
            } ?: return@transaction
            val joined = row[Attendance.joinedAt]
            val session = MeditationSessions.selectAll().first { it[MeditationSessions.id] == sessionId }
            val expected = session[MeditationSessions.durationSeconds]
            val start = session[MeditationSessions.scheduledStart]
            val end = start.plusSeconds(expected.toLong())
            val nowInstant = Instant.now()
            val windowStart = if (joined.isBefore(start)) start else joined
            val windowEnd = if (nowInstant.isAfter(end)) end else nowInstant
            val overlap = if (windowEnd.isAfter(windowStart)) {
                java.time.Duration.between(windowStart, windowEnd).seconds.toInt()
            } else {
                0
            }
            val seconds = (attendedSeconds ?: overlap).coerceIn(0, expected)
            // Leaving the hall records this person only. The scheduled session stays open for the host.
            val status = if (clockFinished) "COMPLETED" else "PARTIAL"
            Attendance.update({ Attendance.id eq row[Attendance.id] }) {
                it[leftAt] = now()
                it[lastSeenAt] = now()
                it[attendedDurationSeconds] = seconds
                it[completionStatus] = status
            }
            if (seconds <= 0) return@transaction
            val hall = Halls.selectAll().first { it[Halls.id] == session[MeditationSessions.hallId] }
            MeditationLogs.insert {
                it[id] = uuid()
                it[MeditationLogs.userId] = userId
                it[MeditationLogs.sessionId] = sessionId
                it[hallId] = hall[Halls.id]
                it[hallName] = hall[Halls.name]
                val userZone = Users.selectAll().firstOrNull { it[Users.id] == userId }?.get(Users.timezone)
                val zone = runCatching { java.time.ZoneId.of(userZone ?: "UTC") }.getOrDefault(java.time.ZoneOffset.UTC)
                it[date] = Instant.now().atZone(zone).toLocalDate().toString()
                it[durationSeconds] = seconds
                it[completionStatus] = status
                it[createdAt] = now()
            }
        }
        hub.leave(sessionId, userId)
        runBlocking {
            fanout(
                WsEvent(
                    type = "PARTICIPANT_LEFT",
                    sessionId = sessionId.toString(),
                    hallId = rowHallId(sessionId),
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
                    completionStatus = it[MeditationLogs.completionStatus],
                    sessionId = it[MeditationLogs.sessionId].toString()
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

    fun hallStats(userId: UUID, hallId: UUID): HallStatsDto = transaction {
        assertMember(userId, hallId)
        val logs = MeditationLogs.selectAll().filter { it[MeditationLogs.hallId] == hallId }
        val att = Attendance.selectAll().filter { it[Attendance.hallId] == hallId }
        val sessionIds = (logs.map { it[MeditationLogs.sessionId] } + att.map { it[Attendance.sessionId] }).distinct()
        val people = (logs.map { it[MeditationLogs.userId] } + att.map { it[Attendance.userId] }).distinct()
        HallStatsDto(
            sessionCount = sessionIds.size,
            totalAttendance = att.size.coerceAtLeast(logs.size),
            uniqueParticipants = people.size,
            totalMeditationSeconds = logs.sumOf { it[MeditationLogs.durationSeconds].toLong() }
        )
    }

    fun tickLifecycle() {
        val ended = mutableListOf<Pair<UUID, UUID>>()
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
                            fanout(
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
                    !now.isBefore(end) -> {
                        if (row[MeditationSessions.status] in listOf("SCHEDULED", "STARTING", "ACTIVE")) {
                            MeditationSessions.update({ MeditationSessions.id eq id }) { it[status] = "COMPLETED" }
                            runBlocking {
                                fanout(
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
                        Attendance.selectAll()
                            .filter { it[Attendance.sessionId] == id && it[Attendance.leftAt] == null }
                            .forEach { ended += it[Attendance.userId] to id }
                    }
                }
            }
        }
        ended.forEach { (userId, sessionId) -> leave(userId, sessionId, null) }
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

    /** Voice, participant names, and session details are for members and people in the sitting. */
    fun requireObserver(userId: UUID, sessionId: UUID) {
        transaction { assertCanObserve(userId, sessionId) }
    }

    private fun assertMember(userId: UUID, hallId: UUID) {
        val hall = Halls.selectAll().firstOrNull { it[Halls.id] == hallId }
            ?: throw ApiException(HttpStatusCode.NotFound, "HALL_NOT_FOUND", "Hall not found.")
        if (hall[Halls.creatorId] == userId) return
        val member = HallMemberships.selectAll().any {
            it[HallMemberships.hallId] == hallId && it[HallMemberships.userId] == userId
        }
        if (!member) {
            throw ApiException(HttpStatusCode.Forbidden, "NOT_MEMBER", "Join this hall before entering the sitting.")
        }
    }

    private fun assertCanObserve(userId: UUID, sessionId: UUID) {
        val session = MeditationSessions.selectAll().firstOrNull { it[MeditationSessions.id] == sessionId }
            ?: throw ApiException(HttpStatusCode.NotFound, "SESSION_NOT_FOUND", "Session not found.")
        val hallId = session[MeditationSessions.hallId]
        val hall = Halls.selectAll().firstOrNull { it[Halls.id] == hallId } ?: return
        if (hall[Halls.creatorId] == userId) return
        val member = HallMemberships.selectAll().any {
            it[HallMemberships.hallId] == hallId && it[HallMemberships.userId] == userId
        }
        if (member) return
        val attended = Attendance.selectAll().any {
            it[Attendance.sessionId] == sessionId && it[Attendance.userId] == userId
        }
        if (!attended) {
            throw ApiException(HttpStatusCode.Forbidden, "NOT_MEMBER", "Join this hall before entering the sitting.")
        }
    }

    private fun rowHallId(sessionId: UUID): String = transaction {
        MeditationSessions.selectAll().firstOrNull { it[MeditationSessions.id] == sessionId }
            ?.get(MeditationSessions.hallId)?.toString().orEmpty()
    }

    private fun fanout(event: WsEvent) {
        val hallId = event.hallId?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return
        val sessionId = event.sessionId?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return
        val recipients = transaction { audience(hallId, sessionId) }
        runBlocking { hub.broadcast(event, recipients) }
    }

    private fun audience(hallId: UUID, sessionId: UUID): Set<UUID> {
        val members = HallMemberships.selectAll()
            .filter { it[HallMemberships.hallId] == hallId }
            .map { it[HallMemberships.userId] }
        val present = Attendance.selectAll()
            .filter { it[Attendance.sessionId] == sessionId && it[Attendance.leftAt] == null }
            .map { it[Attendance.userId] }
        val creator = Halls.selectAll().firstOrNull { it[Halls.id] == hallId }?.get(Halls.creatorId)
        return (members + present + listOfNotNull(creator)).toSet()
    }
}

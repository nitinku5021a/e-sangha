package com.meditationhall.services

import com.meditationhall.ApiException
import com.meditationhall.db.HallMemberships
import com.meditationhall.db.HallSchedules
import com.meditationhall.db.Halls
import com.meditationhall.db.now
import com.meditationhall.db.uuid
import com.meditationhall.dto.CreateHallRequest
import com.meditationhall.dto.HallDto
import com.meditationhall.dto.HallsResponse
import io.ktor.http.HttpStatusCode
import java.util.UUID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class HallService {
    fun list(userId: UUID): HallsResponse = transaction {
        val memberships = HallMemberships.selectAll()
            .filter { it[HallMemberships.userId] == userId }
            .map { it[HallMemberships.hallId] }
            .toSet()
        val cards = Halls.selectAll().mapNotNull { row ->
            if (row[Halls.status] != "ACTIVE") return@mapNotNull null
            val vis = row[Halls.visibility]
            val id = row[Halls.id]
            val joined = id in memberships
            if (vis == "PRIVATE" && !joined) return@mapNotNull null
            val schedule = HallSchedules.selectAll().firstOrNull { it[HallSchedules.hallId] == id }
            val spec = ScheduleSpec(
                type = schedule?.get(HallSchedules.scheduleType) ?: "DAILY",
                startLocalTime = schedule?.get(HallSchedules.startLocalTime) ?: "06:00",
                timezone = schedule?.get(HallSchedules.timezone) ?: row[Halls.timezone],
                daysOfWeek = schedule?.get(HallSchedules.daysOfWeek) ?: "",
                startDate = schedule?.get(HallSchedules.startDate),
                durationSeconds = row[Halls.durationSeconds]
            )
            val next = ScheduleMath.nextStartMillis(spec)
            val remaining = next?.let { ScheduleMath.remainingSeconds(it, row[Halls.durationSeconds]) }
            HallDto(
                id = id.toString(),
                creatorId = row[Halls.creatorId].toString(),
                name = row[Halls.name],
                description = row[Halls.description],
                visibility = vis,
                durationSeconds = row[Halls.durationSeconds],
                timezone = row[Halls.timezone],
                status = row[Halls.status],
                shareCode = row[Halls.shareCode],
                audioType = row[Halls.audioType],
                nextStartMillis = next,
                remainingSeconds = remaining,
                participantCount = if (remaining != null) 8 else 3,
                joined = joined,
                startLocalTime = spec.startLocalTime,
                scheduleType = spec.type
            )
        }
        HallsResponse(
            sittingNow = cards.filter { it.remainingSeconds != null }.sortedBy { it.remainingSeconds },
            startingSoon = cards.filter { it.remainingSeconds == null }.sortedBy { it.nextStartMillis ?: Long.MAX_VALUE }.take(12),
            myHalls = cards.filter { it.joined }.sortedBy { it.nextStartMillis ?: Long.MAX_VALUE }
        )
    }

    fun get(userId: UUID, hallId: UUID): HallDto {
        val list = list(userId)
        return (list.sittingNow + list.startingSoon + list.myHalls).firstOrNull { it.id == hallId.toString() }
            ?: throw ApiException(HttpStatusCode.NotFound, "HALL_NOT_FOUND", "Hall not found.")
    }

    fun create(userId: UUID, body: CreateHallRequest): HallDto = transaction {
        val hallId = uuid()
        val code = body.name.filter { it.isLetterOrDigit() }.take(4).lowercase().ifBlank { "hall" } +
            (1000..9999).random()
        Halls.insert {
            it[id] = hallId
            it[creatorId] = userId
            it[name] = body.name.trim().ifBlank { "Untitled hall" }
            it[description] = body.description
            it[visibility] = body.visibility
            it[durationSeconds] = body.durationMinutes.coerceIn(1, 240) * 60
            it[timezone] = body.timezone
            it[status] = "ACTIVE"
            it[shareCode] = code.take(12)
            it[audioType] = body.audioType
            it[createdAt] = now()
            it[updatedAt] = now()
        }
        HallSchedules.insert {
            it[id] = uuid()
            it[HallSchedules.hallId] = hallId
            it[scheduleType] = body.scheduleType
            it[startLocalTime] = "%02d:%02d".format(body.hour.coerceIn(0, 23), body.minute.coerceIn(0, 59))
            it[timezone] = body.timezone
            it[daysOfWeek] = body.daysOfWeek.joinToString(",")
            it[enabled] = true
        }
        HallMemberships.insert {
            it[HallMemberships.hallId] = hallId
            it[HallMemberships.userId] = userId
            it[joinedAt] = now()
            it[notificationEnabled] = true
            it[displayMode] = "AVATAR"
        }
        hallId
    }.let { get(userId, it) }

    fun join(userId: UUID, hallId: UUID) = transaction {
        val exists = Halls.selectAll().any { it[Halls.id] == hallId }
        if (!exists) throw ApiException(HttpStatusCode.NotFound, "HALL_NOT_FOUND", "Hall not found.")
        val already = HallMemberships.selectAll().any {
            it[HallMemberships.hallId] == hallId && it[HallMemberships.userId] == userId
        }
        if (!already) {
            HallMemberships.insert {
                it[HallMemberships.hallId] = hallId
                it[HallMemberships.userId] = userId
                it[joinedAt] = now()
                it[notificationEnabled] = true
                it[displayMode] = "AVATAR"
            }
        }
    }

    fun leave(userId: UUID, hallId: UUID) = transaction {
        HallMemberships.deleteWhere {
            (HallMemberships.hallId eq hallId) and (HallMemberships.userId eq userId)
        }
    }

    fun findByShareCode(userId: UUID, code: String): HallDto {
        val row = transaction {
            Halls.selectAll().firstOrNull { it[Halls.shareCode].equals(code.trim(), ignoreCase = true) }
        } ?: throw ApiException(HttpStatusCode.NotFound, "HALL_NOT_FOUND", "Unknown share code.")
        return get(userId, row[Halls.id])
    }

    fun hallRow(hallId: UUID) = transaction {
        Halls.selectAll().firstOrNull { it[Halls.id] == hallId }
            ?: throw ApiException(HttpStatusCode.NotFound, "HALL_NOT_FOUND", "Hall not found.")
    }

    fun scheduleFor(hallId: UUID) = transaction {
        HallSchedules.selectAll().firstOrNull { it[HallSchedules.hallId] == hallId }
    }
}

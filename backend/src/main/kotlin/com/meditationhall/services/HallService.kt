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
import com.meditationhall.dto.UpdateHallRequest
import io.ktor.http.HttpStatusCode
import java.util.UUID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

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
            val members = HallMemberships.selectAll().count { it[HallMemberships.hallId] == id }
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
                audioUrl = row[Halls.audioUrl],
                audioFileName = row[Halls.audioFileName],
                audioDurationSeconds = row[Halls.audioDurationSeconds],
                nextStartMillis = next,
                remainingSeconds = remaining,
                participantCount = if (remaining != null) members else members,
                joined = joined,
                startLocalTime = spec.startLocalTime,
                scheduleType = spec.type,
                memberCount = members
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
            it[durationSeconds] = body.durationMinutes.coerceIn(1, 720) * 60
            it[timezone] = body.timezone
            it[status] = "ACTIVE"
            it[shareCode] = code.take(12)
            it[audioType] = body.audioType
            it[audioUrl] = body.audioUrl?.trim()?.takeIf { it.isNotBlank() }
            it[audioFileName] = body.audioFileName?.trim()?.takeIf { it.isNotBlank() }
            it[audioDurationSeconds] = body.audioDurationSeconds?.takeIf { it > 0 }
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

    fun update(userId: UUID, hallId: UUID, body: UpdateHallRequest): HallDto {
        transaction {
            val row = Halls.selectAll().firstOrNull { it[Halls.id] == hallId }
                ?: throw ApiException(HttpStatusCode.NotFound, "HALL_NOT_FOUND", "Hall not found.")
            if (row[Halls.creatorId] != userId) {
                throw ApiException(HttpStatusCode.Forbidden, "NOT_CREATOR", "Only the hall creator can edit this hall.")
            }
            if (row[Halls.status] != "ACTIVE") {
                throw ApiException(HttpStatusCode.Conflict, "HALL_INACTIVE", "This hall cannot be edited.")
            }
            val visibility = body.visibility?.uppercase()
            if (visibility != null && visibility !in setOf("PUBLIC", "PRIVATE", "UNLISTED")) {
                throw ApiException(HttpStatusCode.BadRequest, "INVALID_VISIBILITY", "Visibility must be PUBLIC, PRIVATE, or UNLISTED.")
            }
            val audioType = body.audioType?.uppercase()
            if (audioType != null && audioType !in setOf("NONE", "FILE", "BELL")) {
                throw ApiException(HttpStatusCode.BadRequest, "INVALID_AUDIO", "Audio must be NONE, FILE, or BELL.")
            }
            val scheduleType = body.scheduleType?.uppercase()
            if (scheduleType != null && scheduleType !in setOf("ONCE", "DAILY", "WEEKLY", "WEEKDAYS", "CUSTOM")) {
                throw ApiException(HttpStatusCode.BadRequest, "INVALID_SCHEDULE", "Unknown schedule type.")
            }
            Halls.update({ Halls.id eq hallId }) {
                body.name?.let { n -> it[name] = n.trim().ifBlank { "Untitled hall" } }
                body.description?.let { d -> it[description] = d }
                body.durationMinutes?.let { m -> it[durationSeconds] = m.coerceIn(1, 720) * 60 }
                visibility?.let { v -> it[Halls.visibility] = v }
                audioType?.let { a -> it[Halls.audioType] = a }
                if (audioType == "FILE") {
                    body.audioUrl?.let { u -> it[Halls.audioUrl] = u.trim().takeIf { s -> s.isNotBlank() } }
                    body.audioFileName?.let { n -> it[Halls.audioFileName] = n.trim().takeIf { s -> s.isNotBlank() } }
                    body.audioDurationSeconds?.let { d -> it[Halls.audioDurationSeconds] = d.takeIf { s -> s > 0 } }
                } else if (audioType != null) {
                    it[Halls.audioUrl] = null
                    it[Halls.audioFileName] = null
                    it[Halls.audioDurationSeconds] = null
                }
                body.timezone?.let { tz -> it[timezone] = tz }
                it[updatedAt] = now()
            }
            val schedule = HallSchedules.selectAll().firstOrNull { it[HallSchedules.hallId] == hallId }
            val existingTime = schedule?.get(HallSchedules.startLocalTime) ?: "06:00"
            val parts = existingTime.split(":")
            val hour = body.hour ?: parts.getOrNull(0)?.toIntOrNull() ?: 6
            val minute = body.minute ?: parts.getOrNull(1)?.toIntOrNull() ?: 0
            val time = "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            if (schedule == null) {
                HallSchedules.insert {
                    it[id] = uuid()
                    it[HallSchedules.hallId] = hallId
                    it[HallSchedules.scheduleType] = scheduleType ?: "DAILY"
                    it[startLocalTime] = time
                    it[timezone] = body.timezone ?: row[Halls.timezone]
                    it[daysOfWeek] = body.daysOfWeek?.joinToString(",") ?: ""
                    it[enabled] = true
                }
            } else {
                HallSchedules.update({ HallSchedules.id eq schedule[HallSchedules.id] }) {
                    scheduleType?.let { t -> it[HallSchedules.scheduleType] = t }
                    it[startLocalTime] = time
                    body.timezone?.let { tz -> it[timezone] = tz }
                    body.daysOfWeek?.let { days -> it[daysOfWeek] = days.joinToString(",") }
                }
            }
        }
        return get(userId, hallId)
    }

    fun delete(userId: UUID, hallId: UUID) {
        transaction {
            val row = Halls.selectAll().firstOrNull { it[Halls.id] == hallId }
                ?: throw ApiException(HttpStatusCode.NotFound, "HALL_NOT_FOUND", "Hall not found.")
            if (row[Halls.creatorId] != userId) {
                throw ApiException(HttpStatusCode.Forbidden, "NOT_CREATOR", "Only the hall creator can delete this hall.")
            }
            val others = HallMemberships.selectAll().any {
                it[HallMemberships.hallId] == hallId && it[HallMemberships.userId] != userId
            }
            if (others) {
                throw ApiException(
                    HttpStatusCode.Conflict,
                    "HALL_HAS_MEMBERS",
                    "This hall still has other members. They must leave before you can delete it."
                )
            }
            // Keep attendance, session rows, and personal meditation logs.
            HallMemberships.deleteWhere { HallMemberships.hallId eq hallId }
            HallSchedules.update({ HallSchedules.hallId eq hallId }) {
                it[enabled] = false
            }
            Halls.update({ Halls.id eq hallId }) {
                it[status] = "ARCHIVED"
                it[updatedAt] = now()
            }
        }
    }

    fun join(userId: UUID, hallId: UUID) = transaction {
        val hall = Halls.selectAll().firstOrNull { it[Halls.id] == hallId }
            ?: throw ApiException(HttpStatusCode.NotFound, "HALL_NOT_FOUND", "Hall not found.")
        if (hall[Halls.status] != "ACTIVE") {
            throw ApiException(HttpStatusCode.NotFound, "HALL_NOT_FOUND", "Hall not found.")
        }
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

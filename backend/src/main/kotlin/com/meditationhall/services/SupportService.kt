package com.meditationhall.services

import com.meditationhall.ApiException
import com.meditationhall.db.SupportMatches
import com.meditationhall.db.SupportRelationships
import com.meditationhall.db.SupportRequests
import com.meditationhall.db.SupporterProfiles
import com.meditationhall.db.now
import com.meditationhall.db.uuid
import com.meditationhall.dto.CreateHallRequest
import com.meditationhall.dto.RelationshipDto
import com.meditationhall.dto.SupportRequestBody
import com.meditationhall.dto.SupportRequestDto
import com.meditationhall.dto.SupporterProfileBody
import io.ktor.http.HttpStatusCode
import java.util.UUID
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class SupportService {
    fun upsertProfile(userId: UUID, body: SupporterProfileBody) = transaction {
        val existing = SupporterProfiles.selectAll().firstOrNull { it[SupporterProfiles.userId] == userId }
        if (existing == null) {
            SupporterProfiles.insert {
                it[SupporterProfiles.userId] = userId
                it[enabled] = body.enabled
                it[preferredDurationMinutes] = body.preferredDurationMinutes
                it[maxActiveRelationships] = body.maxActiveRelationships
                it[language] = body.language
                it[updatedAt] = now()
            }
        } else {
            SupporterProfiles.update({ SupporterProfiles.userId eq userId }) {
                it[enabled] = body.enabled
                it[preferredDurationMinutes] = body.preferredDurationMinutes
                it[maxActiveRelationships] = body.maxActiveRelationships
                it[language] = body.language
                it[updatedAt] = now()
            }
        }
    }

    fun profile(userId: UUID): SupporterProfileBody = transaction {
        val row = SupporterProfiles.selectAll().firstOrNull { it[SupporterProfiles.userId] == userId }
        if (row == null) SupporterProfileBody(enabled = false)
        else SupporterProfileBody(
            enabled = row[SupporterProfiles.enabled],
            preferredDurationMinutes = row[SupporterProfiles.preferredDurationMinutes],
            maxActiveRelationships = row[SupporterProfiles.maxActiveRelationships],
            language = row[SupporterProfiles.language]
        )
    }

    fun request(userId: UUID, body: SupportRequestBody): SupportRequestDto = transaction {
        val supporter = SupporterProfiles.selectAll().firstOrNull {
            it[SupporterProfiles.enabled] && it[SupporterProfiles.userId] != userId &&
                it[SupporterProfiles.language] == body.language
        } ?: SupporterProfiles.selectAll().firstOrNull { it[SupporterProfiles.enabled] }
        val id = uuid()
        val matched = supporter?.get(SupporterProfiles.userId)
        SupportRequests.insert {
            it[SupportRequests.id] = id
            it[requesterId] = userId
            it[status] = if (matched != null) "MATCHED" else "OPEN"
            it[preferredDurationMinutes] = body.preferredDurationMinutes
            it[language] = body.language
            it[sameHallOnly] = body.sameHallOnly
            it[matchedSupporterId] = matched
            it[createdAt] = now()
        }
        if (matched != null) {
            val matchId = uuid()
            SupportMatches.insert {
                it[SupportMatches.id] = matchId
                it[requestId] = id
                it[supporterId] = matched
                it[status] = "PENDING"
                it[createdAt] = now()
            }
        }
        SupportRequestDto(id.toString(), if (matched != null) "MATCHED" else "OPEN", body.preferredDurationMinutes, matched?.toString())
    }

    fun listRequests(userId: UUID): List<SupportRequestDto> = transaction {
        SupportRequests.selectAll().filter { it[SupportRequests.requesterId] == userId }.map {
            SupportRequestDto(
                it[SupportRequests.id].toString(),
                it[SupportRequests.status],
                it[SupportRequests.preferredDurationMinutes],
                it[SupportRequests.matchedSupporterId]?.toString()
            )
        }
    }

    fun cancel(userId: UUID, requestId: UUID) = transaction {
        val row = SupportRequests.selectAll().firstOrNull { it[SupportRequests.id] == requestId }
            ?: throw ApiException(HttpStatusCode.NotFound, "REQUEST_NOT_FOUND", "Support request not found.")
        if (row[SupportRequests.requesterId] != userId) {
            throw ApiException(HttpStatusCode.Forbidden, "FORBIDDEN", "Not your request.")
        }
        SupportRequests.update({ SupportRequests.id eq requestId }) { it[status] = "CANCELLED" }
    }

    fun matchesFor(userId: UUID) = transaction {
        SupportMatches.selectAll().filter { it[SupportMatches.supporterId] == userId }.map {
            mapOf(
                "id" to it[SupportMatches.id].toString(),
                "requestId" to it[SupportMatches.requestId].toString(),
                "status" to it[SupportMatches.status]
            )
        }
    }

    fun acceptMatch(userId: UUID, matchId: UUID) = transaction {
        val match = SupportMatches.selectAll().firstOrNull { it[SupportMatches.id] == matchId }
            ?: throw ApiException(HttpStatusCode.NotFound, "MATCH_NOT_FOUND", "Match not found.")
        if (match[SupportMatches.supporterId] != userId) {
            throw ApiException(HttpStatusCode.Forbidden, "FORBIDDEN", "Not your match.")
        }
        SupportMatches.update({ SupportMatches.id eq matchId }) { it[status] = "ACCEPTED" }
        val req = SupportRequests.selectAll().first { it[SupportRequests.id] == match[SupportMatches.requestId] }
        SupportRequests.update({ SupportRequests.id eq req[SupportRequests.id] }) { it[status] = "ACCEPTED" }
        SupportRelationships.insert {
            it[id] = uuid()
            it[supporterId] = userId
            it[supportedUserId] = req[SupportRequests.requesterId]
            it[createdAt] = now()
            it[sessionCount] = 0
        }
    }

    fun declineMatch(userId: UUID, matchId: UUID) = transaction {
        val match = SupportMatches.selectAll().firstOrNull { it[SupportMatches.id] == matchId }
            ?: throw ApiException(HttpStatusCode.NotFound, "MATCH_NOT_FOUND", "Match not found.")
        if (match[SupportMatches.supporterId] != userId) {
            throw ApiException(HttpStatusCode.Forbidden, "FORBIDDEN", "Not your match.")
        }
        SupportMatches.update({ SupportMatches.id eq matchId }) { it[status] = "DECLINED" }
    }

    fun relationships(userId: UUID): List<RelationshipDto> = transaction {
        SupportRelationships.selectAll().filter {
            it[SupportRelationships.supporterId] == userId || it[SupportRelationships.supportedUserId] == userId
        }.map {
            RelationshipDto(
                it[SupportRelationships.id].toString(),
                it[SupportRelationships.supporterId].toString(),
                it[SupportRelationships.supportedUserId].toString(),
                it[SupportRelationships.sessionCount]
            )
        }
    }

    fun createSupportSession(userId: UUID, relationshipId: UUID, halls: HallService): String {
        val rel = transaction {
            SupportRelationships.selectAll().firstOrNull { it[SupportRelationships.id] == relationshipId }
                ?: throw ApiException(HttpStatusCode.NotFound, "RELATIONSHIP_NOT_FOUND", "Relationship not found.")
        }
        if (rel[SupportRelationships.supporterId] != userId && rel[SupportRelationships.supportedUserId] != userId) {
            throw ApiException(HttpStatusCode.Forbidden, "FORBIDDEN", "Not your relationship.")
        }
        val hall = halls.create(
            userId,
            CreateHallRequest(
                name = "Private support sit",
                description = "Silent sitting for two. No conversation during meditation.",
                durationMinutes = 60,
                visibility = "PRIVATE",
                scheduleType = "ONCE"
            )
        )
        val other = if (rel[SupportRelationships.supporterId] == userId) rel[SupportRelationships.supportedUserId]
        else rel[SupportRelationships.supporterId]
        halls.join(other, UUID.fromString(hall.id))
        return hall.id
    }
}

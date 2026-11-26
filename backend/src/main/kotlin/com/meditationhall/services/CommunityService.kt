package com.meditationhall.services

import com.meditationhall.db.CommunityPresence
import com.meditationhall.db.MeditationLogs
import com.meditationhall.db.Users
import com.meditationhall.db.now
import com.meditationhall.dto.CommunityPerson
import com.meditationhall.dto.CommunityPresenceResponse
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class CommunityService {
    fun expire() {
        transaction { expireStale() }
    }

    fun present(): CommunityPresenceResponse = transaction {
        expireStale()
        val people = CommunityPresence.selectAll().map {
            CommunityPerson(
                it[CommunityPresence.userId].toString(),
                it[CommunityPresence.displayName],
                it[CommunityPresence.joinedAt].toEpochMilli()
            )
        }
        CommunityPresenceResponse(people)
    }

    fun join(userId: UUID, durationSeconds: Int): CommunityPresenceResponse = transaction {
        val name = Users.selectAll().first { it[Users.id] == userId }[Users.displayName]
        val now = Instant.now()
        val until = now.plusSeconds(durationSeconds.coerceIn(60, 12 * 60 * 60).toLong())
        val existing = CommunityPresence.selectAll().firstOrNull { it[CommunityPresence.userId] == userId }
        if (existing == null) {
            CommunityPresence.insert {
                it[CommunityPresence.userId] = userId
                it[displayName] = name
                it[joinedAt] = now
                it[lastSeenAt] = now
                it[presentUntil] = until
            }
        } else {
            CommunityPresence.update({ CommunityPresence.userId eq userId }) {
                it[displayName] = name
                it[joinedAt] = now
                it[lastSeenAt] = now
                it[presentUntil] = until
            }
        }
        present()
    }

    fun heartbeat(userId: UUID) {
        transaction {
            val name = Users.selectAll().firstOrNull { it[Users.id] == userId }?.get(Users.displayName)
            val existing = CommunityPresence.selectAll().firstOrNull { it[CommunityPresence.userId] == userId }
            if (existing == null) return@transaction
            CommunityPresence.update({ CommunityPresence.userId eq userId }) {
                it[lastSeenAt] = Instant.now()
                if (!name.isNullOrBlank()) it[displayName] = name
            }
        }
    }

    fun leave(userId: UUID, attendedSeconds: Int? = null) {
        transaction {
            val row = CommunityPresence.selectAll().firstOrNull { it[CommunityPresence.userId] == userId }
            if (row != null) {
                val joined = row[CommunityPresence.joinedAt]
                val until = row[CommunityPresence.presentUntil]
                val nowInstant = Instant.now()
                val cap = if (until != null && until.isBefore(nowInstant)) until else nowInstant
                val elapsed = java.time.Duration.between(joined, cap).seconds.toInt().coerceAtLeast(0)
                val seconds = (attendedSeconds ?: elapsed).coerceAtLeast(0)
                writeLog(userId, joined, seconds)
                CommunityPresence.deleteWhere { CommunityPresence.userId eq userId }
            }
        }
    }

    private fun expireStale() {
        val now = Instant.now()
        val heartbeatCutoff = now.minusSeconds(90)
        CommunityPresence.selectAll().forEach { row ->
            val until = row[CommunityPresence.presentUntil]
            val stale = if (until != null) !until.isAfter(now) else row[CommunityPresence.lastSeenAt].isBefore(heartbeatCutoff)
            if (!stale) return@forEach
            val userId = row[CommunityPresence.userId]
            val joined = row[CommunityPresence.joinedAt]
            val end = until ?: row[CommunityPresence.lastSeenAt]
            val seconds = java.time.Duration.between(joined, end).seconds.toInt().coerceAtLeast(0)
            if (until != null) writeLog(userId, joined, seconds)
            CommunityPresence.deleteWhere { CommunityPresence.userId eq userId }
        }
    }

    private fun writeLog(userId: UUID, joinedAt: Instant, seconds: Int) {
        if (seconds <= 0) return
        val id = UUID.nameUUIDFromBytes(
            "open-hall|$userId|${joinedAt.toEpochMilli()}".toByteArray(StandardCharsets.UTF_8)
        )
        if (MeditationLogs.selectAll().any { it[MeditationLogs.id] == id }) return
        val userZone = Users.selectAll().firstOrNull { it[Users.id] == userId }?.get(Users.timezone)
        val zone = runCatching { java.time.ZoneId.of(userZone ?: "UTC") }.getOrDefault(ZoneOffset.UTC)
        MeditationLogs.insert {
            it[MeditationLogs.id] = id
            it[MeditationLogs.userId] = userId
            it[sessionId] = id
            it[hallId] = id
            it[hallName] = "Open Meditation Hall"
            it[date] = joinedAt.atZone(zone).toLocalDate().toString()
            it[durationSeconds] = seconds
            it[completionStatus] = "PARTIAL"
            it[createdAt] = now()
        }
    }
}

package com.meditationhall.services

import com.meditationhall.db.CommunityPresence
import com.meditationhall.db.Users
import com.meditationhall.dto.CommunityPerson
import com.meditationhall.dto.CommunityPresenceResponse
import java.time.Instant
import java.util.UUID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class CommunityService {
    fun present(): CommunityPresenceResponse = transaction {
        val cutoff = Instant.now().minusSeconds(90)
        CommunityPresence.selectAll()
            .filter { it[CommunityPresence.lastSeenAt].isBefore(cutoff) }
            .forEach { row ->
                CommunityPresence.deleteWhere { userId eq row[CommunityPresence.userId] }
            }
        val people = CommunityPresence.selectAll().map {
            CommunityPerson(it[CommunityPresence.userId].toString(), it[CommunityPresence.displayName])
        }
        CommunityPresenceResponse(people)
    }

    fun join(userId: UUID): CommunityPresenceResponse = transaction {
        val name = Users.selectAll().first { it[Users.id] == userId }[Users.displayName]
        val now = Instant.now()
        val existing = CommunityPresence.selectAll().firstOrNull { it[CommunityPresence.userId] == userId }
        if (existing == null) {
            CommunityPresence.insert {
                it[CommunityPresence.userId] = userId
                it[displayName] = name
                it[joinedAt] = now
                it[lastSeenAt] = now
            }
        } else {
            CommunityPresence.update({ CommunityPresence.userId eq userId }) {
                it[displayName] = name
                it[lastSeenAt] = now
            }
        }
        present()
    }

    fun heartbeat(userId: UUID) {
        transaction {
            CommunityPresence.update({ CommunityPresence.userId eq userId }) {
                it[lastSeenAt] = Instant.now()
            }
        }
    }

    fun leave(userId: UUID) {
        transaction {
            CommunityPresence.deleteWhere { CommunityPresence.userId eq userId }
        }
    }
}

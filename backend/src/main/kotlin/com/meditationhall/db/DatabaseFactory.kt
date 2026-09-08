package com.meditationhall.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.time.Instant
import java.util.UUID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init() {
        val jdbc = System.getenv("JDBC_URL") ?: "jdbc:h2:file:./data/meditation;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"
        val user = System.getenv("JDBC_USER") ?: "sa"
        val pass = System.getenv("JDBC_PASSWORD") ?: ""
        val config = HikariConfig().apply {
            jdbcUrl = jdbc
            username = user
            password = pass
            maximumPoolSize = 8
            isAutoCommit = false
        }
        Database.connect(HikariDataSource(config))
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Users, UserDevices, RefreshTokens, Halls, HallMemberships, HallSchedules,
                MeditationSessions, Attendance, MeditationLogs,
                SupporterProfiles, SupportRequests, SupportMatches,
                SupportRelationships, Notifications
            )
            seedIfEmpty()
        }
    }

    private fun seedIfEmpty() {
        if (Halls.selectAll().count() > 0) return
        val seedUser = UUID.fromString("00000000-0000-0000-0000-000000000001")
        Users.insert {
            it[id] = seedUser
            it[displayName] = "Hall Host"
            it[timezone] = "UTC"
            it[status] = "ACTIVE"
            it[createdAt] = Instant.now()
            it[updatedAt] = Instant.now()
        }
        fun hall(id: UUID, name: String, desc: String, duration: Int, code: String) {
            Halls.insert {
                it[Halls.id] = id
                it[creatorId] = seedUser
                it[Halls.name] = name
                it[description] = desc
                it[visibility] = "PUBLIC"
                it[durationSeconds] = duration
                it[timezone] = "UTC"
                it[status] = "ACTIVE"
                it[shareCode] = code
                it[audioType] = "BELL"
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
        }
        val morning = UUID.fromString("00000000-0000-0000-0000-000000000011")
        val evening = UUID.fromString("00000000-0000-0000-0000-000000000012")
        val sunday = UUID.fromString("00000000-0000-0000-0000-000000000013")
        hall(morning, "Morning Vipassana", "A quiet daily sit. Shared timer, optional bell, no conversation.", 3600, "morn01")
        hall(evening, "Evening Sit", "Forty-five minutes after the day. Sit together in silence.", 2700, "even01")
        hall(sunday, "Sunday Long Sit", "A two-hour weekend sitting. Arrive when you can.", 7200, "sunl01")
        HallSchedules.insert {
            it[id] = UUID.randomUUID()
            it[hallId] = morning
            it[scheduleType] = "DAILY"
            it[startLocalTime] = "06:00"
            it[timezone] = "UTC"
            it[daysOfWeek] = ""
            it[enabled] = true
        }
        HallSchedules.insert {
            it[id] = UUID.randomUUID()
            it[hallId] = evening
            it[scheduleType] = "DAILY"
            it[startLocalTime] = "18:00"
            it[timezone] = "UTC"
            it[daysOfWeek] = ""
            it[enabled] = true
        }
        HallSchedules.insert {
            it[id] = UUID.randomUUID()
            it[hallId] = sunday
            it[scheduleType] = "WEEKLY"
            it[startLocalTime] = "09:00"
            it[timezone] = "UTC"
            it[daysOfWeek] = "SUNDAY"
            it[enabled] = true
        }
    }
}

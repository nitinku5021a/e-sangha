package com.meditationhall.db

import java.time.Instant
import java.util.UUID
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object Users : Table("users") {
    val id = uuid("id")
    val displayName = varchar("display_name", 120)
    val email = varchar("email", 255).nullable()
    val googleSub = varchar("google_sub", 64).nullable().uniqueIndex()
    val timezone = varchar("timezone", 64)
    val status = varchar("status", 24)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object RefreshTokens : Table("refresh_tokens") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id).index()
    val tokenHash = varchar("token_hash", 64).uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()
    val createdAt = timestamp("created_at")
    val device = varchar("device", 80).nullable()
    override val primaryKey = PrimaryKey(id)
}

object UserDevices : Table("user_devices") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val fcmToken = varchar("fcm_token", 512)
    val platform = varchar("platform", 24)
    val lastSeenAt = timestamp("last_seen_at")
    val enabled = bool("enabled")
    override val primaryKey = PrimaryKey(id)
}

object Halls : Table("halls") {
    val id = uuid("id")
    val creatorId = uuid("creator_id").references(Users.id)
    val name = varchar("name", 160)
    val description = text("description")
    val visibility = varchar("visibility", 24)
    val durationSeconds = integer("duration_seconds")
    val timezone = varchar("timezone", 64)
    val status = varchar("status", 24)
    val shareCode = varchar("share_code", 16).uniqueIndex()
    val audioType = varchar("audio_type", 24)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object HallMemberships : Table("hall_memberships") {
    val hallId = uuid("hall_id").references(Halls.id)
    val userId = uuid("user_id").references(Users.id)
    val joinedAt = timestamp("joined_at")
    val notificationEnabled = bool("notification_enabled")
    val displayMode = varchar("display_mode", 24)
    override val primaryKey = PrimaryKey(hallId, userId)
}

object HallSchedules : Table("hall_schedules") {
    val id = uuid("id")
    val hallId = uuid("hall_id").references(Halls.id).index()
    val scheduleType = varchar("schedule_type", 24)
    val startLocalTime = varchar("start_local_time", 8)
    val timezone = varchar("timezone", 64)
    val daysOfWeek = varchar("days_of_week", 80)
    val startDate = varchar("start_date", 16).nullable()
    val enabled = bool("enabled")
    override val primaryKey = PrimaryKey(id)
}

object MeditationSessions : Table("meditation_sessions") {
    val id = uuid("id")
    val hallId = uuid("hall_id").references(Halls.id).index()
    val scheduledStart = timestamp("scheduled_start")
    val actualStart = timestamp("actual_start").nullable()
    val durationSeconds = integer("duration_seconds")
    val status = varchar("status", 24)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)

    init {
        index(false, hallId, scheduledStart)
        index(false, scheduledStart, status)
    }
}

object Attendance : Table("attendance") {
    val id = uuid("id")
    val sessionId = uuid("session_id").references(MeditationSessions.id).index()
    val userId = uuid("user_id").references(Users.id).index()
    val hallId = uuid("hall_id")
    val joinedAt = timestamp("joined_at")
    val leftAt = timestamp("left_at").nullable()
    val lastSeenAt = timestamp("last_seen_at")
    val attendedDurationSeconds = integer("attended_duration_seconds")
    val completionStatus = varchar("completion_status", 24)
    override val primaryKey = PrimaryKey(id)
}

object MeditationLogs : Table("meditation_logs") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val sessionId = uuid("session_id")
    val hallId = uuid("hall_id")
    val hallName = varchar("hall_name", 160)
    val date = varchar("date", 16)
    val durationSeconds = integer("duration_seconds")
    val completionStatus = varchar("completion_status", 24)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object SupporterProfiles : Table("supporter_profiles") {
    val userId = uuid("user_id").references(Users.id)
    val enabled = bool("enabled")
    val preferredDurationMinutes = integer("preferred_duration_minutes")
    val maxActiveRelationships = integer("max_active_relationships")
    val language = varchar("language", 16)
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

object SupportRequests : Table("support_requests") {
    val id = uuid("id")
    val requesterId = uuid("requester_id").references(Users.id)
    val status = varchar("status", 24).index()
    val preferredDurationMinutes = integer("preferred_duration_minutes")
    val language = varchar("language", 16)
    val sameHallOnly = bool("same_hall_only")
    val matchedSupporterId = uuid("matched_supporter_id").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object SupportMatches : Table("support_matches") {
    val id = uuid("id")
    val requestId = uuid("request_id").references(SupportRequests.id)
    val supporterId = uuid("supporter_id")
    val status = varchar("status", 24)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object SupportRelationships : Table("support_relationships") {
    val id = uuid("id")
    val supporterId = uuid("supporter_id")
    val supportedUserId = uuid("supported_user_id")
    val createdAt = timestamp("created_at")
    val sessionCount = integer("session_count")
    override val primaryKey = PrimaryKey(id)
}

object Notifications : Table("notifications") {
    val id = uuid("id")
    val idempotencyKey = varchar("idempotency_key", 160).uniqueIndex()
    val userId = uuid("user_id")
    val type = varchar("type", 48)
    val sentAt = timestamp("sent_at")
    override val primaryKey = PrimaryKey(id)
}

fun now(): Instant = Instant.now()
fun uuid(): UUID = UUID.randomUUID()

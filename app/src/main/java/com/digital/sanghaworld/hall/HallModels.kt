package com.digital.sanghaworld.hall

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

enum class HallVisibility { PUBLIC, PRIVATE, UNLISTED }
enum class HallStatus { ACTIVE, PAUSED, ARCHIVED }
enum class ScheduleType { ONCE, DAILY, WEEKLY, WEEKDAYS, CUSTOM }
enum class SessionStatus { SCHEDULED, STARTING, ACTIVE, COMPLETED, CANCELLED }
enum class DisplayMode { SNAPSHOT, AVATAR, HIDDEN }
enum class CompletionStatus { COMPLETED, PARTIAL, NO_SHOW, UNKNOWN }
enum class AudioType { NONE, FILE, BELL }
enum class SupportRequestStatus { OPEN, MATCHED, ACCEPTED, DECLINED, EXPIRED, CANCELLED, COMPLETED }

data class UserProfile(
    val id: String,
    val displayName: String,
    val timezone: String = ZoneId.systemDefault().id,
    val displayMode: DisplayMode = DisplayMode.AVATAR,
    val avatarLabel: String = "You"
)

data class Hall(
    val id: String,
    val creatorId: String,
    val name: String,
    val description: String,
    val visibility: HallVisibility,
    val durationSeconds: Int,
    val timezone: String,
    val status: HallStatus = HallStatus.ACTIVE,
    val shareCode: String,
    val audioType: AudioType = AudioType.BELL,
    val audioUrl: String? = null,
    val audioFileName: String? = null,
    val audioDurationSeconds: Int? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class HallSchedule(
    val id: String,
    val hallId: String,
    val scheduleType: ScheduleType,
    val startLocalTime: LocalTime,
    val timezone: String,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val startDate: LocalDate? = null,
    val enabled: Boolean = true
)

data class HallMembership(
    val hallId: String,
    val userId: String,
    val joinedAtMillis: Long = System.currentTimeMillis(),
    val notificationEnabled: Boolean = true,
    val displayMode: DisplayMode = DisplayMode.AVATAR
)

data class MeditationSession(
    val id: String,
    val hallId: String,
    val scheduledStartMillis: Long,
    val durationSeconds: Int,
    val status: SessionStatus,
    val actualStartMillis: Long? = null
)

data class Attendance(
    val id: String,
    val sessionId: String,
    val userId: String,
    val hallId: String,
    val joinedAtMillis: Long,
    val leftAtMillis: Long? = null,
    val attendedDurationSeconds: Int = 0,
    val completionStatus: CompletionStatus = CompletionStatus.UNKNOWN
)

data class MeditationLogEntry(
    val id: String,
    val userId: String,
    val sessionId: String,
    val hallId: String,
    val hallName: String,
    val date: LocalDate,
    val durationSeconds: Int,
    val completionStatus: CompletionStatus
)

data class HallStats(
    val sessionCount: Int,
    val totalAttendance: Int,
    val uniqueParticipants: Int,
    val totalMeditationSeconds: Long
)

data class ParticipantPresence(
    val userId: String,
    val label: String,
    val displayMode: DisplayMode
)

data class SupporterProfile(
    val userId: String,
    val enabled: Boolean,
    val preferredDurationMinutes: Int = 60,
    val maxActiveRelationships: Int = 2,
    val language: String = "en"
)

data class SupportRequest(
    val id: String,
    val requesterId: String,
    val status: SupportRequestStatus,
    val preferredDurationMinutes: Int,
    val language: String,
    val sameHallOnly: Boolean,
    val matchedSupporterId: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class SupportRelationship(
    val id: String,
    val supporterId: String,
    val supportedUserId: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val sessionCount: Int = 0
)

data class HallDiscovery(
    val sittingNow: List<HallCard>,
    val startingSoon: List<HallCard>,
    val myHalls: List<HallCard>
)

data class HallCard(
    val hall: Hall,
    val schedule: HallSchedule,
    val nextStartMillis: Long,
    val remainingSeconds: Long?,
    val participantCount: Int,
    val joined: Boolean
)

object HallIds {
    fun newId(): String = UUID.randomUUID().toString()

    fun shareCode(): String {
        val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
        return (1..6).map { alphabet.random() }.joinToString("")
    }
}

object SessionClock {
    fun nowMillis(): Long = System.currentTimeMillis()

    fun elapsedSeconds(actualStartMillis: Long, nowMillis: Long = nowMillis()): Long {
        return ((nowMillis - actualStartMillis) / 1000L).coerceAtLeast(0)
    }

    fun remainingSeconds(actualStartMillis: Long, durationSeconds: Int, nowMillis: Long = nowMillis()): Long {
        return (durationSeconds - elapsedSeconds(actualStartMillis, nowMillis)).coerceAtLeast(0)
    }
}

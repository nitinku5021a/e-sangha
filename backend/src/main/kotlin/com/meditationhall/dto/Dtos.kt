package com.meditationhall.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(val displayName: String = "You")

@Serializable
data class GoogleAuthRequest(val idToken: String, val device: String? = null)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class LogoutRequest(val refreshToken: String? = null)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val userId: String,
    val displayName: String
)

@Serializable
data class MeResponse(
    val userId: String,
    val displayName: String,
    val email: String? = null,
    val status: String
)

@Serializable
data class AuthResponse(val token: String, val userId: String, val displayName: String)

@Serializable
data class TimeResponse(val serverTimeMillis: Long)

@Serializable
data class CreateHallRequest(
    val name: String,
    val description: String = "",
    val durationMinutes: Int = 60,
    val hour: Int = 6,
    val minute: Int = 0,
    val scheduleType: String = "DAILY",
    val daysOfWeek: List<String> = emptyList(),
    val visibility: String = "PUBLIC",
    val audioType: String = "BELL",
    val timezone: String = "UTC"
)

@Serializable
data class UpdateHallRequest(
    val name: String? = null,
    val description: String? = null,
    val durationMinutes: Int? = null,
    val hour: Int? = null,
    val minute: Int? = null,
    val scheduleType: String? = null,
    val daysOfWeek: List<String>? = null,
    val visibility: String? = null,
    val audioType: String? = null,
    val timezone: String? = null
)

@Serializable
data class HallDto(
    val id: String,
    val creatorId: String,
    val name: String,
    val description: String,
    val visibility: String,
    val durationSeconds: Int,
    val timezone: String,
    val status: String,
    val shareCode: String,
    val audioType: String,
    val nextStartMillis: Long? = null,
    val remainingSeconds: Long? = null,
    val participantCount: Int = 0,
    val joined: Boolean = false,
    val startLocalTime: String? = null,
    val scheduleType: String? = null,
    val memberCount: Int = 0
)

@Serializable
data class HallsResponse(
    val sittingNow: List<HallDto>,
    val startingSoon: List<HallDto>,
    val myHalls: List<HallDto>
)

@Serializable
data class SessionDto(
    val id: String,
    val hallId: String,
    val scheduledStartMillis: Long,
    val actualStartMillis: Long?,
    val durationSeconds: Int,
    val status: String,
    val participantCount: Int,
    val remainingSeconds: Long
)

@Serializable
data class ParticipantDto(
    val userId: String,
    val displayName: String,
    val displayMode: String = "AVATAR"
)

@Serializable
data class LogDto(
    val id: String,
    val hallId: String,
    val hallName: String,
    val date: String,
    val durationSeconds: Int,
    val completionStatus: String,
    val sessionId: String = ""
)

@Serializable
data class StatsDto(
    val sessionsExpected: Int,
    val sessionsAttended: Int,
    val sessionsCompleted: Int,
    val totalMeditationSeconds: Long
)

@Serializable
data class HallStatsDto(
    val sessionCount: Int,
    val totalAttendance: Int,
    val uniqueParticipants: Int,
    val totalMeditationSeconds: Long
)

@Serializable
data class SupportRequestBody(
    val preferredDurationMinutes: Int = 60,
    val language: String = "en",
    val sameHallOnly: Boolean = false
)

@Serializable
data class SupportRequestDto(
    val id: String,
    val status: String,
    val preferredDurationMinutes: Int,
    val matchedSupporterId: String? = null
)

@Serializable
data class SupporterProfileBody(
    val enabled: Boolean,
    val preferredDurationMinutes: Int = 60,
    val maxActiveRelationships: Int = 2,
    val language: String = "en"
)

@Serializable
data class RelationshipDto(
    val id: String,
    val supporterId: String,
    val supportedUserId: String,
    val sessionCount: Int
)

@Serializable
data class WsEvent(
    val type: String,
    val sessionId: String? = null,
    val hallId: String? = null,
    val userId: String? = null,
    val participantCount: Int? = null,
    val serverTimeMillis: Long? = null,
    val status: String? = null
)

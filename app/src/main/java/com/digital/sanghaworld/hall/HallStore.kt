package com.digital.sanghaworld.hall

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

class HallStore(private val context: Context) {
    private val lock = Any()
    private val file: File get() = File(context.filesDir, "halls.json")

    fun load(): HallState = synchronized(lock) {
        if (!file.exists()) {
            val seeded = seed()
            persist(seeded)
            return seeded
        }
        return runCatching { decode(JSONObject(file.readText())) }.getOrElse {
            val seeded = seed()
            persist(seeded)
            seeded
        }
    }

    fun save(state: HallState) = synchronized(lock) { persist(state) }

    fun discovery(state: HallState, nowMillis: Long = SessionClock.nowMillis()): HallDiscovery {
        val cards = state.halls.filter { it.status == HallStatus.ACTIVE }.mapNotNull { hall ->
            val schedule = state.schedules.firstOrNull { it.hallId == hall.id } ?: return@mapNotNull null
            val next = nextOccurrence(schedule, hall.durationSeconds, nowMillis) ?: return@mapNotNull null
            val remaining = remainingIfActive(next, hall.durationSeconds, nowMillis)
            HallCard(
                hall = hall,
                schedule = schedule,
                nextStartMillis = next,
                remainingSeconds = remaining,
                participantCount = estimatedParticipants(hall, remaining != null, next, nowMillis),
                joined = state.memberships.any { it.hallId == hall.id && it.userId == state.profile.id }
            )
        }
        val sitting = cards.filter { it.remainingSeconds != null }.sortedBy { it.remainingSeconds }
        val soon = cards.filter { it.remainingSeconds == null }
            .sortedBy { it.nextStartMillis }
            .take(8)
        val mine = cards.filter { it.joined }.sortedBy { it.nextStartMillis }
        return HallDiscovery(sitting, soon, mine)
    }

    fun nextOccurrence(schedule: HallSchedule, durationSeconds: Int, nowMillis: Long): Long? {
        val zone = runCatching { ZoneId.of(schedule.timezone) }.getOrDefault(ZoneId.systemDefault())
        var cursor = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        repeat(14) { dayOffset ->
            val day = cursor.toLocalDate()
            if (matchesDay(schedule, day, dayOffset == 0)) {
                val start = ZonedDateTime.of(day, schedule.startLocalTime, zone)
                val end = start.plusSeconds(durationSeconds.toLong())
                if (end.toInstant().toEpochMilli() > nowMillis) {
                    return start.toInstant().toEpochMilli()
                }
            }
            cursor = cursor.plusDays(1).with(LocalTime.MIDNIGHT)
        }
        return null
    }

    private fun remainingIfActive(startMillis: Long, durationSeconds: Int, nowMillis: Long): Long? {
        val end = startMillis + durationSeconds * 1000L
        return if (nowMillis in startMillis until end) {
            ((end - nowMillis) / 1000L)
        } else null
    }

    private fun matchesDay(schedule: HallSchedule, day: LocalDate, isToday: Boolean): Boolean {
        return when (schedule.scheduleType) {
            ScheduleType.ONCE -> schedule.startDate == null || schedule.startDate == day
            ScheduleType.DAILY -> true
            ScheduleType.WEEKDAYS -> day.dayOfWeek.value in 1..5
            ScheduleType.WEEKLY, ScheduleType.CUSTOM ->
                schedule.daysOfWeek.isEmpty() || day.dayOfWeek in schedule.daysOfWeek
        } && (schedule.startDate == null || !day.isBefore(schedule.startDate) || isToday)
    }

    private fun estimatedParticipants(hall: Hall, active: Boolean, nextStart: Long, now: Long): Int {
        val seed = abs(hall.id.hashCode() % 17)
        return if (active) 8 + seed else {
            val hoursUntil = ((nextStart - now) / 3_600_000L).coerceAtLeast(0)
            (4 + seed - hoursUntil.toInt()).coerceAtLeast(1)
        }
    }

    private fun persist(state: HallState) {
        file.writeText(encode(state).toString())
    }

    private fun seed(): HallState {
        val userId = HallIds.newId()
        val profile = UserProfile(id = userId, displayName = "You")
        val tz = ZoneId.systemDefault().id
        val morning = Hall(
            id = "hall-morning",
            creatorId = "seed",
            name = "Morning Vipassana",
            description = "A quiet daily sit at dawn. Shared timer, optional bell, no conversation.",
            visibility = HallVisibility.PUBLIC,
            durationSeconds = 3600,
            timezone = tz,
            shareCode = "morn01"
        )
        val evening = Hall(
            id = "hall-evening",
            creatorId = "seed",
            name = "Evening Sit",
            description = "Forty-five minutes after the day. Sit together in silence.",
            visibility = HallVisibility.PUBLIC,
            durationSeconds = 2700,
            timezone = tz,
            shareCode = "even01"
        )
        val weekend = Hall(
            id = "hall-weekend",
            creatorId = "seed",
            name = "Sunday Long Sit",
            description = "A two-hour weekend sitting. Arrive when you can; the timer is shared.",
            visibility = HallVisibility.PUBLIC,
            durationSeconds = 7200,
            timezone = tz,
            shareCode = "sunl01"
        )
        val schedules = listOf(
            HallSchedule(
                id = "sch-morning",
                hallId = morning.id,
                scheduleType = ScheduleType.DAILY,
                startLocalTime = LocalTime.of(6, 0),
                timezone = tz
            ),
            HallSchedule(
                id = "sch-evening",
                hallId = evening.id,
                scheduleType = ScheduleType.DAILY,
                startLocalTime = LocalTime.of(18, 0),
                timezone = tz
            ),
            HallSchedule(
                id = "sch-weekend",
                hallId = weekend.id,
                scheduleType = ScheduleType.WEEKLY,
                startLocalTime = LocalTime.of(9, 0),
                timezone = tz,
                daysOfWeek = setOf(DayOfWeek.SUNDAY)
            )
        )
        return HallState(
            profile = profile,
            halls = listOf(morning, evening, weekend),
            schedules = schedules,
            memberships = emptyList(),
            attendance = emptyList(),
            logs = emptyList(),
            supporter = SupporterProfile(userId = userId, enabled = false),
            supportRequests = emptyList(),
            relationships = emptyList()
        )
    }

    private fun encode(state: HallState): JSONObject {
        return JSONObject().apply {
            put("profile", JSONObject().apply {
                put("id", state.profile.id)
                put("displayName", state.profile.displayName)
                put("timezone", state.profile.timezone)
                put("displayMode", state.profile.displayMode.name)
                put("avatarLabel", state.profile.avatarLabel)
            })
            put("halls", JSONArray().apply {
                state.halls.forEach { h ->
                    put(JSONObject().apply {
                        put("id", h.id)
                        put("creatorId", h.creatorId)
                        put("name", h.name)
                        put("description", h.description)
                        put("visibility", h.visibility.name)
                        put("durationSeconds", h.durationSeconds)
                        put("timezone", h.timezone)
                        put("status", h.status.name)
                        put("shareCode", h.shareCode)
                        put("audioType", h.audioType.name)
                        put("createdAtMillis", h.createdAtMillis)
                    })
                }
            })
            put("schedules", JSONArray().apply {
                state.schedules.forEach { s ->
                    put(JSONObject().apply {
                        put("id", s.id)
                        put("hallId", s.hallId)
                        put("scheduleType", s.scheduleType.name)
                        put("startLocalTime", s.startLocalTime.toString())
                        put("timezone", s.timezone)
                        put("daysOfWeek", JSONArray(s.daysOfWeek.map { it.name }))
                        put("startDate", s.startDate?.toString() ?: JSONObject.NULL)
                        put("enabled", s.enabled)
                    })
                }
            })
            put("memberships", JSONArray().apply {
                state.memberships.forEach { m ->
                    put(JSONObject().apply {
                        put("hallId", m.hallId)
                        put("userId", m.userId)
                        put("joinedAtMillis", m.joinedAtMillis)
                        put("notificationEnabled", m.notificationEnabled)
                        put("displayMode", m.displayMode.name)
                    })
                }
            })
            put("attendance", JSONArray().apply {
                state.attendance.forEach { a ->
                    put(JSONObject().apply {
                        put("id", a.id)
                        put("sessionId", a.sessionId)
                        put("userId", a.userId)
                        put("hallId", a.hallId)
                        put("joinedAtMillis", a.joinedAtMillis)
                        put("leftAtMillis", a.leftAtMillis ?: JSONObject.NULL)
                        put("attendedDurationSeconds", a.attendedDurationSeconds)
                        put("completionStatus", a.completionStatus.name)
                    })
                }
            })
            put("logs", JSONArray().apply {
                state.logs.forEach { l ->
                    put(JSONObject().apply {
                        put("id", l.id)
                        put("userId", l.userId)
                        put("sessionId", l.sessionId)
                        put("hallId", l.hallId)
                        put("hallName", l.hallName)
                        put("date", l.date.toString())
                        put("durationSeconds", l.durationSeconds)
                        put("completionStatus", l.completionStatus.name)
                    })
                }
            })
            put("supporter", JSONObject().apply {
                put("userId", state.supporter.userId)
                put("enabled", state.supporter.enabled)
                put("preferredDurationMinutes", state.supporter.preferredDurationMinutes)
                put("maxActiveRelationships", state.supporter.maxActiveRelationships)
                put("language", state.supporter.language)
            })
            put("supportRequests", JSONArray().apply {
                state.supportRequests.forEach { r ->
                    put(JSONObject().apply {
                        put("id", r.id)
                        put("requesterId", r.requesterId)
                        put("status", r.status.name)
                        put("preferredDurationMinutes", r.preferredDurationMinutes)
                        put("language", r.language)
                        put("sameHallOnly", r.sameHallOnly)
                        put("matchedSupporterId", r.matchedSupporterId ?: JSONObject.NULL)
                        put("createdAtMillis", r.createdAtMillis)
                    })
                }
            })
            put("relationships", JSONArray().apply {
                state.relationships.forEach { rel ->
                    put(JSONObject().apply {
                        put("id", rel.id)
                        put("supporterId", rel.supporterId)
                        put("supportedUserId", rel.supportedUserId)
                        put("createdAtMillis", rel.createdAtMillis)
                        put("sessionCount", rel.sessionCount)
                    })
                }
            })
        }
    }

    private fun decode(obj: JSONObject): HallState {
        val profileObj = obj.getJSONObject("profile")
        val profile = UserProfile(
            id = profileObj.getString("id"),
            displayName = profileObj.getString("displayName"),
            timezone = profileObj.optString("timezone", ZoneId.systemDefault().id),
            displayMode = DisplayMode.valueOf(profileObj.optString("displayMode", "AVATAR")),
            avatarLabel = profileObj.optString("avatarLabel", "You")
        )
        val halls = jsonArray(obj, "halls").map { h ->
            Hall(
                id = h.getString("id"),
                creatorId = h.getString("creatorId"),
                name = h.getString("name"),
                description = h.getString("description"),
                visibility = HallVisibility.valueOf(h.getString("visibility")),
                durationSeconds = h.getInt("durationSeconds"),
                timezone = h.getString("timezone"),
                status = HallStatus.valueOf(h.optString("status", "ACTIVE")),
                shareCode = h.getString("shareCode"),
                audioType = AudioType.valueOf(h.optString("audioType", "BELL")),
                createdAtMillis = h.optLong("createdAtMillis", 0L)
            )
        }
        val schedules = jsonArray(obj, "schedules").map { s ->
            val days = mutableSetOf<DayOfWeek>()
            val arr = s.optJSONArray("daysOfWeek")
            if (arr != null) {
                for (i in 0 until arr.length()) days.add(DayOfWeek.valueOf(arr.getString(i)))
            }
            HallSchedule(
                id = s.getString("id"),
                hallId = s.getString("hallId"),
                scheduleType = ScheduleType.valueOf(s.getString("scheduleType")),
                startLocalTime = LocalTime.parse(s.getString("startLocalTime")),
                timezone = s.getString("timezone"),
                daysOfWeek = days,
                startDate = s.optString("startDate", "").takeIf { it.isNotBlank() && it != "null" }?.let { LocalDate.parse(it) },
                enabled = s.optBoolean("enabled", true)
            )
        }
        val memberships = jsonArray(obj, "memberships").map { m ->
            HallMembership(
                hallId = m.getString("hallId"),
                userId = m.getString("userId"),
                joinedAtMillis = m.optLong("joinedAtMillis"),
                notificationEnabled = m.optBoolean("notificationEnabled", true),
                displayMode = DisplayMode.valueOf(m.optString("displayMode", "AVATAR"))
            )
        }
        val attendance = jsonArray(obj, "attendance").map { a ->
            Attendance(
                id = a.getString("id"),
                sessionId = a.getString("sessionId"),
                userId = a.getString("userId"),
                hallId = a.getString("hallId"),
                joinedAtMillis = a.getLong("joinedAtMillis"),
                leftAtMillis = if (a.isNull("leftAtMillis")) null else a.optLong("leftAtMillis"),
                attendedDurationSeconds = a.optInt("attendedDurationSeconds"),
                completionStatus = CompletionStatus.valueOf(a.optString("completionStatus", "UNKNOWN"))
            )
        }
        val logs = jsonArray(obj, "logs").map { l ->
            MeditationLogEntry(
                id = l.getString("id"),
                userId = l.getString("userId"),
                sessionId = l.getString("sessionId"),
                hallId = l.getString("hallId"),
                hallName = l.getString("hallName"),
                date = LocalDate.parse(l.getString("date")),
                durationSeconds = l.getInt("durationSeconds"),
                completionStatus = CompletionStatus.valueOf(l.getString("completionStatus"))
            )
        }
        val sup = obj.optJSONObject("supporter")
        val supporter = if (sup != null) {
            SupporterProfile(
                userId = sup.getString("userId"),
                enabled = sup.optBoolean("enabled"),
                preferredDurationMinutes = sup.optInt("preferredDurationMinutes", 60),
                maxActiveRelationships = sup.optInt("maxActiveRelationships", 2),
                language = sup.optString("language", "en")
            )
        } else {
            SupporterProfile(userId = profile.id, enabled = false)
        }
        val requests = jsonArray(obj, "supportRequests").map { r ->
            SupportRequest(
                id = r.getString("id"),
                requesterId = r.getString("requesterId"),
                status = SupportRequestStatus.valueOf(r.getString("status")),
                preferredDurationMinutes = r.getInt("preferredDurationMinutes"),
                language = r.optString("language", "en"),
                sameHallOnly = r.optBoolean("sameHallOnly"),
                matchedSupporterId = if (r.isNull("matchedSupporterId")) null else r.optString("matchedSupporterId"),
                createdAtMillis = r.optLong("createdAtMillis")
            )
        }
        val rels = jsonArray(obj, "relationships").map { rel ->
            SupportRelationship(
                id = rel.getString("id"),
                supporterId = rel.getString("supporterId"),
                supportedUserId = rel.getString("supportedUserId"),
                createdAtMillis = rel.optLong("createdAtMillis"),
                sessionCount = rel.optInt("sessionCount")
            )
        }
        return HallState(profile, halls, schedules, memberships, attendance, logs, supporter, requests, rels)
    }

    private fun jsonArray(obj: JSONObject, key: String): List<JSONObject> {
        val arr = obj.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }
}

data class HallState(
    val profile: UserProfile,
    val halls: List<Hall>,
    val schedules: List<HallSchedule>,
    val memberships: List<HallMembership>,
    val attendance: List<Attendance>,
    val logs: List<MeditationLogEntry>,
    val supporter: SupporterProfile,
    val supportRequests: List<SupportRequest>,
    val relationships: List<SupportRelationship>
)

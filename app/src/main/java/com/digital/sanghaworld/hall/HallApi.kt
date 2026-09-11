package com.digital.sanghaworld.hall

import com.digital.sanghaworld.BuildConfig
import com.digital.sanghaworld.auth.TokenStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class HallApi(
    private val tokens: TokenStore,
    private val baseUrl: String = BuildConfig.API_BASE_URL.ifBlank { DEFAULT_BASE }
) {
    fun isConfigured(): Boolean = tokens.isSignedIn()

    fun loginWithGoogle(idToken: String): Boolean {
        val res = request(
            "POST",
            "/v1/auth/google",
            JSONObject().put("idToken", idToken).put("device", "android"),
            auth = false
        )
        saveTokens(res)
        return true
    }

    fun refreshSession(): Boolean {
        val refresh = tokens.refreshToken ?: return false
        return runCatching {
            val res = request(
                "POST",
                "/v1/auth/refresh",
                JSONObject().put("refreshToken", refresh),
                auth = false
            )
            saveTokens(res)
            true
        }.getOrDefault(false)
    }

    fun logout() {
        val refresh = tokens.refreshToken
        runCatching {
            if (!refresh.isNullOrBlank()) {
                request("POST", "/v1/auth/logout", JSONObject().put("refreshToken", refresh), auth = false)
            }
        }
        tokens.clear()
    }

    fun me(): JSONObject = request("GET", "/v1/me")

    private fun saveTokens(res: JSONObject) {
        tokens.save(
            access = res.getString("accessToken"),
            refresh = res.getString("refreshToken"),
            userId = res.getString("userId"),
            displayName = res.optString("displayName", "You")
        )
    }

    fun timeMillis(): Long {
        val res = request("GET", "/v1/time")
        return res.getLong("serverTimeMillis")
    }

    fun halls(): HallDiscovery {
        val res = request("GET", "/v1/halls")
        return HallDiscovery(
            sittingNow = parseCards(res.optJSONArray("sittingNow")),
            startingSoon = parseCards(res.optJSONArray("startingSoon")),
            myHalls = parseCards(res.optJSONArray("myHalls"))
        )
    }

    fun getHall(id: String): HallCard {
        return parseCard(request("GET", "/v1/halls/$id"))
    }

    fun createHall(
        name: String,
        description: String,
        durationMinutes: Int,
        hour: Int,
        minute: Int,
        scheduleType: ScheduleType,
        days: Set<DayOfWeek>,
        visibility: HallVisibility,
        audioType: AudioType,
        audioUrl: String? = null,
        audioFileName: String? = null,
        audioDurationSeconds: Int? = null
    ): String {
        val body = JSONObject()
            .put("name", name)
            .put("description", description)
            .put("durationMinutes", durationMinutes)
            .put("hour", hour)
            .put("minute", minute)
            .put("scheduleType", scheduleType.name)
            .put("daysOfWeek", JSONArray(days.map { it.name }))
            .put("visibility", visibility.name)
            .put("audioType", audioType.name)
            .put("timezone", ZoneId.systemDefault().id)
            .put("audioUrl", audioUrl ?: JSONObject.NULL)
            .put("audioFileName", audioFileName ?: JSONObject.NULL)
            .put("audioDurationSeconds", audioDurationSeconds ?: JSONObject.NULL)
        return request("POST", "/v1/halls", body).getString("id")
    }

    fun updateHall(
        id: String,
        name: String,
        description: String,
        durationMinutes: Int,
        hour: Int,
        minute: Int,
        scheduleType: ScheduleType,
        days: Set<DayOfWeek>,
        visibility: HallVisibility,
        audioType: AudioType,
        audioUrl: String? = null,
        audioFileName: String? = null,
        audioDurationSeconds: Int? = null
    ) {
        val body = JSONObject()
            .put("name", name)
            .put("description", description)
            .put("durationMinutes", durationMinutes)
            .put("hour", hour)
            .put("minute", minute)
            .put("scheduleType", scheduleType.name)
            .put("daysOfWeek", JSONArray(days.map { it.name }))
            .put("visibility", visibility.name)
            .put("audioType", audioType.name)
            .put("timezone", ZoneId.systemDefault().id)
            .put("audioUrl", audioUrl ?: JSONObject.NULL)
            .put("audioFileName", audioFileName ?: JSONObject.NULL)
            .put("audioDurationSeconds", audioDurationSeconds ?: JSONObject.NULL)
        request("POST", "/v1/halls/$id", body)
    }

    fun deleteHall(id: String) {
        request("DELETE", "/v1/halls/$id")
    }

    fun joinHall(id: String) {
        request("POST", "/v1/halls/$id/join")
    }

    fun leaveHall(id: String) {
        request("DELETE", "/v1/halls/$id/leave")
    }

    fun hallByCode(code: String): String {
        return request("GET", "/v1/halls/by-code/${code.trim()}").getString("id")
    }

    data class JoinedSession(
        val sessionId: String,
        val remainingMillis: Long,
        val status: String,
        val scheduledStartMillis: Long,
        val participantCount: Int
    )

    fun ensureAndJoinSession(hallId: String): JoinedSession {
        val session = request("POST", "/v1/halls/$hallId/sessions/ensure")
        val sessionId = session.getString("id")
        val joined = request("POST", "/v1/sessions/$sessionId/join")
        val remainingSec = joined.optLong("remainingSeconds", 0L)
        return JoinedSession(
            sessionId = sessionId,
            remainingMillis = remainingSec * 1000L,
            status = joined.optString("status"),
            scheduledStartMillis = joined.optLong("scheduledStartMillis"),
            participantCount = joined.optInt("participantCount")
        )
    }

    fun sessionParticipants(sessionId: String): List<ParticipantPresence> {
        val arr = requestArray("GET", "/v1/sessions/$sessionId/participants")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ParticipantPresence(
                userId = o.getString("userId"),
                label = o.optString("displayName").ifBlank { "Practitioner" },
                displayMode = runCatching { DisplayMode.valueOf(o.optString("displayMode", "AVATAR")) }
                    .getOrDefault(DisplayMode.AVATAR)
            )
        }
    }

    fun leaveSession(sessionId: String) {
        request("POST", "/v1/sessions/$sessionId/leave")
    }

    fun logs(): List<MeditationLogEntry> {
        val arr = requestArray("GET", "/v1/me/meditation-log")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            MeditationLogEntry(
                id = o.getString("id"),
                userId = tokens.userId.orEmpty(),
                sessionId = o.optString("sessionId"),
                hallId = o.getString("hallId"),
                hallName = o.getString("hallName"),
                date = LocalDate.parse(o.getString("date")),
                durationSeconds = o.getInt("durationSeconds"),
                completionStatus = runCatching { CompletionStatus.valueOf(o.getString("completionStatus")) }
                    .getOrDefault(CompletionStatus.UNKNOWN)
            )
        }
    }

    fun hallStats(hallId: String): HallStats {
        val o = request("GET", "/v1/halls/$hallId/stats")
        return HallStats(
            sessionCount = o.optInt("sessionCount"),
            totalAttendance = o.optInt("totalAttendance"),
            uniqueParticipants = o.optInt("uniqueParticipants"),
            totalMeditationSeconds = o.optLong("totalMeditationSeconds")
        )
    }

    fun setSupporter(enabled: Boolean) {
        request(
            "POST",
            "/v1/supporter/profile",
            JSONObject().put("enabled", enabled).put("preferredDurationMinutes", 60)
        )
    }

    fun requestSupport(durationMinutes: Int, sameHallOnly: Boolean): SupportRequest {
        val o = request(
            "POST",
            "/v1/support/requests",
            JSONObject()
                .put("preferredDurationMinutes", durationMinutes)
                .put("sameHallOnly", sameHallOnly)
        )
        return SupportRequest(
            id = o.getString("id"),
            requesterId = tokens.userId.orEmpty(),
            status = SupportRequestStatus.valueOf(o.getString("status")),
            preferredDurationMinutes = o.getInt("preferredDurationMinutes"),
            language = "en",
            sameHallOnly = sameHallOnly,
            matchedSupporterId = o.optString("matchedSupporterId").takeIf { it.isNotBlank() }
        )
    }

    fun listSupportRequests(): List<SupportRequest> {
        val arr = requestArray("GET", "/v1/support/requests")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SupportRequest(
                id = o.getString("id"),
                requesterId = tokens.userId.orEmpty(),
                status = runCatching { SupportRequestStatus.valueOf(o.getString("status")) }
                    .getOrDefault(SupportRequestStatus.OPEN),
                preferredDurationMinutes = o.getInt("preferredDurationMinutes"),
                language = "en",
                sameHallOnly = false,
                matchedSupporterId = o.optString("matchedSupporterId").takeIf { it.isNotBlank() }
            )
        }
    }

    fun cancelSupport(id: String) {
        request("POST", "/v1/support/requests/$id/cancel")
    }

    fun relationships(): List<SupportRelationship> {
        val arr = requestArray("GET", "/v1/support/relationships")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SupportRelationship(
                id = o.getString("id"),
                supporterId = o.getString("supporterId"),
                supportedUserId = o.getString("supportedUserId"),
                sessionCount = o.optInt("sessionCount")
            )
        }
    }

    fun createSupportSession(relationshipId: String): String {
        return request("POST", "/v1/support/relationships/$relationshipId/sessions").getString("hallId")
    }

    fun currentUserId(): String = tokens.userId.orEmpty()

    private fun parseCards(arr: JSONArray?): List<HallCard> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { parseCard(arr.getJSONObject(it)) }
    }

    private fun parseCard(o: JSONObject): HallCard {
        val hall = Hall(
            id = o.getString("id"),
            creatorId = o.getString("creatorId"),
            name = o.getString("name"),
            description = o.getString("description"),
            visibility = HallVisibility.valueOf(o.getString("visibility")),
            durationSeconds = o.getInt("durationSeconds"),
            timezone = o.getString("timezone"),
            status = HallStatus.valueOf(o.getString("status")),
            shareCode = o.getString("shareCode"),
            audioType = runCatching { AudioType.valueOf(o.getString("audioType")) }.getOrDefault(AudioType.BELL),
            audioUrl = o.optString("audioUrl").takeIf { it.isNotBlank() },
            audioFileName = o.optString("audioFileName").takeIf { it.isNotBlank() },
            audioDurationSeconds = o.optInt("audioDurationSeconds").takeIf { it > 0 }
        )
        val time = o.optString("startLocalTime").takeIf { it.isNotBlank() } ?: "06:00"
        val schedule = HallSchedule(
            id = "remote",
            hallId = hall.id,
            scheduleType = runCatching { ScheduleType.valueOf(o.optString("scheduleType", "DAILY")) }
                .getOrDefault(ScheduleType.DAILY),
            startLocalTime = LocalTime.parse(if (time.length == 5) time else "06:00"),
            timezone = hall.timezone
        )
        val remaining = if (o.has("remainingSeconds") && !o.isNull("remainingSeconds")) o.optLong("remainingSeconds") else null
        return HallCard(
            hall = hall,
            schedule = schedule,
            nextStartMillis = o.optLong("nextStartMillis"),
            remainingSeconds = remaining,
            participantCount = o.optInt("participantCount"),
            joined = o.optBoolean("joined")
        )
    }

    private fun request(method: String, path: String, body: JSONObject? = null, auth: Boolean = true): JSONObject {
        val raw = requestRaw(method, path, body, auth)
        if (raw.isBlank()) return JSONObject()
        return JSONObject(raw)
    }

    private fun requestArray(method: String, path: String): JSONArray {
        val raw = requestRaw(method, path, null, true)
        if (raw.isBlank()) return JSONArray()
        return JSONArray(raw)
    }

    private fun requestRaw(method: String, path: String, body: JSONObject?, auth: Boolean, retried: Boolean = false): String {
        val conn = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (auth) tokens.accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            doInput = true
            if (body != null) {
                doOutput = true
                OutputStreamWriter(outputStream).use { it.write(body.toString()) }
            }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.readText().orEmpty()
        if (code == 401 && auth && !retried && refreshSession()) {
            return requestRaw(method, path, body, auth = true, retried = true)
        }
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code: $text")
        }
        return text
    }

    companion object {
        const val DEFAULT_BASE = "http://10.0.2.2:8080"
    }
}

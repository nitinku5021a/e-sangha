package com.digital.sanghaworld.hall

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.digital.sanghaworld.auth.TokenStore
import androidx.lifecycle.viewModelScope
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HallUiState(
    val discovery: HallDiscovery = HallDiscovery(emptyList(), emptyList(), emptyList()),
    val selectedHall: Hall? = null,
    val selectedSchedule: HallSchedule? = null,
    val selectedJoined: Boolean = false,
    val selectedParticipantCount: Int = 0,
    val selectedNextStart: Long = 0,
    val selectedRemaining: Long? = null,
    val stats: HallStats = HallStats(0, 0, 0, 0),
    val logs: List<MeditationLogEntry> = emptyList(),
    val supporter: SupporterProfile? = null,
    val supportRequests: List<SupportRequest> = emptyList(),
    val relationships: List<SupportRelationship> = emptyList(),
    val profile: UserProfile? = null,
    val sittingHallName: String? = null,
    val sittingParticipants: List<ParticipantPresence> = emptyList(),
    val sittingCount: Int = 0,
    val sittingExpected: Int = 0,
    val actionError: String? = null,
    val suppressAutoSitKey: String? = null,
    val seats: List<MeditationSeat> = emptyList(),
    val meditating: Boolean = false
)

class HallViewModel(application: Application) : AndroidViewModel(application) {
    private val store = HallStore(application)
    private val tokens = TokenStore(application)
    private val api = HallApi(tokens)
    private val engine = SessionEngine()
    private val _state = MutableStateFlow(HallUiState())
    val state: StateFlow<HallUiState> = _state.asStateFlow()
    private var useRemote = false

    var activeSessionId: String? = null
        private set
    var activeHallId: String? = null
        private set
    var sessionJoinMillis: Long = 0
        private set
    private var assignedSeatId: Int? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val ui = withContext(Dispatchers.IO) {
                connectRemote()
                if (useRemote) remoteUi() else toUi(store.load())
            }
            _state.value = ui
        }
    }

    private fun connectRemote() {
        if (useRemote) return
        useRemote = api.isConfigured() && runCatching { api.me() }.isSuccess
    }

    private fun remoteUi(selectedId: String? = _state.value.selectedHall?.id): HallUiState {
        val disc = api.halls()
        val logs = runCatching { api.logs() }.getOrDefault(emptyList())
        val reqs = runCatching { api.listSupportRequests() }.getOrDefault(emptyList())
        val rels = runCatching { api.relationships() }.getOrDefault(emptyList())
        val card = selectedId?.let {
            (disc.sittingNow + disc.startingSoon + disc.myHalls).firstOrNull { c -> c.hall.id == it }
                ?: runCatching { api.getHall(it) }.getOrNull()
        }
        val hallLogs = selectedId?.let { id -> logs.filter { it.hallId == id } }.orEmpty()
        val statsFromLogs = if (hallLogs.isEmpty()) HallStats(0, 0, 0, 0) else HallStats(
            sessionCount = hallLogs.map { it.sessionId.ifBlank { it.id } }.distinct().size,
            totalAttendance = hallLogs.size,
            uniqueParticipants = hallLogs.map { it.userId }.distinct().size.coerceAtLeast(1),
            totalMeditationSeconds = hallLogs.sumOf { it.durationSeconds.toLong() }
        )
        val stats = selectedId?.let { id ->
            runCatching { api.hallStats(id) }.getOrNull()?.takeIf { it.sessionCount > 0 || it.totalAttendance > 0 }
        } ?: statsFromLogs
        return HallUiState(
            discovery = disc,
            selectedHall = card?.hall,
            selectedSchedule = card?.schedule,
            selectedJoined = card?.joined == true,
            selectedParticipantCount = card?.participantCount ?: 0,
            selectedNextStart = card?.nextStartMillis ?: 0,
            selectedRemaining = card?.remainingSeconds,
            stats = stats,
            logs = logs,
            supportRequests = reqs,
            relationships = rels,
            profile = UserProfile(
                id = api.currentUserId(),
                displayName = tokens.displayName ?: "You"
            ),
            sittingHallName = _state.value.sittingHallName,
            sittingParticipants = _state.value.sittingParticipants,
            sittingCount = _state.value.sittingCount,
            sittingExpected = _state.value.sittingExpected,
            actionError = _state.value.actionError,
            suppressAutoSitKey = _state.value.suppressAutoSitKey,
            seats = _state.value.seats,
            meditating = _state.value.meditating
        )
    }

    fun prepareHallScene() {
        val current = _state.value
        val seats = HallSeatLayout.seedOccupied(
            seats = HallSeatLayout.standard(),
            live = current.sittingParticipants,
            currentUserId = current.profile?.id.orEmpty(),
            userJoined = current.meditating,
            assignedSeatId = assignedSeatId
        )
        _state.value = current.copy(seats = seats)
    }

    fun joinMeditation() {
        val current = _state.value
        val layout = HallSeatLayout.seedOccupied(
            seats = HallSeatLayout.standard(),
            live = current.sittingParticipants,
            currentUserId = current.profile?.id.orEmpty(),
            userJoined = current.meditating,
            assignedSeatId = assignedSeatId
        )
        val empty = layout.firstOrNull { !it.occupied } ?: return
        assignedSeatId = empty.id
        val seats = HallSeatLayout.seedOccupied(
            seats = HallSeatLayout.standard(),
            live = current.sittingParticipants,
            currentUserId = current.profile?.id.orEmpty(),
            userJoined = true,
            assignedSeatId = empty.id
        )
        _state.value = current.copy(seats = seats, meditating = true)
    }

    fun leaveMeditationSeat() {
        assignedSeatId = null
        val current = _state.value
        val seats = HallSeatLayout.seedOccupied(
            seats = HallSeatLayout.standard(),
            live = current.sittingParticipants.filter { it.userId != current.profile?.id },
            currentUserId = current.profile?.id.orEmpty(),
            userJoined = false,
            assignedSeatId = null
        )
        _state.value = current.copy(seats = seats, meditating = false)
    }

    fun openHall(hallId: String) {
        viewModelScope.launch {
            val ui = withContext(Dispatchers.IO) {
                connectRemote()
                if (useRemote) remoteUi(hallId) else {
                    val s = store.load()
                    val disc = store.discovery(s)
                    val card = (disc.sittingNow + disc.startingSoon + disc.myHalls).firstOrNull { it.hall.id == hallId }
                        ?: return@withContext _state.value
                    val stats = hallStats(s, hallId)
                    _state.value.copy(
                        discovery = disc,
                        selectedHall = card.hall,
                        selectedSchedule = card.schedule,
                        selectedJoined = card.joined,
                        selectedParticipantCount = card.participantCount,
                        selectedNextStart = card.nextStartMillis,
                        selectedRemaining = card.remainingSeconds,
                        stats = stats,
                        logs = s.logs,
                        supporter = s.supporter,
                        supportRequests = s.supportRequests,
                        relationships = s.relationships,
                        profile = s.profile
                    )
                }
            }
            _state.value = ui
            prepareHallScene()
        }
    }

    fun joinHall(hallId: String) {
        if (useRemote) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { api.joinHall(hallId) }
                openHall(hallId)
            }
            return
        }
        mutate { s ->
            if (s.memberships.any { it.hallId == hallId && it.userId == s.profile.id }) s
            else s.copy(memberships = s.memberships + HallMembership(hallId, s.profile.id))
        }
        openHall(hallId)
    }

    fun leaveHall(hallId: String) {
        if (useRemote) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { api.leaveHall(hallId) }
                openHall(hallId)
            }
            return
        }
        mutate { s ->
            s.copy(memberships = s.memberships.filterNot { it.hallId == hallId && it.userId == s.profile.id })
        }
        openHall(hallId)
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
        onCreated: (String?) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    connectRemote()
                    if (useRemote) {
                        api.createHall(
                            name, description, durationMinutes, hour, minute,
                            scheduleType, days, visibility, audioType
                        )
                    } else {
                        createHallLocal(
                            name, description, durationMinutes, hour, minute,
                            scheduleType, days, visibility, audioType
                        )
                    }
                }
            }
            val id = result.getOrNull()
            if (id != null) openHall(id)
            onCreated(id)
        }
    }

    private fun createHallLocal(
        name: String,
        description: String,
        durationMinutes: Int,
        hour: Int,
        minute: Int,
        scheduleType: ScheduleType,
        days: Set<DayOfWeek>,
        visibility: HallVisibility,
        audioType: AudioType
    ): String {
        val hallId = HallIds.newId()
        mutate { s ->
            val hall = Hall(
                id = hallId,
                creatorId = s.profile.id,
                name = name.trim().ifBlank { "Untitled hall" },
                description = description.trim(),
                visibility = visibility,
                durationSeconds = (durationMinutes.coerceIn(1, 240)) * 60,
                timezone = ZoneId.systemDefault().id,
                shareCode = HallIds.shareCode(),
                audioType = audioType
            )
            val schedule = HallSchedule(
                id = HallIds.newId(),
                hallId = hallId,
                scheduleType = scheduleType,
                startLocalTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)),
                timezone = hall.timezone,
                daysOfWeek = days
            )
            val membership = HallMembership(hallId, s.profile.id)
            s.copy(
                halls = s.halls + hall,
                schedules = s.schedules + schedule,
                memberships = s.memberships + membership
            )
        }
        return hallId
    }

    fun updateHall(
        hallId: String,
        name: String,
        description: String,
        durationMinutes: Int,
        hour: Int,
        minute: Int,
        scheduleType: ScheduleType,
        days: Set<DayOfWeek>,
        visibility: HallVisibility,
        audioType: AudioType,
        onDone: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    connectRemote()
                    if (useRemote) {
                        api.updateHall(
                            hallId, name, description, durationMinutes, hour, minute,
                            scheduleType, days, visibility, audioType
                        )
                    } else {
                        mutate { s ->
                            if (s.halls.none { it.id == hallId && it.creatorId == s.profile.id }) s
                            else s.copy(
                                halls = s.halls.map { hall ->
                                    if (hall.id != hallId) hall
                                    else hall.copy(
                                        name = name.trim().ifBlank { "Untitled hall" },
                                        description = description.trim(),
                                        durationSeconds = durationMinutes.coerceIn(1, 240) * 60,
                                        visibility = visibility,
                                        audioType = audioType,
                                        timezone = ZoneId.systemDefault().id
                                    )
                                },
                                schedules = s.schedules.map { sch ->
                                    if (sch.hallId != hallId) sch
                                    else sch.copy(
                                        scheduleType = scheduleType,
                                        startLocalTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)),
                                        timezone = ZoneId.systemDefault().id,
                                        daysOfWeek = days
                                    )
                                }
                            )
                        }
                    }
                }.isSuccess
            }
            if (ok) {
                _state.value = _state.value.copy(actionError = null)
                openHall(hallId)
            } else {
                _state.value = _state.value.copy(actionError = "Could not update this hall.")
            }
            onDone(ok)
        }
    }

    fun deleteHall(hallId: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    connectRemote()
                    if (useRemote) {
                        api.deleteHall(hallId)
                    } else {
                        val s = store.load()
                        val hall = s.halls.firstOrNull { it.id == hallId }
                            ?: error("Hall not found.")
                        if (hall.creatorId != s.profile.id) error("Only the hall creator can delete this hall.")
                        val others = s.memberships.any { it.hallId == hallId && it.userId != s.profile.id }
                        if (others) error("HALL_HAS_MEMBERS")
                        mutate { st ->
                            st.copy(
                                halls = st.halls.map {
                                    if (it.id == hallId) it.copy(status = HallStatus.ARCHIVED) else it
                                },
                                memberships = st.memberships.filterNot { it.hallId == hallId }
                            )
                        }
                    }
                }
            }
            if (result.isSuccess) {
                _state.value = _state.value.copy(
                    selectedHall = null,
                    actionError = null
                )
                refresh()
                onDone(true)
            } else {
                val raw = result.exceptionOrNull()?.message.orEmpty()
                val message = when {
                    raw.contains("HALL_HAS_MEMBERS") ->
                        "Other people are still in this hall. They must leave before you can delete it."
                    raw.contains("NOT_CREATOR") ->
                        "Only the hall creator can delete this hall."
                    else -> "Could not delete this hall."
                }
                _state.value = _state.value.copy(actionError = message)
                onDone(false)
            }
        }
    }

    /**
     * Join the upcoming sitting as arrived (from 15 minutes before start).
     * Does not start the meditation timer.
     */
    fun arriveForSitting(hallId: String) {
        if (activeHallId == hallId && activeSessionId != null) {
            refreshPresence()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                connectRemote()
                if (useRemote) {
                    val joined = api.ensureAndJoinSession(hallId)
                    activeSessionId = joined.sessionId
                    activeHallId = hallId
                    sessionJoinMillis = SessionClock.nowMillis()
                    val hall = runCatching { api.getHall(hallId).hall }.getOrNull()
                    val people = runCatching { api.sessionParticipants(joined.sessionId) }.getOrDefault(
                        listOf(
                            ParticipantPresence(
                                api.currentUserId(),
                                tokens.displayName ?: "You",
                                DisplayMode.AVATAR
                            )
                        )
                    )
                    _state.value = _state.value.copy(
                        sittingHallName = hall?.name ?: _state.value.selectedHall?.name,
                        sittingCount = people.size.coerceAtLeast(joined.participantCount),
                        sittingExpected = maxOf(
                            _state.value.sittingExpected,
                            _state.value.selectedParticipantCount,
                            people.size
                        ),
                        sittingParticipants = people
                    )
                } else {
                    enterSessionLocal(hallId)
                }
            }
        }
    }

    fun refreshPresence() {
        val sessionId = activeSessionId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (useRemote) {
                    val people = api.sessionParticipants(sessionId)
                    _state.value = _state.value.copy(
                        sittingParticipants = people,
                        sittingCount = people.size
                    )
                    prepareHallScene()
                }
            }
        }
    }

    /**
     * Returns remaining duration in millis for TimerService, or null if not yet started
     * (caller should wait / start at scheduled time with full duration).
     */
    fun enterSession(hallId: String, onRemaining: (Long?) -> Unit) {
        if (useRemote) {
            viewModelScope.launch {
                val remaining = withContext(Dispatchers.IO) {
                    runCatching {
                        val joined = api.ensureAndJoinSession(hallId)
                        activeSessionId = joined.sessionId
                        activeHallId = hallId
                        sessionJoinMillis = SessionClock.nowMillis()
                        val hall = runCatching { api.getHall(hallId).hall }.getOrNull()
                        val people = runCatching { api.sessionParticipants(joined.sessionId) }.getOrDefault(
                            listOf(
                                ParticipantPresence(
                                    api.currentUserId(),
                                    tokens.displayName ?: "You",
                                    DisplayMode.AVATAR
                                )
                            )
                        )
                        _state.value = _state.value.copy(
                            sittingHallName = hall?.name,
                            sittingCount = people.size.coerceAtLeast(1),
                            sittingExpected = maxOf(
                                _state.value.sittingExpected,
                                _state.value.selectedParticipantCount,
                                people.size
                            ),
                            sittingParticipants = people
                        )
                        joined.remainingMillis.takeIf { it > 0 }
                    }.getOrNull()
                }
                onRemaining(remaining)
            }
            return
        }
        val remaining = enterSessionLocal(hallId)
        onRemaining(remaining.takeIf { it != null && it > 0 })
    }

    private fun enterSessionLocal(hallId: String): Long? {
        val s = store.load()
        val hall = s.halls.firstOrNull { it.id == hallId } ?: return null
        val schedule = s.schedules.firstOrNull { it.hallId == hallId } ?: return null
        val now = SessionClock.nowMillis()
        val start = store.nextOccurrence(schedule, hall.durationSeconds, now) ?: return null
        val inSession = now >= start
        val remaining = SessionClock.remainingSeconds(start, hall.durationSeconds, now)
        val sessionId = "$hallId-$start"
        engine.handleSessionStarted(start, hall.durationSeconds)
        activeSessionId = sessionId
        activeHallId = hallId
        sessionJoinMillis = now
        val you = ParticipantPresence(s.profile.id, s.profile.displayName, DisplayMode.AVATAR)
        val presence = listOf(you)
        _state.value = _state.value.copy(
            sittingHallName = hall.name,
            sittingParticipants = presence,
            sittingCount = presence.size,
            sittingExpected = maxOf(_state.value.selectedParticipantCount, presence.size)
        )
        mutate { st ->
            val already = st.attendance.any { it.sessionId == sessionId && it.userId == st.profile.id && it.leftAtMillis == null }
            if (already) st
            else st.copy(
                attendance = st.attendance + Attendance(
                    id = HallIds.newId(),
                    sessionId = sessionId,
                    userId = st.profile.id,
                    hallId = hallId,
                    joinedAtMillis = now
                )
            )
        }
        if (!inSession) return null
        val remainingMillis = remaining * 1000L
        return remainingMillis.coerceAtLeast(1_000L)
    }

    fun completeSession(attendedMillis: Long) {
        val sessionId = activeSessionId ?: return
        val hallId = activeHallId ?: return
        val sitKey = "$hallId-${_state.value.selectedNextStart}"
        activeSessionId = null
        activeHallId = null
        assignedSeatId = null
        _state.value = _state.value.copy(
            sittingHallName = null,
            sittingParticipants = emptyList(),
            sittingCount = 0,
            sittingExpected = 0,
            suppressAutoSitKey = sitKey,
            meditating = false
        )
        leaveMeditationSeat()
        if (useRemote) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { api.leaveSession(sessionId) }
                refresh()
            }
            return
        }
        val now = SessionClock.nowMillis()
        mutate { s ->
            val hall = s.halls.firstOrNull { it.id == hallId }
            val durationSeconds = hall?.durationSeconds ?: (attendedMillis / 1000).toInt()
            val attendedSec = (attendedMillis / 1000).toInt().coerceAtLeast(1)
            val threshold = (durationSeconds * 0.9).toInt()
            val completion = if (attendedSec >= threshold) CompletionStatus.COMPLETED else CompletionStatus.PARTIAL
            val updatedAttendance = s.attendance.map {
                if (it.sessionId == sessionId && it.userId == s.profile.id && it.leftAtMillis == null) {
                    it.copy(
                        leftAtMillis = now,
                        attendedDurationSeconds = attendedSec,
                        completionStatus = completion
                    )
                } else it
            }
            val log = MeditationLogEntry(
                id = HallIds.newId(),
                userId = s.profile.id,
                sessionId = sessionId,
                hallId = hallId,
                hallName = hall?.name ?: "Hall",
                date = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate(),
                durationSeconds = attendedSec,
                completionStatus = completion
            )
            s.copy(attendance = updatedAttendance, logs = listOf(log) + s.logs)
        }
        engine.handleSessionCompleted()
        refresh()
    }

    fun setSupporterEnabled(enabled: Boolean) {
        if (useRemote) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { api.setSupporter(enabled) }
                refresh()
            }
            return
        }
        mutate { s -> s.copy(supporter = s.supporter.copy(enabled = enabled)) }
    }

    fun requestSupport(durationMinutes: Int, sameHallOnly: Boolean) {
        if (useRemote) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { api.requestSupport(durationMinutes, sameHallOnly) }
                refresh()
            }
            return
        }
        mutate { s ->
            val request = SupportRequest(
                id = HallIds.newId(),
                requesterId = s.profile.id,
                status = if (s.supporter.enabled) SupportRequestStatus.MATCHED else SupportRequestStatus.OPEN,
                preferredDurationMinutes = durationMinutes,
                language = "en",
                sameHallOnly = sameHallOnly,
                matchedSupporterId = if (s.supporter.enabled) s.profile.id else "community-sitter"
            )
            val rels = if (request.status == SupportRequestStatus.MATCHED) {
                s.relationships + SupportRelationship(
                    id = HallIds.newId(),
                    supporterId = request.matchedSupporterId ?: s.profile.id,
                    supportedUserId = s.profile.id
                )
            } else s.relationships
            s.copy(supportRequests = s.supportRequests + request, relationships = rels)
        }
    }

    fun cancelSupport(requestId: String) {
        if (useRemote) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { api.cancelSupport(requestId) }
                refresh()
            }
            return
        }
        mutate { s ->
            s.copy(supportRequests = s.supportRequests.map {
                if (it.id == requestId) it.copy(status = SupportRequestStatus.CANCELLED) else it
            })
        }
    }

    fun createPrivateSupportHall(onCreated: (String?) -> Unit = {}) {
        createHall(
            name = "Private support sit",
            description = "A silent sitting for two. No conversation during meditation.",
            durationMinutes = 60,
            hour = LocalTime.now().hour,
            minute = ((LocalTime.now().minute / 5) * 5),
            scheduleType = ScheduleType.ONCE,
            days = emptySet(),
            visibility = HallVisibility.PRIVATE,
            audioType = AudioType.BELL,
            onCreated = onCreated
        )
    }

    fun findByShareCode(code: String, onFound: (String?) -> Unit) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                runCatching {
                    connectRemote()
                    if (useRemote) api.hallByCode(code)
                    else store.load().halls.firstOrNull {
                        it.shareCode.equals(code.trim(), ignoreCase = true)
                    }?.id
                }.getOrNull()
            }
            if (id != null) openHall(id)
            onFound(id)
        }
    }

    private fun mutate(block: (HallState) -> HallState) {
        val next = synchronized(store) {
            val current = store.load()
            val updated = block(current)
            store.save(updated)
            updated
        }
        _state.value = toUi(next).copy(
            selectedHall = _state.value.selectedHall?.let { sel -> next.halls.firstOrNull { it.id == sel.id } },
            sittingHallName = _state.value.sittingHallName,
            sittingParticipants = _state.value.sittingParticipants,
            sittingCount = _state.value.sittingCount,
            sittingExpected = _state.value.sittingExpected,
            suppressAutoSitKey = _state.value.suppressAutoSitKey,
            seats = _state.value.seats,
            meditating = _state.value.meditating
        )
    }

    private fun toUi(s: HallState): HallUiState {
        val disc = store.discovery(s)
        val selected = _state.value.selectedHall
        val card = selected?.let { (disc.sittingNow + disc.startingSoon + disc.myHalls).firstOrNull { c -> c.hall.id == it.id } }
        return HallUiState(
            discovery = disc,
            selectedHall = card?.hall ?: selected,
            selectedSchedule = card?.schedule ?: _state.value.selectedSchedule,
            selectedJoined = card?.joined ?: false,
            selectedParticipantCount = card?.participantCount ?: 0,
            selectedNextStart = card?.nextStartMillis ?: 0,
            selectedRemaining = card?.remainingSeconds,
            stats = selected?.let { hallStats(s, it.id) } ?: HallStats(0, 0, 0, 0),
            logs = s.logs,
            supporter = s.supporter,
            supportRequests = s.supportRequests,
            relationships = s.relationships,
            profile = s.profile,
            sittingHallName = _state.value.sittingHallName,
            sittingParticipants = _state.value.sittingParticipants,
            sittingCount = _state.value.sittingCount,
            sittingExpected = _state.value.sittingExpected,
            actionError = _state.value.actionError,
            suppressAutoSitKey = _state.value.suppressAutoSitKey,
            seats = _state.value.seats,
            meditating = _state.value.meditating
        )
    }

    private fun hallStats(s: HallState, hallId: String): HallStats {
        val att = s.attendance.filter { it.hallId == hallId }
        val logs = s.logs.filter { it.hallId == hallId }
        return HallStats(
            sessionCount = logs.map { it.sessionId }.distinct().size,
            totalAttendance = att.size,
            uniqueParticipants = att.map { it.userId }.distinct().size.coerceAtLeast(if (att.isEmpty()) 0 else 1),
            totalMeditationSeconds = logs.sumOf { it.durationSeconds.toLong() }
        )
    }

    private fun syntheticPresence(s: HallState, hall: Hall, active: Boolean): List<ParticipantPresence> {
        val names = listOf("Nitin", "Prashant", "Rahul", "Maya", "Arun", "Leela", "Sam")
        val count = if (active) 5 else 3
        val you = ParticipantPresence(s.profile.id, s.profile.displayName, s.profile.displayMode)
        val others = names.take(count).mapIndexed { i, n ->
            ParticipantPresence("p$i-${hall.id}", n, if (i % 3 == 0) DisplayMode.SNAPSHOT else DisplayMode.AVATAR)
        }
        return listOf(you) + others
    }
}

package com.digital.sanghaworld.hall

/**
 * Compose-independent session clock. The server (here: local store) is authoritative
 * for actualStart and duration; the client never receives timer ticks.
 */
class SessionEngine {
    private var actualStartMillis: Long = 0
    private var durationSeconds: Int = 0
    private var clockOffsetMillis: Long = 0
    private var status: SessionStatus = SessionStatus.SCHEDULED

    fun start(actualStartMillis: Long, durationSeconds: Int, clockOffsetMillis: Long = 0) {
        this.actualStartMillis = actualStartMillis
        this.durationSeconds = durationSeconds
        this.clockOffsetMillis = clockOffsetMillis
        this.status = SessionStatus.ACTIVE
    }

    fun sync(actualStartMillis: Long, durationSeconds: Int, clockOffsetMillis: Long) {
        this.actualStartMillis = actualStartMillis
        this.durationSeconds = durationSeconds
        this.clockOffsetMillis = clockOffsetMillis
    }

    fun handleSessionStarted(actualStartMillis: Long, durationSeconds: Int) {
        start(actualStartMillis, durationSeconds, clockOffsetMillis)
    }

    fun handleSessionCompleted() {
        status = SessionStatus.COMPLETED
    }

    fun synchronizedNow(): Long = System.currentTimeMillis() + clockOffsetMillis

    fun getElapsedSeconds(): Long {
        if (status != SessionStatus.ACTIVE && status != SessionStatus.STARTING) return 0
        return SessionClock.elapsedSeconds(actualStartMillis, synchronizedNow())
    }

    fun getRemainingSeconds(): Long {
        if (status == SessionStatus.COMPLETED) return 0
        return SessionClock.remainingSeconds(actualStartMillis, durationSeconds, synchronizedNow())
    }

    fun getStatus(): SessionStatus = status
}

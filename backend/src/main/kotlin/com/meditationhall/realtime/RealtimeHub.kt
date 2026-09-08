package com.meditationhall.realtime

import com.meditationhall.dto.WsEvent
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RealtimeHub {
    private val sessions = ConcurrentHashMap<WebSocketSession, UUID>()
    private val presence = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Long>>()
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true }

    suspend fun register(userId: UUID, socket: WebSocketSession) {
        mutex.withLock { sessions[socket] = userId }
    }

    suspend fun unregister(socket: WebSocketSession) {
        mutex.withLock { sessions.remove(socket) }
    }

    fun heartbeat(sessionId: UUID, userId: UUID) {
        val map = presence.getOrPut(sessionId) { ConcurrentHashMap() }
        map[userId] = System.currentTimeMillis()
        prune(sessionId)
    }

    fun leave(sessionId: UUID, userId: UUID) {
        presence[sessionId]?.remove(userId)
    }

    fun count(sessionId: UUID): Int {
        prune(sessionId)
        return presence[sessionId]?.size ?: 0
    }

    private fun prune(sessionId: UUID) {
        val cutoff = System.currentTimeMillis() - 90_000
        presence[sessionId]?.entries?.removeIf { it.value < cutoff }
    }

    suspend fun broadcast(event: WsEvent) {
        val payload = json.encodeToString(event)
        val snapshot = sessions.keys.toList()
        snapshot.forEach { socket ->
            runCatching { socket.send(Frame.Text(payload)) }
        }
    }
}

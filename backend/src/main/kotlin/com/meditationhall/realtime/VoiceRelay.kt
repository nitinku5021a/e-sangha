package com.meditationhall.realtime

import com.meditationhall.ApiException
import com.meditationhall.dto.VoiceClipDto
import com.meditationhall.dto.VoicePollDto
import io.ktor.http.HttpStatusCode
import java.util.ArrayDeque
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory one-way voice for a scheduled sitting.
 * A buffer exists only after the hall creator indicates a broadcast.
 * Clips from the last minute stay available for one minute after the sitting ends.
 */
class VoiceRelay {
    private class Clip(val seq: Int, val bytes: ByteArray, val at: Long)
    private class Buffer {
        val clips = ArrayDeque<Clip>()
        var opened = false
        var ended = false
        var endMillis = 0L
        var lastAt = 0L
    }

    private val sessions = ConcurrentHashMap<UUID, Buffer>()

    fun publish(sessionId: UUID, sittingEndMillis: Long, seq: Int, bytes: ByteArray, ended: Boolean) {
        val now = System.currentTimeMillis()
        val buffer = sessions.computeIfAbsent(sessionId) { Buffer() }
        synchronized(buffer) {
            buffer.endMillis = sittingEndMillis
            if (buffer.endMillis > 0 && now > buffer.endMillis + WINDOW_MS) {
                sessions.remove(sessionId, buffer)
                throw ApiException(HttpStatusCode.Conflict, "VOICE_CLOSED", "The broadcast window has closed.")
            }
            if (!buffer.opened && now > sittingEndMillis) {
                throw ApiException(HttpStatusCode.Conflict, "VOICE_CLOSED", "The sitting has ended.")
            }
            if (buffer.ended && bytes.isNotEmpty()) {
                throw ApiException(HttpStatusCode.Conflict, "VOICE_ENDED", "This broadcast has ended.")
            }
            if (bytes.size > MAX_BYTES) {
                throw ApiException(HttpStatusCode.PayloadTooLarge, "VOICE_TOO_LARGE", "Voice clip is too large.")
            }
            buffer.opened = true
            if (bytes.isNotEmpty()) {
                buffer.clips.addLast(Clip(seq, bytes, now))
                buffer.lastAt = now
                while (buffer.clips.isNotEmpty() && now - buffer.clips.first().at > WINDOW_MS) {
                    buffer.clips.removeFirst()
                }
            }
            if (ended) buffer.ended = true
        }
    }

    fun poll(sessionId: UUID, after: Int): VoicePollDto {
        val now = System.currentTimeMillis()
        val buffer = sessions[sessionId] ?: return VoicePollDto(speaking = false, ended = false)
        synchronized(buffer) {
            if (buffer.endMillis > 0 && now > buffer.endMillis + WINDOW_MS) {
                sessions.remove(sessionId, buffer)
                return VoicePollDto(speaking = false, ended = true)
            }
            val clips = buffer.clips
                .filter { it.seq > after }
                .map { VoiceClipDto(seq = it.seq, audioBase64 = Base64.getEncoder().encodeToString(it.bytes)) }
            val speaking = buffer.opened && !buffer.ended && buffer.lastAt > 0 && now - buffer.lastAt < SPEAKING_GAP_MS
            return VoicePollDto(speaking = speaking, ended = buffer.ended, clips = clips)
        }
    }

    private companion object {
        const val WINDOW_MS = 60_000L
        const val SPEAKING_GAP_MS = 6_000L
        const val MAX_BYTES = 256 * 1024
    }
}

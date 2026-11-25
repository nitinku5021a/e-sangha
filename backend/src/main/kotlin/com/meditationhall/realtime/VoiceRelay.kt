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
        var segment = 0L
        var nextSeq = 0
        var opened = false
        var ended = false
        var endMillis = 0L
        var lastAt = 0L
    }

    private val sessions = ConcurrentHashMap<UUID, Buffer>()

    fun publish(sessionId: UUID, sittingEndMillis: Long, segment: Long, bytes: ByteArray, ended: Boolean) {
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
            // A late clip or ended marker from an earlier Speak tap must not close the new one.
            if (buffer.segment > 0 && segment < buffer.segment) return
            if (segment > buffer.segment) {
                buffer.segment = segment
                buffer.ended = false
            }
            if (bytes.size > MAX_BYTES) {
                throw ApiException(HttpStatusCode.PayloadTooLarge, "VOICE_TOO_LARGE", "Voice clip is too large.")
            }
            buffer.opened = true
            if (bytes.isNotEmpty()) {
                buffer.nextSeq += 1
                buffer.clips.addLast(Clip(buffer.nextSeq, bytes, now))
                buffer.lastAt = now
                while (buffer.clips.isNotEmpty() && now - buffer.clips.first().at > WINDOW_MS) {
                    buffer.clips.removeFirst()
                }
            }
            if (ended && segment == buffer.segment) buffer.ended = true
        }
    }

    fun poll(sessionId: UUID, after: Int, live: Boolean = false): VoicePollDto {
        val now = System.currentTimeMillis()
        val buffer = sessions[sessionId] ?: return VoicePollDto(speaking = false, ended = false)
        synchronized(buffer) {
            if (buffer.endMillis > 0 && now > buffer.endMillis + WINDOW_MS) {
                sessions.remove(sessionId, buffer)
                return VoicePollDto(speaking = false, ended = true)
            }
            val latest = buffer.clips.lastOrNull()?.seq ?: 0
            val cursor = if (live) latest else after
            val clips = buffer.clips
                .filter { it.seq > cursor }
                .map { VoiceClipDto(seq = it.seq, audioBase64 = Base64.getEncoder().encodeToString(it.bytes)) }
            val speaking = buffer.opened && !buffer.ended && buffer.lastAt > 0 && now - buffer.lastAt < SPEAKING_GAP_MS
            return VoicePollDto(speaking = speaking, ended = buffer.ended, latestSeq = latest, clips = clips)
        }
    }

    private companion object {
        const val WINDOW_MS = 60_000L
        const val SPEAKING_GAP_MS = 6_000L
        const val MAX_BYTES = 256 * 1024
    }
}

package com.digital.sanghaworld.hall

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * One-way hall speech. The creator records short clips. Other sitters poll and play them.
 * Listening stops when [listen] is called with a null session.
 */
class HallVoiceController(context: Context, private val api: HallApi) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val holding = AtomicBoolean(false)
    private val seq = AtomicInteger(0)
    private var recordJob: Job? = null
    private var listenJob: Job? = null
    private var listenSession: String? = null
    private val main = Handler(Looper.getMainLooper())
    private val playerLock = Any()
    private var player: ExoPlayer? = null
    private val queue = ArrayDeque<File>()
    private var playing = false
    private var currentFile: File? = null

    private val _hostSpeaking = MutableStateFlow(false)
    val hostSpeaking: StateFlow<Boolean> = _hostSpeaking.asStateFlow()
    private val _micOpen = MutableStateFlow(false)
    val micOpen: StateFlow<Boolean> = _micOpen.asStateFlow()

    fun begin(sessionId: String) {
        if (!holding.compareAndSet(false, true)) return
        seq.set(0)
        _micOpen.value = true
        recordJob = scope.launch {
            val recorder = openMic()
            try {
                if (recorder == null) return@launch
                recorder.startRecording()
                launch { runCatching { api.postVoice(sessionId, seq = 0, ended = false, bytes = ByteArray(0)) } }
                val buf = ByteArray(recorder.bufferSizeInFrames.coerceAtLeast(1024) * 2)
                val chunk = ByteArrayOutputStream()
                var chunkStart = System.nanoTime()
                while (isActive && holding.get()) {
                    val read = recorder.read(buf, 0, buf.size)
                    if (read > 0) chunk.write(buf, 0, read)
                    val elapsed = System.nanoTime() - chunkStart
                    if (elapsed >= 2_000_000_000L && chunk.size() > 0) {
                        sendClip(sessionId, chunk.toByteArray())
                        chunk.reset()
                        chunkStart = System.nanoTime()
                    }
                }
                if (chunk.size() > 0) sendClip(sessionId, chunk.toByteArray())
                val n = seq.incrementAndGet()
                runCatching { api.postVoice(sessionId, seq = n, ended = true, bytes = ByteArray(0)) }
            } finally {
                runCatching { recorder?.stop() }
                runCatching { recorder?.release() }
                holding.set(false)
                _micOpen.value = false
            }
        }
    }

    private fun sendClip(sessionId: String, pcm: ByteArray) {
        val n = seq.incrementAndGet()
        runCatching { api.postVoice(sessionId, seq = n, ended = false, bytes = wav(pcm)) }
    }

    /** Finish the clip already being recorded and send the ended marker. */
    fun releaseHold() {
        holding.set(false)
    }

    fun listen(sessionId: String?) {
        if (sessionId == null) {
            listenSession = null
            listenJob?.cancel()
            listenJob = null
            _hostSpeaking.value = false
            stopPlayback()
            return
        }
        if (listenSession == sessionId && listenJob?.isActive == true) return
        listenSession = sessionId
        listenJob?.cancel()
        _hostSpeaking.value = false
        listenJob = scope.launch {
            var after = 0
            var caughtUp = false
            while (isActive && listenSession == sessionId) {
                val poll = runCatching { api.pollVoice(sessionId, after, live = !caughtUp) }.getOrNull()
                if (poll != null && !caughtUp) {
                    after = poll.optInt("latestSeq")
                    caughtUp = true
                    _hostSpeaking.value = poll.optBoolean("speaking")
                    delay(1_000)
                    continue
                }
                if (poll != null) {
                    _hostSpeaking.value = poll.optBoolean("speaking")
                    val clips = poll.optJSONArray("clips")
                    if (clips != null) {
                        for (i in 0 until clips.length()) {
                            val clip = clips.getJSONObject(i)
                            val clipSeq = clip.optInt("seq")
                            if (clipSeq > after) after = clipSeq
                            val encoded = clip.optString("audioBase64")
                            if (encoded.isNotBlank()) enqueue(encoded)
                        }
                    }
                }
                delay(1_000)
            }
        }
    }

    fun close() {
        holding.set(false)
        listen(null)
        recordJob?.cancel()
        main.post { releasePlayer() }
    }

    private fun openMic(): AudioRecord? {
        val rate = 16_000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return null
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            rate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            min * 2
        )
        return recorder.takeIf { it.state == AudioRecord.STATE_INITIALIZED }
    }

    private fun wav(pcm: ByteArray): ByteArray {
        val rate = 16_000
        val header = ByteArray(44)
        val total = 36 + pcm.size
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        writeLe(header, 4, total)
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        writeLe(header, 16, 16)
        header[20] = 1; header[21] = 0
        header[22] = 1; header[23] = 0
        writeLe(header, 24, rate)
        writeLe(header, 28, rate * 2)
        header[32] = 2; header[33] = 0
        header[34] = 16; header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        writeLe(header, 40, pcm.size)
        return header + pcm
    }

    private fun writeLe(into: ByteArray, at: Int, value: Int) {
        into[at] = (value and 0xff).toByte()
        into[at + 1] = (value shr 8 and 0xff).toByte()
        into[at + 2] = (value shr 16 and 0xff).toByte()
        into[at + 3] = (value shr 24 and 0xff).toByte()
    }

    private fun enqueue(base64: String) {
        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
        if (bytes.isEmpty()) return
        val wav = bytes.size > 4 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte()
        val file = File.createTempFile("hall-hear-", if (wav) ".wav" else ".m4a", appContext.cacheDir)
        file.writeBytes(bytes)
        main.post {
            synchronized(playerLock) {
                queue.addLast(file)
                if (!playing) playNextLocked()
            }
        }
    }

    private fun playNextLocked() {
        val file = queue.pollFirst()
        if (file == null) {
            playing = false
            return
        }
        playing = true
        currentFile = file
        val exo = player ?: ExoPlayer.Builder(appContext).build().also { created ->
            created.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        synchronized(playerLock) {
                            currentFile?.delete()
                            currentFile = null
                            playNextLocked()
                        }
                    }
                }
            })
            player = created
        }
        exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        exo.prepare()
        exo.play()
    }

    private fun stopPlayback() {
        main.post {
            synchronized(playerLock) {
                queue.forEach { it.delete() }
                queue.clear()
                currentFile?.delete()
                currentFile = null
                playing = false
                player?.stop()
                player?.clearMediaItems()
            }
        }
    }

    private fun releasePlayer() {
        synchronized(playerLock) {
            queue.forEach { it.delete() }
            queue.clear()
            currentFile?.delete()
            currentFile = null
            playing = false
            player?.release()
            player = null
        }
    }
}

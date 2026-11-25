package com.digital.sanghaworld.hall

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
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
            try {
                runCatching { api.postVoice(sessionId, seq = 0, ended = false, bytes = ByteArray(0)) }
                while (isActive && holding.get()) {
                    val bytes = recordClip(2_000L) ?: break
                    if (bytes.isEmpty()) continue
                    val n = seq.incrementAndGet()
                    runCatching { api.postVoice(sessionId, seq = n, ended = false, bytes = bytes) }
                }
                val n = seq.incrementAndGet()
                runCatching { api.postVoice(sessionId, seq = n, ended = true, bytes = ByteArray(0)) }
            } finally {
                holding.set(false)
                _micOpen.value = false
            }
        }
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
            while (isActive && listenSession == sessionId) {
                val poll = runCatching { api.pollVoice(sessionId, after) }.getOrNull()
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

    private fun recordClip(maxMs: Long): ByteArray? {
        val file = File.createTempFile("hall-voice-", ".m4a", appContext.cacheDir)
        val recorder = newRecorder()
        return try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(24_000)
            recorder.setAudioSamplingRate(16_000)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            val started = System.currentTimeMillis()
            while (holding.get() && System.currentTimeMillis() - started < maxMs) {
                Thread.sleep(50)
            }
            recorder.stop()
            file.readBytes()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { recorder.release() }
            file.delete()
        }
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(appContext) else @Suppress("DEPRECATION") MediaRecorder()

    private fun enqueue(base64: String) {
        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
        if (bytes.isEmpty()) return
        val file = File.createTempFile("hall-hear-", ".m4a", appContext.cacheDir)
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

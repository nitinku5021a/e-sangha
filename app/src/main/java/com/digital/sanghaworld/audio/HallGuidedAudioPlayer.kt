package com.digital.sanghaworld.audio

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.net.CookieHandler
import java.net.CookieManager

object HallGuidedAudioPlayer {
    private var player: ExoPlayer? = null
    private val main = Handler(Looper.getMainLooper())
    private var stopTask: Runnable? = null
    private var muted = false
    private var pendingSeekMs = 0L
    private var triedFallback = false

    fun start(
        context: Context,
        url: String,
        startPositionMs: Long,
        playForMs: Long,
        startMuted: Boolean
    ) {
        stop()
        muted = startMuted
        triedFallback = false
        pendingSeekMs = startPositionMs.coerceAtLeast(0)
        if (url.isBlank() || playForMs <= 0) return
        if (CookieHandler.getDefault() == null) {
            CookieHandler.setDefault(CookieManager())
        }
        val app = context.applicationContext
        val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.requestAudioFocus(
            null,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        val exo = ExoPlayer.Builder(app).build()
        player = exo
        exo.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            false
        )
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (player !== exo || playbackState != Player.STATE_READY) return
                if (pendingSeekMs > 0) {
                    exo.seekTo(pendingSeekMs)
                    pendingSeekMs = 0
                }
                val duration = exo.duration
                val seek = exo.currentPosition.coerceAtLeast(0)
                if (duration > 0 && duration != C.TIME_UNSET && seek >= duration) {
                    stop()
                    return
                }
                val remainingAudio = if (duration > 0 && duration != C.TIME_UNSET) {
                    (duration - seek).coerceAtLeast(0)
                } else {
                    playForMs
                }
                stopTask?.let { main.removeCallbacks(it) }
                val task = Runnable { stop() }
                stopTask = task
                main.postDelayed(task, minOf(playForMs, remainingAudio))
            }

            override fun onPlayerError(error: PlaybackException) {
                if (player !== exo || triedFallback) {
                    stop()
                    return
                }
                val fileId = Regex("id=([a-zA-Z0-9_-]+)").find(url)?.groupValues?.getOrNull(1).orEmpty()
                val alt = DriveAudioCatalog.confirmPlaybackUrl(fileId)
                if (fileId.isBlank() || alt.isBlank()) {
                    stop()
                    return
                }
                triedFallback = true
                open(exo, alt, playWhenReady = true)
            }
        })
        applyVolume(exo)
        open(exo, DriveAudioCatalog.mediaUrl(url), playWhenReady = true)
    }

    private fun open(exo: ExoPlayer, url: String, playWhenReady: Boolean) {
        val dataSource = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android) eSangha")
            .setAllowCrossProtocolRedirects(true)
        val source = ProgressiveMediaSource.Factory(dataSource)
            .createMediaSource(MediaItem.fromUri(url))
        exo.setMediaSource(source)
        exo.prepare()
        exo.playWhenReady = playWhenReady
    }

    fun positionMs(): Int = runCatching {
        player?.currentPosition?.takeIf { it >= 0 }?.toInt() ?: 0
    }.getOrDefault(0)

    fun durationMs(): Int = runCatching {
        val duration = player?.duration ?: return 0
        if (duration <= 0 || duration == C.TIME_UNSET) 0 else duration.toInt()
    }.getOrDefault(0)

    fun seekTo(positionMs: Int) {
        val target = positionMs.coerceAtLeast(0).toLong()
        pendingSeekMs = target
        val exo = player ?: return
        val duration = exo.duration
        val clamped = if (duration > 0 && duration != C.TIME_UNSET) target.coerceIn(0, duration) else target
        pendingSeekMs = 0
        runCatching { exo.seekTo(clamped) }
    }

    fun skipForward(byMs: Int = 15_000) {
        seekTo(positionMs() + byMs)
    }

    fun setMuted(value: Boolean) {
        muted = value
        player?.let { applyVolume(it) }
    }

    fun stop() {
        stopTask?.let { main.removeCallbacks(it) }
        stopTask = null
        pendingSeekMs = 0
        val current = player
        player = null
        if (current != null) {
            runCatching { current.stop() }
            runCatching { current.release() }
        }
    }

    private fun applyVolume(exo: ExoPlayer) {
        runCatching { exo.volume = if (muted) 0f else 1f }
    }
}

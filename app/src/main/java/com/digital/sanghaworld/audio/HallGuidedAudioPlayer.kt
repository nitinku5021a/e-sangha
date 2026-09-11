package com.digital.sanghaworld.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

object HallGuidedAudioPlayer {
    private var player: MediaPlayer? = null
    private val main = Handler(Looper.getMainLooper())
    private var stopTask: Runnable? = null
    private var muted = false

    fun start(
        context: Context,
        url: String,
        startPositionMs: Long,
        playForMs: Long,
        startMuted: Boolean
    ) {
        stop()
        muted = startMuted
        if (url.isBlank() || playForMs <= 0) return
        val app = context.applicationContext
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(url)
            mp.setOnPreparedListener { prepared ->
                if (player !== prepared) {
                    prepared.release()
                    return@setOnPreparedListener
                }
                val duration = prepared.duration.toLong().coerceAtLeast(0)
                val seek = startPositionMs.coerceAtLeast(0)
                if (duration > 0 && seek >= duration) {
                    stop()
                    return@setOnPreparedListener
                }
                if (seek > 0) {
                    runCatching { prepared.seekTo(seek.toInt()) }
                }
                applyVolume(prepared)
                prepared.start()
                val remainingAudio = if (duration > 0) (duration - seek).coerceAtLeast(0) else playForMs
                val stopAfter = minOf(playForMs, remainingAudio)
                val task = Runnable { stop() }
                stopTask = task
                main.postDelayed(task, stopAfter)
            }
            mp.setOnCompletionListener { stop() }
            mp.setOnErrorListener { _, _, _ ->
                val alt = DriveAudioCatalog.confirmPlaybackUrl(
                    Regex("id=([a-zA-Z0-9_-]+)").find(url)?.groupValues?.getOrNull(1).orEmpty()
                )
                if (alt.isNotBlank() && alt != url && player === mp) {
                    runCatching {
                        mp.reset()
                        mp.setDataSource(alt)
                        mp.prepareAsync()
                    }
                    true
                } else {
                    stop()
                    true
                }
            }
            mp.prepareAsync()
        } catch (_: Exception) {
            stop()
        }
    }

    fun setMuted(value: Boolean) {
        muted = value
        player?.let { applyVolume(it) }
    }

    fun stop() {
        stopTask?.let { main.removeCallbacks(it) }
        stopTask = null
        val current = player
        player = null
        if (current != null) {
            runCatching { if (current.isPlaying) current.stop() }
            runCatching { current.release() }
        }
    }

    private fun applyVolume(mp: MediaPlayer) {
        val v = if (muted) 0f else 1f
        runCatching { mp.setVolume(v, v) }
    }
}

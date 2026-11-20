package com.digital.sanghaworld.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer

class GongPreviewPlayer(private val context: Context) {
    private var player: MediaPlayer? = null

    fun play(resId: Int, onComplete: (() -> Unit)? = null) {
        stop()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.requestAudioFocus(
            null,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )
        val created = MediaPlayer.create(context, resId)
        if (created == null) {
            onComplete?.invoke()
            return
        }
        player = created.apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnCompletionListener {
                stop()
                onComplete?.invoke()
            }
            start()
        }
    }

    fun stop() {
        player?.runCatching {
            if (isPlaying) stop()
            release()
        }
        player = null
    }
}

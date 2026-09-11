package com.digital.sanghaworld.audio

import android.media.MediaMetadataRetriever

object AudioDurationProbe {
    fun durationSeconds(url: String): Int? {
        if (url.isBlank()) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(url, HashMap())
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val seconds = ms?.let { ((it + 999) / 1000).toInt() }
            seconds?.takeIf { it > 0 }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}

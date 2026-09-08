package com.digital.sanghaworld.audio

import android.content.Context
import com.digital.sanghaworld.R

data class GongSound(
    val id: String,
    val title: String,
    val description: String,
    val resId: Int
)

object GongCatalog {
    const val DEFAULT_ID = "dhamma"

    /**
     * Add new gongs here after placing the file in:
     * app/src/main/res/raw/
     *
     * File name rules: lowercase, digits, underscores only.
     * Example: gong_temple.mp3 → R.raw.gong_temple
     *
     * One file is enough. Session end plays that file three times
     * with a 3 second interval.
     */
    val all: List<GongSound> = listOf(
        GongSound(
            id = "dhamma",
            title = "Dhamma",
            description = "Dhamma gong; end of sit plays it three times",
            resId = R.raw.dhamma_gong
        ),
        GongSound(
            id = "classic",
            title = "Classic",
            description = "Single strike; end of sit plays it three times",
            resId = R.raw.gong
        ),
        GongSound(
            id = "temple",
            title = "Temple",
            description = "A deeper temple gong",
            resId = R.raw.temple_gong
        )
    )

    fun byId(id: String): GongSound {
        val mapped = when (id) {
            "classic_start", "classic_end" -> DEFAULT_ID
            else -> id
        }
        return all.find { it.id == mapped } ?: all.first { it.id == DEFAULT_ID }
    }
}

object GongPreferences {
    private const val PREFS = "vipassana_settings"
    private const val KEY_GONG_ID = "gong_sound_id"

    fun selectedId(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_GONG_ID, GongCatalog.DEFAULT_ID)
            ?: GongCatalog.DEFAULT_ID
    }

    fun selectedSound(context: Context): GongSound {
        return GongCatalog.byId(selectedId(context))
    }

    fun setSelectedId(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GONG_ID, id)
            .apply()
    }
}

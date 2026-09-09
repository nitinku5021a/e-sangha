package com.digital.sanghaworld.hall.filament

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import com.google.android.filament.Engine
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.android.TextureHelper

class HallTextures(context: Context, engine: Engine) {
    val woodFloor = load(context, engine, "hall/wood_floor.jpg")
    val woodWall = load(context, engine, "hall/wood_wall.jpg")
    val plaster = load(context, engine, "hall/plaster.jpg")
    val beige = load(context, engine, "hall/fabric_beige.jpg")
    val navy = load(context, engine, "hall/fabric_navy.jpg")
    val tatami = load(context, engine, "hall/tatami.jpg")
    val robe = load(context, engine, "hall/fabric_robe.jpg")
    val sampler = TextureSampler(TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.REPEAT)

    private fun load(context: Context, engine: Engine, path: String): Texture {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = context.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("Missing texture $path")
        val tex = Texture.Builder()
            .width(bmp.width)
            .height(bmp.height)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .format(Texture.InternalFormat.SRGB8_A8)
            .levels(8)
            .build(engine)
        TextureHelper.setBitmap(engine, tex, 0, bmp)
        tex.generateMipmaps(engine)
        return tex
    }

    fun destroy(engine: Engine) {
        listOf(woodFloor, woodWall, plaster, beige, navy, tatami, robe).forEach { engine.destroyTexture(it) }
    }
}

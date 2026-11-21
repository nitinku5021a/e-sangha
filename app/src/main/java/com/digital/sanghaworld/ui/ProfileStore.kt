package com.digital.sanghaworld.ui

import android.content.Context
import android.net.Uri
import java.io.File

object ProfileStore {
    fun photoFile(context: Context): File = File(context.filesDir, "profile_photo.jpg")

    fun savePhoto(context: Context, uri: Uri) {
        val dest = photoFile(context)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }

    fun clearPhoto(context: Context) {
        photoFile(context).delete()
    }
}

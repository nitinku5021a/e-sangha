package com.digital.sanghaworld.audio

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.regex.Pattern

data class DriveAudioFile(
    val id: String?,
    val name: String,
    val playbackUrl: String
)

object DriveAudioCatalog {
    private val AUDIO_EXT = Regex("\\.(mp3|wav|m4a|aac|ogg|oga|flac|opus|wma)$", RegexOption.IGNORE_CASE)
    private val FILE_ID = Pattern.compile("/file/d/([a-zA-Z0-9_-]+)")
    private val FOLDER_ID = Pattern.compile("/(?:drive/)?(?:u/\\d+/)?folders/([a-zA-Z0-9_-]+)")
    private val OPEN_ID = Pattern.compile("[?&]id=([a-zA-Z0-9_-]+)")
    private val HREF_FILE = Pattern.compile(
        "href=\"(?:https://drive\\.google\\.com)?/file/d/([a-zA-Z0-9_-]+)[^\"]*\"[^>]*>([^<]+)",
        Pattern.CASE_INSENSITIVE
    )
    private val DATA_ID_TITLE = Pattern.compile(
        "data-id=\"([a-zA-Z0-9_-]+)\"[^>]{0,400}?(?:aria-label|data-tooltip|title)=\"([^\"]+)\"",
        Pattern.CASE_INSENSITIVE
    )

    fun playbackUrl(fileId: String): String =
        "https://drive.google.com/uc?export=download&id=$fileId"

    fun confirmPlaybackUrl(fileId: String): String =
        "https://drive.google.com/uc?export=download&confirm=t&id=$fileId"

    fun listAudio(sourceUrl: String): List<DriveAudioFile> {
        val url = sourceUrl.trim()
        if (url.isBlank()) return emptyList()
        if (!isGoogleDrive(url) && looksLikeAudio(url)) {
            return listOf(DriveAudioFile(id = null, name = fileNameFromUrl(url), playbackUrl = url))
        }
        val fileId = extractFileId(url)
        val folderId = extractFolderId(url)
        if (fileId != null && folderId == null) {
            val name = fileNameFromUrl(url).ifBlank { "Selected audio" }
            return listOf(DriveAudioFile(id = fileId, name = name, playbackUrl = playbackUrl(fileId)))
        }
        val id = folderId ?: fileId ?: return emptyList()
        val html = fetch("https://drive.google.com/embeddedfolderview?id=$id")
            ?: fetch("https://drive.google.com/drive/folders/$id?usp=sharing")
            ?: return emptyList()
        val found = LinkedHashMap<String, DriveAudioFile>()
        fun add(fid: String, rawName: String) {
            val name = decode(rawName).trim()
            if (name.isBlank() || !looksLikeAudio(name)) return
            found[fid] = DriveAudioFile(fid, name, playbackUrl(fid))
        }
        val href = HREF_FILE.matcher(html)
        while (href.find()) add(href.group(1), href.group(2))
        val data = DATA_ID_TITLE.matcher(html)
        while (data.find()) add(data.group(1), data.group(2))
        val jsonIds = Regex("\"id\"\\s*:\\s*\"([a-zA-Z0-9_-]{10,})\"").findAll(html)
        val jsonNames = Regex("\"(?:title|name)\"\\s*:\\s*\"([^\"]+)\"").findAll(html).map { decode(it.groupValues[1]) }.toList()
        jsonIds.forEachIndexed { index, match ->
            val name = jsonNames.getOrNull(index) ?: return@forEachIndexed
            add(match.groupValues[1], name)
        }
        return found.values.toList()
    }

    private fun isGoogleDrive(url: String): Boolean =
        url.contains("drive.google.com", ignoreCase = true) || url.contains("docs.google.com", ignoreCase = true)

    private fun looksLikeAudio(name: String): Boolean = AUDIO_EXT.containsMatchIn(name)

    private fun extractFileId(url: String): String? {
        val m = FILE_ID.matcher(url)
        if (m.find()) return m.group(1)
        if (url.contains("/folders/")) return null
        val o = OPEN_ID.matcher(url)
        return if (o.find()) o.group(1) else null
    }

    private fun extractFolderId(url: String): String? {
        val m = FOLDER_ID.matcher(url)
        return if (m.find()) m.group(1) else null
    }

    private fun fileNameFromUrl(url: String): String {
        val path = url.substringBefore('?').substringAfterLast('/')
        return decode(path).ifBlank { "Audio" }
    }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value.replace("&amp;", "&").replace("\\u0027", "'"), "UTF-8")
    }.getOrDefault(value)

    private fun fetch(url: String): String? {
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 12_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android) eSangha")
                setRequestProperty("Accept", "text/html")
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        }.getOrNull()
    }
}

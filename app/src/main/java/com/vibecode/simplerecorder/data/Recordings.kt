package com.vibecode.simplerecorder.data

import android.content.Context
import android.media.MediaMetadataRetriever
import com.vibecode.simplerecorder.R
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Recording(
    val file: File,
    val durationMs: Long,
    val dateMs: Long,
    val displayName: String,
    val width: Int = 0,
    val height: Int = 0,
) {
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height else DEFAULT_ASPECT_RATIO

    private companion object {
        const val DEFAULT_ASPECT_RATIO = 16f / 9f
    }
}

private data class VideoMetadata(val durationMs: Long, val width: Int, val height: Int)

class Recordings(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "recordings")

    fun stagingFile(): File {
        pruneStaleStagingFilesOnce()
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Recording directory unavailable")
        return File.createTempFile("pending-", ".tmp", directory)
    }

    fun publish(staging: File): Recording {
        val video = metadata(staging)
        require(video.durationMs > 0)
        val base = appContext.getString(
            R.string.recording_default_name,
            timestamp(staging.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()),
        )
        val output = uniqueFile(base)
        if (!staging.renameTo(output)) throw IOException("Could not publish recording")
        return Recording(
            output,
            video.durationMs,
            output.lastModified(),
            output.nameWithoutExtension,
            video.width,
            video.height,
        )
    }

    fun list(): List<Recording> {
        pruneStaleStagingFilesOnce()
        return directory.listFiles { file -> file.isFile && file.extension == "mp4" }
            .orEmpty().mapNotNull(::readRecording).sortedByDescending { it.dateMs }
    }

    fun findByName(name: String): Recording? {
        if (!name.endsWith(".mp4") || File(name).name != name) return null
        val file = File(directory, name)
        return if (file.isFile) readRecording(file) else null
    }

    fun rename(recording: Recording, name: String): Recording {
        val base = name.trim().removeSuffix(".mp4").trim()
        require(base.isNotEmpty() && base.length <= 80 && base.first() != '.' && base.none(::isInvalidNameChar)) {
            "Invalid recording name"
        }
        val target = File(directory, "$base.mp4")
        if (target.canonicalPath == recording.file.canonicalPath) return recording.copy(displayName = base)
        if (target.exists()) throw IOException("A recording with that name already exists")
        if (!recording.file.renameTo(target)) throw IOException("Could not rename recording")
        return Recording(target, recording.durationMs, recording.dateMs, base)
    }

    private fun uniqueFile(base: String): File {
        var candidate = File(directory, "$base.mp4")
        var index = 2
        while (candidate.exists()) {
            candidate = File(directory, "${base}_$index.mp4")
            index++
        }
        return candidate
    }

    private fun readRecording(file: File): Recording? = runCatching {
        val video = metadata(file)
        val dateMs = file.lastModified()
        Recording(file, video.durationMs, dateMs, displayNameFor(file, dateMs), video.width, video.height)
    }.getOrNull()

    private fun displayNameFor(file: File, dateMs: Long): String {
        val base = file.nameWithoutExtension
        return if (UUID_NAME.matches(base)) {
            appContext.getString(R.string.recording_default_name, timestamp(dateMs))
        } else base
    }

    private fun timestamp(ms: Long) = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(ms))

    private fun isInvalidNameChar(char: Char) = char in ILLEGAL_NAME_CHARS || char.code < 32

    private fun pruneStaleStagingFilesOnce() = synchronized(stagingCleanupLock) {
        if (stagingCleanupDone) return@synchronized
        // Every staging allocation passes through this gate before creating a file.
        val cutoff = System.currentTimeMillis() - STAGING_MAX_AGE_MS
        directory.listFiles { _, name -> name.startsWith("pending-") && name.endsWith(".tmp") }
            ?.forEach { file ->
                val modified = file.lastModified()
                if (modified > 0 && modified < cutoff && file.isFile) file.delete()
            }
        stagingCleanupDone = true
    }

    fun remove(recording: Recording): Boolean = recording.file.delete() || !recording.file.exists()

    fun durationMs(file: File): Long = metadata(file).durationMs

    private fun metadata(file: File): VideoMetadata {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            check(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes")
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: throw IOException("Recording duration unavailable")
            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val swap = rotation == 90 || rotation == 270
            return VideoMetadata(
                durationMs = duration,
                width = if (swap) rawHeight else rawWidth,
                height = if (swap) rawWidth else rawHeight,
            )
        } finally {
            retriever.release()
        }
    }

    private companion object {
        const val STAGING_MAX_AGE_MS = 24 * 60 * 60 * 1_000L
        const val ILLEGAL_NAME_CHARS = "\\/:*?\"<>|"
        val UUID_NAME = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        val stagingCleanupLock = Any()
        var stagingCleanupDone = false
    }
}

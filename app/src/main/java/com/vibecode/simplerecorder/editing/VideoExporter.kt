package com.vibecode.simplerecorder.editing

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
object VideoExporter {
    suspend fun trim(
        context: Context,
        input: File,
        output: File,
        startMs: Long,
        endMs: Long,
        progress: (Int) -> Unit = {},
    ) = withContext(Dispatchers.Main.immediate) {
        coroutineScope {
            val transformer = Transformer.Builder(context.applicationContext).build()
            val progressJob = launch {
                val holder = ProgressHolder()
                while (true) {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) progress(holder.progress)
                    delay(200)
                }
            }
            try {
                suspendCancellableCoroutine { continuation ->
                    transformer.addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (continuation.isActive) continuation.resumeWithException(exportException)
                        }
                    })
                    val item = MediaItem.Builder().setUri(Uri.fromFile(input)).setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build(),
                    ).build()
                    if (continuation.isActive) transformer.start(EditedMediaItem.Builder(item).build(), output.absolutePath)
                }
            } finally {
                progressJob.cancel()
                transformer.cancel()
            }
        }
    }
}

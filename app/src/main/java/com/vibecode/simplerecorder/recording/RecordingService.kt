package com.vibecode.simplerecorder.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import com.vibecode.simplerecorder.MainActivity
import com.vibecode.simplerecorder.R
import com.vibecode.simplerecorder.data.Recording
import com.vibecode.simplerecorder.data.Recordings
import com.vibecode.simplerecorder.editing.VideoExporter
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

sealed interface CaptureState {
    data object Idle : CaptureState
    data object Starting : CaptureState
    data class Active(val elapsedMs: Long, val microphone: Boolean) : CaptureState
    data object Saving : CaptureState
    data class Saved(val recording: Recording, val reachedLimit: Boolean) : CaptureState
    data class Failed(val message: Int) : CaptureState
}

class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { Recordings(this) }
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var staging: File? = null
    private var captureJob: Job? = null
    private var started = false
    private var finishing = false
    private var finishedState: CaptureState? = null
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() = finishCapture()
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> if (!finishing) {
                if (projection != null) finishCapture(cancelledBeforeStart = !started) else stopSelf()
            }
            ACTION_START -> if (staging == null && projection == null && !finishing) startCapture(intent)
            else -> if (!started && !finishing) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startCapture(intent: Intent) {
        stateHolder.value = CaptureState.Starting
        val microphone = intent.getBooleanExtra(EXTRA_MICROPHONE, false)
        try {
            val consent = requireNotNull(IntentCompat.getParcelableExtra(intent, EXTRA_CONSENT, Intent::class.java))
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
            )
            val types = (if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0) or
                (if (microphone && Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
            ServiceCompat.startForeground(this, ONGOING_ID, ongoing(microphone, false), types)
            val capture = requireNotNull(getSystemService(MediaProjectionManager::class.java).getMediaProjection(
                intent.getIntExtra(EXTRA_RESULT, 0), consent,
            ))
            projection = capture
            val main = Handler(Looper.getMainLooper())
            capture.registerCallback(projectionCallback, main)
            captureJob = scope.launch {
                try {
                    val file = withContext(NonCancellable) {
                        val candidate = withContext(Dispatchers.IO) { repository.stagingFile() }
                        if (finishing || !scope.isActive) {
                            withContext(Dispatchers.IO) { candidate.delete() }
                            null
                        } else candidate.also { staging = it }
                    } ?: return@launch
                    val (width, height) = captureSize()
                    @Suppress("DEPRECATION")
                    val encoder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this@RecordingService) else MediaRecorder()
                    recorder = encoder
                    encoder.apply {
                        if (microphone) setAudioSource(MediaRecorder.AudioSource.MIC)
                        setVideoSource(MediaRecorder.VideoSource.SURFACE)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                        setVideoSize(width, height)
                        setVideoFrameRate(30)
                        setVideoEncodingBitRate(4_000_000)
                        if (microphone) {
                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                            setAudioChannels(1)
                            setAudioEncodingBitRate(96_000)
                            setAudioSamplingRate(44_100)
                        }
                        setMaxDuration(MAX_MS.toInt())
                        setOutputFile(file.absolutePath)
                        setOnInfoListener { _, what, _ ->
                            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) finishCapture(reachedLimit = true)
                        }
                        setOnErrorListener { _, _, _ -> finishCapture(error = R.string.recording_failed) }
                        prepare()
                    }
                    display = requireNotNull(capture.createVirtualDisplay(
                        "SimpleRecorder", width, height, resources.configuration.densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, encoder.surface, null, main,
                    ))
                    encoder.start()
                    started = true
                    val startAt = SystemClock.elapsedRealtime()
                    var warned = false
                    while (started && !finishing) {
                        val elapsed = SystemClock.elapsedRealtime() - startAt
                        if (elapsed >= MAX_MS) {
                            finishCapture(reachedLimit = true)
                            break
                        }
                        stateHolder.value = CaptureState.Active(elapsed, microphone)
                        if (!warned && elapsed >= MAX_MS - 30_000) {
                            getSystemService(NotificationManager::class.java).notify(ONGOING_ID, ongoing(microphone, true))
                            warned = true
                        }
                        delay(minOf(200L, MAX_MS - elapsed))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    finishCapture(error = if (microphone) R.string.microphone_start_failed else R.string.recording_failed)
                }
            }
        } catch (_: Exception) {
            finishCapture(error = if (microphone) R.string.microphone_start_failed else R.string.recording_failed)
        }
    }

    private fun captureSize(): Pair<Int, Int> {
        val window = getSystemService(WindowManager::class.java)
        @Suppress("DEPRECATION")
        val bounds = if (Build.VERSION.SDK_INT >= 30) window.maximumWindowMetrics.bounds else {
            val point = android.graphics.Point()
            window.defaultDisplay.getRealSize(point)
            android.graphics.Rect(0, 0, point.x, point.y)
        }
        val scale = minOf(1.0, 720.0 / minOf(bounds.width(), bounds.height()), 1280.0 / maxOf(bounds.width(), bounds.height()))
        fun aligned(pixels: Int) = ((pixels * scale / 2).roundToInt() * 2).coerceAtLeast(2)
        return aligned(bounds.width()) to aligned(bounds.height())
    }

    private fun finishCapture(reachedLimit: Boolean = false, error: Int? = null, cancelledBeforeStart: Boolean = false) {
        if (finishing) return
        finishing = true
        stateHolder.value = CaptureState.Saving
        val encoder = recorder.also { recorder = null }
        val file = staging.also { staging = null }
        val wasStarted = started.also { started = false }
        scope.launch {
            var trimmed: File? = null
            try {
                val encoderStopped = withContext(NonCancellable + Dispatchers.IO) {
                    try {
                        wasStarted && runCatching { encoder?.stop() }.isSuccess
                    } finally {
                        runCatching { encoder?.release() }
                    }
                }
                releaseProjection()
                if (!wasStarted) captureJob?.join()
                if (cancelledBeforeStart) {
                    finishedState = CaptureState.Idle
                    return@launch
                }
                // The platform cap may stop the recorder before the app receives its callback.
                // In that case validate the finalized file rather than relying on a second stop().
                check((encoderStopped || (wasStarted && reachedLimit)) && file != null && error == null)
                val duration = withContext(Dispatchers.IO) { repository.durationMs(file) }
                val output = if (duration > MAX_MS) {
                    val corrected = withContext(Dispatchers.IO) { repository.stagingFile().also { trimmed = it } }
                    // Leave headroom for frame and audio-sample alignment in the exported MP4.
                    VideoExporter.trim(this@RecordingService, file, corrected, 0, MAX_MS - 500)
                    check(withContext(Dispatchers.IO) { repository.durationMs(corrected) } <= MAX_MS)
                    corrected
                } else file
                val recording = withContext(Dispatchers.IO) { repository.publish(output) }
                val atLimit = reachedLimit || duration >= MAX_MS
                finishedState = CaptureState.Saved(recording, atLimit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val message = error ?: if (wasStarted) R.string.save_failed else R.string.recording_interrupted
                finishedState = CaptureState.Failed(message)
            } finally {
                releaseProjection()
                withContext(NonCancellable + Dispatchers.IO) {
                    file?.delete()
                    trimmed?.delete()
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun releaseProjection() {
        runCatching { display?.release() }
        display = null
        projection?.let {
            it.unregisterCallback(projectionCallback)
            runCatching { it.stop() }
        }
        projection = null
    }

    private fun ongoing(microphone: Boolean, warning: Boolean): Notification = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_recording)
        .setContentTitle(getString(if (microphone) R.string.recording_with_mic else R.string.recording_active))
        .setContentText(getString(if (warning) R.string.limit_warning else R.string.notification_hint))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(openApp())
        .addAction(R.drawable.ic_recording, getString(R.string.stop_recording), PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )).build()

    private fun openApp(recording: Recording? = null): PendingIntent = PendingIntent.getActivity(
        this, if (recording == null) 2 else 3,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN, recording?.file?.name), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun postResult(message: Int, recording: Recording? = null) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        runCatching {
            getSystemService(NotificationManager::class.java).notify(RESULT_ID, NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_recording).setContentTitle(getString(message))
                .setAutoCancel(true).setContentIntent(openApp(recording)).build())
        }
    }

    override fun onDestroy() {
        scope.cancel()
        releaseProjection()
        runCatching { recorder?.release() }
        staging?.let { file -> CoroutineScope(Dispatchers.IO).launch { file.delete() } }
        super.onDestroy()
        // Unlock capture only once the old service instance has finished shutting down.
        when (val result = finishedState) {
            is CaptureState.Saved -> {
                stateHolder.value = result
                postResult(if (result.reachedLimit) R.string.limit_saved else R.string.recording_saved, result.recording)
            }
            is CaptureState.Failed -> {
                stateHolder.value = result
                postResult(result.message)
            }
            CaptureState.Idle -> stateHolder.value = CaptureState.Idle
            else -> if (stateHolder.value is CaptureState.Active || stateHolder.value == CaptureState.Starting || stateHolder.value == CaptureState.Saving) {
                stateHolder.value = CaptureState.Failed(R.string.recording_interrupted)
            }
        }
    }

    companion object {
        const val MAX_MS = 300_000L
        const val ACTION_START = "com.vibecode.simplerecorder.START"
        const val ACTION_STOP = "com.vibecode.simplerecorder.STOP"
        const val EXTRA_CONSENT = "consent"
        const val EXTRA_MICROPHONE = "microphone"
        const val EXTRA_RESULT = "result"
        const val EXTRA_OPEN = "open_recording"
        private const val CHANNEL = "recording"
        private const val ONGOING_ID = 101
        private const val RESULT_ID = 102
        private val stateHolder = MutableStateFlow<CaptureState>(CaptureState.Idle)
        val state = stateHolder.asStateFlow()
    }
}

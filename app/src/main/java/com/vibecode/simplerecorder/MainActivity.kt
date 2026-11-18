package com.vibecode.simplerecorder

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.vibecode.simplerecorder.data.NotificationPreferences
import com.vibecode.simplerecorder.data.Recording
import com.vibecode.simplerecorder.recording.RecordingService
import com.vibecode.simplerecorder.ui.RecorderApp
import com.vibecode.simplerecorder.ui.RecorderViewModel
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[RecorderViewModel::class.java] }
    private var pendingMicrophone = false
    private var showMicrophoneChoice = androidx.compose.runtime.mutableStateOf(false)
    private val countdown = androidx.compose.runtime.mutableStateOf<Int?>(null)
    private var countdownJob: Job? = null
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchProjection() else showMicrophoneChoice.value = true
    }
    private val notificationsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        updateNotificationAvailability()
        requestMicrophoneIfNeeded()
    }
    private val projectionConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val consent = result.data
        if (result.resultCode == RESULT_OK && consent != null) {
            val seconds = model.state.value.countdownSeconds
            if (seconds <= 0) {
                startCapture(result.resultCode, consent)
            } else {
                countdownJob = lifecycleScope.launch {
                    for (remaining in seconds downTo 1) {
                        countdown.value = remaining
                        delay(1_000)
                    }
                    countdown.value = null
                    startCapture(result.resultCode, consent)
                }
            }
        } else model.message(R.string.consent_cancelled)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingMicrophone = savedInstanceState?.getBoolean("pending_microphone") ?: false
        showMicrophoneChoice.value = savedInstanceState?.getBoolean("microphone_dialog") ?: false
        enableEdgeToEdge()
        if (savedInstanceState == null) openFromIntent(intent)
        setContent {
            val ui by model.state.collectAsStateWithLifecycle()
            RecorderApp(
                ui = ui,
                model = model,
                start = ::beginCapture,
                stop = { startService(Intent(this, RecordingService::class.java).setAction(RecordingService.ACTION_STOP)) },
                share = ::share,
                microphoneDenied = showMicrophoneChoice.value,
                continueWithoutMicrophone = {
                    showMicrophoneChoice.value = false
                    pendingMicrophone = false
                    model.setMicrophone(false)
                    launchProjection()
                },
                cancelMicrophoneChoice = { showMicrophoneChoice.value = false },
                countdown = countdown.value,
                cancelCountdown = ::cancelCountdown,
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("pending_microphone", pendingMicrophone)
        outState.putBoolean("microphone_dialog", showMicrophoneChoice.value)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        updateNotificationAvailability()
    }

    override fun onStop() {
        countdownJob?.cancel()
        countdownJob = null
        countdown.value = null
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFromIntent(intent)
    }

    private fun openFromIntent(intent: Intent) {
        intent.getStringExtra(RecordingService.EXTRA_OPEN)?.let { model.openName(it) }
    }

    private fun updateNotificationAvailability() {
        model.setNotificationsOff(!NotificationManagerCompat.from(this).areNotificationsEnabled())
    }

    private fun beginCapture() {
        if (countdown.value != null || countdownJob?.isActive == true) return
        pendingMicrophone = model.state.value.microphone
        lifecycleScope.launch {
            val prompt = Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS) &&
                try { NotificationPreferences(this@MainActivity).shouldPrompt() } catch (_: IOException) { true }
            if (prompt) requestNotifications() else requestMicrophoneIfNeeded()
        }
    }

    @RequiresApi(33)
    private fun requestNotifications() = notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

    private fun requestMicrophoneIfNeeded() {
        if (pendingMicrophone && !hasPermission(Manifest.permission.RECORD_AUDIO)) {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else launchProjection()
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun launchProjection() {
        projectionConsent.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }

    private fun startCapture(resultCode: Int, consent: Intent) {
        val command = Intent(this, RecordingService::class.java).setAction(RecordingService.ACTION_START)
            .putExtra(RecordingService.EXTRA_RESULT, resultCode)
            .putExtra(RecordingService.EXTRA_CONSENT, consent)
            .putExtra(RecordingService.EXTRA_MICROPHONE, pendingMicrophone)
        lifecycleScope.launch {
            lifecycle.withResumed {
                runCatching { ContextCompat.startForegroundService(this@MainActivity, command) }
                    .onFailure { model.message(R.string.recording_failed) }
            }
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        countdown.value = null
        model.message(R.string.recording_cancelled)
    }

    private fun share(recording: Recording) {
        runCatching {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", recording.file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(recording.file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, getString(R.string.share)))
        }.onFailure { model.message(R.string.share_failed) }
    }
}

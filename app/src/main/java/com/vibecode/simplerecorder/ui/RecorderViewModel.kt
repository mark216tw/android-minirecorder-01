package com.vibecode.simplerecorder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vibecode.simplerecorder.R
import com.vibecode.simplerecorder.data.AppPreferences
import com.vibecode.simplerecorder.data.Recording
import com.vibecode.simplerecorder.data.Recordings
import com.vibecode.simplerecorder.data.ThemeMode
import com.vibecode.simplerecorder.editing.VideoExporter
import com.vibecode.simplerecorder.recording.CaptureState
import com.vibecode.simplerecorder.recording.RecordingService
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecorderUiState(
    val recordings: List<Recording>? = null,
    val openingRecording: Boolean = false,
    val microphone: Boolean = false,
    val capture: CaptureState = CaptureState.Idle,
    val selected: Recording? = null,
    val editing: Boolean = false,
    val range: ClosedFloatingPointRange<Float> = 0f..0f,
    val exportProgress: Int? = null,
    val message: Int? = null,
    val notificationsOff: Boolean = false,
    val showSettings: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val countdownSeconds: Int = 0,
    val dynamicColor: Boolean = false,
)

class RecorderViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = Recordings(application)
    private val preferences = AppPreferences(application)
    private val stateHolder = MutableStateFlow(RecorderUiState())
    val state = stateHolder.asStateFlow()
    private var exportJob: Job? = null
    private var refreshJob: Job? = null
    private var openNameJob: Job? = null

    init {
        viewModelScope.launch {
            preferences.settings.collect { settings ->
                stateHolder.update {
                    it.copy(
                        themeMode = settings.themeMode,
                        countdownSeconds = settings.countdownSeconds,
                        dynamicColor = settings.dynamicColor,
                    )
                }
            }
        }
        viewModelScope.launch {
            RecordingService.state.collect { capture ->
                val previous = state.value.capture
                stateHolder.update { it.copy(capture = capture) }
                when (capture) {
                    is CaptureState.Saved -> {
                        if (refreshJob != null) refresh()
                        if (previous == CaptureState.Saving || previous is CaptureState.Active) open(capture.recording)
                        message(if (capture.reachedLimit) R.string.limit_saved else R.string.recording_saved)
                    }
                    is CaptureState.Failed -> message(capture.message)
                    else -> Unit
                }
            }
        }
    }

    fun ensureLibraryLoaded() {
        if (state.value.recordings == null && refreshJob?.isActive != true) refresh()
    }

    private fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val files = withContext(Dispatchers.IO) { repository.list() }
            stateHolder.update { it.copy(recordings = files) }
        }
    }

    fun setMicrophone(value: Boolean) = stateHolder.update { it.copy(microphone = value) }
    fun setNotificationsOff(value: Boolean) = stateHolder.update { it.copy(notificationsOff = value) }
    fun message(value: Int?) = stateHolder.update { it.copy(message = value) }

    fun openSettings() = stateHolder.update { it.copy(showSettings = true) }

    fun setThemeMode(mode: ThemeMode) {
        stateHolder.update { it.copy(themeMode = mode) }
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setCountdownSeconds(seconds: Int) {
        stateHolder.update { it.copy(countdownSeconds = seconds) }
        viewModelScope.launch { preferences.setCountdownSeconds(seconds) }
    }

    fun setDynamicColor(enabled: Boolean) {
        stateHolder.update { it.copy(dynamicColor = enabled) }
        viewModelScope.launch { preferences.setDynamicColor(enabled) }
    }

    fun rename(recording: Recording, name: String) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { runCatching { repository.rename(recording, name) } }
        result.onSuccess { renamed ->
            stateHolder.update {
                if (it.selected?.file == recording.file) it.copy(selected = renamed) else it
            }
            refresh()
            message(R.string.rename_saved)
        }.onFailure {
            message(if (it is IllegalArgumentException) R.string.rename_invalid else R.string.rename_failed)
        }
    }

    fun openName(name: String) {
        openNameJob?.cancel()
        stateHolder.update { it.copy(openingRecording = true) }
        openNameJob = viewModelScope.launch {
            val recording = withContext(Dispatchers.IO) { repository.findByName(name) }
            if (recording == null) message(R.string.recording_missing) else if (exportJob?.isActive != true) open(recording)
            stateHolder.update { it.copy(openingRecording = false) }
        }
    }

    fun open(recording: Recording) = stateHolder.update {
        it.copy(selected = recording, editing = false, showSettings = false, range = 0f..recording.durationMs.toFloat())
    }

    fun back() = stateHolder.update {
        when {
            it.exportProgress != null -> it
            it.showSettings -> it.copy(showSettings = false)
            it.editing -> it.copy(editing = false)
            else -> it.copy(selected = null)
        }
    }

    fun startEditing() = stateHolder.update { it.copy(editing = true) }
    fun setRange(range: ClosedFloatingPointRange<Float>) = stateHolder.update { it.copy(range = range) }

    fun delete(recording: Recording) = viewModelScope.launch {
        if (withContext(Dispatchers.IO) { repository.remove(recording) }) {
            stateHolder.update { if (it.selected == recording) it.copy(selected = null, editing = false) else it }
            refresh()
            message(R.string.recording_deleted)
        } else message(R.string.delete_failed)
    }

    fun export() {
        val current = state.value
        val recording = current.selected ?: return
        if (exportJob?.isActive == true || current.range.endInclusive - current.range.start < 1) return
        stateHolder.update { it.copy(exportProgress = 0) }
        exportJob = viewModelScope.launch {
            var staging: File? = null
            try {
                val output = withContext(Dispatchers.IO) { repository.stagingFile().also { staging = it } }
                VideoExporter.trim(getApplication(), recording.file, output, current.range.start.toLong(), current.range.endInclusive.toLong()) { progress ->
                    stateHolder.update { it.copy(exportProgress = progress) }
                }
                val copy = withContext(Dispatchers.IO) { repository.publish(output) }
                open(copy)
                refresh()
                message(R.string.copy_saved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message(R.string.export_failed)
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { staging?.delete() }
                stateHolder.update { it.copy(exportProgress = null) }
            }
        }
    }

    fun cancelExport() { exportJob?.cancel() }
}

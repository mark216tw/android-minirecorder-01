package com.vibecode.simplerecorder.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.graphics.scale
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.vibecode.simplerecorder.BuildConfig
import com.vibecode.simplerecorder.R
import com.vibecode.simplerecorder.data.AppPreferences
import com.vibecode.simplerecorder.data.Recording
import com.vibecode.simplerecorder.data.ThemeMode
import com.vibecode.simplerecorder.recording.CaptureState
import com.vibecode.simplerecorder.recording.RecordingService
import com.vibecode.simplerecorder.ui.theme.RecorderBlue
import com.vibecode.simplerecorder.ui.theme.RecorderBlueDark
import com.vibecode.simplerecorder.ui.theme.RecorderTypography
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderApp(
    ui: RecorderUiState,
    model: RecorderViewModel,
    start: () -> Unit,
    stop: () -> Unit,
    share: (Recording) -> Unit,
    microphoneDenied: Boolean,
    continueWithoutMicrophone: () -> Unit,
    cancelMicrophoneChoice: () -> Unit,
    countdown: Int?,
    cancelCountdown: () -> Unit,
) {
    val snackbars = remember { SnackbarHostState() }
    val context = LocalContext.current
    val selected = ui.selected
    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbars.showSnackbar(context.getString(it))
            model.message(null)
        }
    }
    BackHandler(selected != null || ui.showSettings) { model.back() }
    BackHandler(countdown != null) { cancelCountdown() }
    RecorderTheme(ui.themeMode, ui.dynamicColor) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    val title = when {
                        ui.showSettings -> R.string.settings
                        selected == null -> R.string.app_name
                        ui.editing -> R.string.trim
                        else -> R.string.preview_title
                    }
                    TopAppBar(
                        title = { Text(stringResource(title)) },
                        navigationIcon = {
                            if (selected != null || ui.showSettings) {
                                IconButton(onClick = model::back, enabled = ui.exportProgress == null) {
                                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                                }
                            }
                        },
                        actions = {
                            if (selected == null && !ui.showSettings) {
                                IconButton(onClick = model::openSettings) {
                                    Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings))
                                }
                            }
                        },
                    )
                },
                snackbarHost = { SnackbarHost(snackbars) },
            ) { insets ->
                val body = Modifier.fillMaxSize().padding(insets)
                when {
                    ui.showSettings -> SettingsScreen(
                        ui,
                        body,
                        model::setThemeMode,
                        model::setCountdownSeconds,
                        model::setDynamicColor,
                    )
                    selected == null -> {
                        LaunchedEffect(model, ui.openingRecording) {
                            if (!ui.openingRecording) model.ensureLibraryLoaded()
                        }
                        HomeScreen(ui, body, start, stop, model::setMicrophone, model::open, countdown != null)
                    }
                    else -> VideoScreen(ui, selected, body, { share(selected) }, model)
                }
            }
            if (countdown != null) CountdownOverlay(countdown, cancelCountdown)
        }
        if (microphoneDenied) {
            AlertDialog(
                onDismissRequest = cancelMicrophoneChoice,
                title = { Text(stringResource(R.string.microphone_option)) },
                text = { Text(stringResource(R.string.microphone_denied)) },
                confirmButton = { TextButton(onClick = continueWithoutMicrophone) { Text(stringResource(R.string.continue_silent)) } },
                dismissButton = { TextButton(onClick = cancelMicrophoneChoice) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}

@Composable
private fun RecorderTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = RecorderBlueDark)
        else -> lightColorScheme(primary = RecorderBlue)
    }
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = RecorderTypography,
        content = content,
    )
}

@Composable
private fun CountdownOverlay(count: Int, onCancel: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hint = stringResource(R.string.countdown_cancel_hint)
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(interactionSource = interaction, indication = null, onClick = onCancel)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel), color = Color.White)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    ui: RecorderUiState,
    modifier: Modifier,
    setThemeMode: (ThemeMode) -> Unit,
    setCountdownSeconds: (Int) -> Unit,
    setDynamicColor: (Boolean) -> Unit,
) {
    var showAbout by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SettingsSectionTitle(stringResource(R.string.display_mode))
        ThemeMode.entries.forEach { mode ->
            val label = stringResource(
                when (mode) {
                    ThemeMode.SYSTEM -> R.string.theme_system
                    ThemeMode.LIGHT -> R.string.theme_light
                    ThemeMode.DARK -> R.string.theme_dark
                },
            )
            CompactRadioRow(label = label, selected = ui.themeMode == mode) { setThemeMode(mode) }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val dynamicLabel = stringResource(R.string.dynamic_color)
            CompactSwitchRow(
                title = dynamicLabel,
                supporting = stringResource(R.string.dynamic_color_hint),
                checked = ui.dynamicColor,
                onCheckedChange = setDynamicColor,
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        SettingsSectionTitle(stringResource(R.string.countdown_title))
        Text(
            stringResource(R.string.countdown_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            AppPreferences.COUNTDOWN_OPTIONS.forEachIndexed { index, seconds ->
                val label = if (seconds == 0) stringResource(R.string.countdown_none)
                else stringResource(R.string.countdown_seconds, seconds)
                SegmentedButton(
                    selected = ui.countdownSeconds == seconds,
                    onClick = { setCountdownSeconds(seconds) },
                    shape = SegmentedButtonDefaults.itemShape(index, AppPreferences.COUNTDOWN_OPTIONS.size),
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        val aboutLabel = stringResource(R.string.about_title)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(role = Role.Button) { showAbout = true }
                .semantics { contentDescription = aboutLabel }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(aboutLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun CompactRadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = label }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CompactSwitchRow(
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { contentDescription = title }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                Text(
                    stringResource(R.string.about_license),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.about_privacy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun HomeScreen(
    ui: RecorderUiState,
    modifier: Modifier,
    start: () -> Unit,
    stop: () -> Unit,
    setMicrophone: (Boolean) -> Unit,
    open: (Recording) -> Unit,
    counting: Boolean,
) {
    val busy = ui.capture is CaptureState.Active || ui.capture == CaptureState.Starting || ui.capture == CaptureState.Saving
    LazyColumn(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (val capture = ui.capture) {
                        is CaptureState.Active -> {
                            Text(stringResource(if (capture.microphone) R.string.recording_with_mic else R.string.recording_active))
                            Text(
                                stringResource(R.string.recording_timer, clock(capture.elapsedMs), clock(RecordingService.MAX_MS)),
                                style = MaterialTheme.typography.displaySmall,
                            )
                            LinearProgressIndicator(
                                progress = { (capture.elapsedMs.toFloat() / RecordingService.MAX_MS).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (RecordingService.MAX_MS - capture.elapsedMs <= 30_000) Text(stringResource(R.string.limit_warning))
                            if (ui.notificationsOff) Text(stringResource(R.string.notifications_off))
                            Button(onClick = stop, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.stop_recording)) }
                        }
                        CaptureState.Starting -> {
                            Text(stringResource(R.string.recording_starting))
                            Button(onClick = stop, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.stop_recording)) }
                        }
                        else -> {
                            val label = when (capture) {
                                CaptureState.Saving -> R.string.recording_saving
                                else -> R.string.start_recording
                            }
                            Button(onClick = start, enabled = !busy && !counting, modifier = Modifier.fillMaxWidth()) { Text(stringResource(label)) }
                            Text(stringResource(R.string.limit_hint), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        if (!busy) item {
            val microphoneLabel = stringResource(R.string.microphone_option)
            ListItem(
                headlineContent = { Text(microphoneLabel) },
                supportingContent = { Text(stringResource(R.string.microphone_hint)) },
                trailingContent = { Switch(checked = ui.microphone, onCheckedChange = null) },
                modifier = Modifier.toggleable(ui.microphone, role = Role.Switch, onValueChange = setMicrophone)
                    .semantics { contentDescription = microphoneLabel },
            )
        }
        item { Text(stringResource(R.string.recent_recordings), style = MaterialTheme.typography.titleLarge) }
        val recordings = ui.recordings
        when {
            recordings == null -> item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(R.string.loading))
                }
            }
            recordings.isEmpty() -> item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_recording),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.no_recordings),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> items(recordings, key = { it.file.name }) { recording ->
                val date = remember(recording.dateMs) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(recording.dateMs)) }
                val duration = remember(recording.durationMs) { clock(recording.durationMs) }
                val name = recording.displayName
                val summary = stringResource(R.string.recording_summary, name, date, duration)
                val meta = stringResource(R.string.recording_meta, date, duration)
                ListItem(
                    headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(meta) },
                    leadingContent = { Thumbnail(recording) },
                    modifier = Modifier.clickable(enabled = !busy, role = Role.Button) { open(recording) }
                        .semantics { contentDescription = summary },
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(recording: Recording) {
    val thumbnail by produceState<Bitmap?>(null, recording.file) {
        value = withContext(Dispatchers.IO) { extractThumbnail(recording.file) }
    }
    Card(Modifier.size(64.dp, 44.dp)) {
        val bitmap = thumbnail
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_recording),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun extractThumbnail(file: File): Bitmap? = runCatching {
    val extractor = MediaMetadataRetriever()
    try {
        extractor.setDataSource(file.absolutePath)
        if (Build.VERSION.SDK_INT >= 27) {
            extractor.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 144, 96)
        } else {
            @Suppress("DEPRECATION")
            val frame = extractor.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@runCatching null
            frame.scale(144, 96).also { scaled -> if (scaled !== frame) frame.recycle() }
        }
    } finally {
        extractor.release()
    }
}.getOrNull()

@Composable
private fun VideoScreen(ui: RecorderUiState, recording: Recording, modifier: Modifier, share: () -> Unit, model: RecorderViewModel) {
    var confirmDelete by rememberSaveable(recording.file.name) { mutableStateOf(false) }
    var showRename by remember(recording.file.name) { mutableStateOf(false) }
    var clipPreview by remember(recording.file, ui.editing) { mutableStateOf<ClosedFloatingPointRange<Float>?>(null) }
    var previewRequest by remember(recording.file) { mutableIntStateOf(0) }
    val progress = ui.exportProgress
    val validRange = ui.range.endInclusive.toLong() > ui.range.start.toLong()
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (progress == null) VideoPlayer(recording, clipPreview, previewRequest)
        if (ui.editing) {
            Text(stringResource(R.string.trim_hint))
            val rangeLabel = stringResource(R.string.trim_range, clock(ui.range.start.toLong()),
                clock(ui.range.endInclusive.toLong()), clock((ui.range.endInclusive - ui.range.start).toLong()))
            Text(rangeLabel, style = MaterialTheme.typography.titleMedium)
            RangeSlider(value = ui.range, onValueChange = { clipPreview = null; model.setRange(it) },
                enabled = progress == null, valueRange = 0f..recording.durationMs.toFloat(),
                modifier = Modifier.semantics { contentDescription = rangeLabel })
            if (progress == null) {
                OutlinedButton(onClick = { clipPreview = ui.range; previewRequest++ }, enabled = validRange, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.preview_clip))
                }
                Button(onClick = { clipPreview = null; model.export() }, enabled = validRange, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.save_copy))
                }
            } else {
                if (progress == 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text(if (progress == 0) stringResource(R.string.exporting) else stringResource(R.string.export_progress, progress))
                OutlinedButton(onClick = model::cancelExport) { Text(stringResource(R.string.cancel)) }
            }
        } else {
            Text(recording.displayName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(clock(recording.durationMs), style = MaterialTheme.typography.titleMedium)
            Button(onClick = share, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.share)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = model::startEditing, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.trim)) }
                OutlinedButton(onClick = { showRename = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.rename)) }
            }
            TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete)) }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        text = { Text(stringResource(R.string.delete_prompt)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; model.delete(recording) }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )
    if (showRename) RenameDialog(
        initialName = recording.displayName,
        onDismiss = { showRename = false },
        onConfirm = { name -> showRename = false; model.rename(recording, name) },
    )
}

@Composable
private fun RenameDialog(initialName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by rememberSaveable(initialName, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialName, TextRange(0, initialName.length)))
    }
    val name = value.text
    val invalid = invalidRecordingName(name)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { if (it.text.length <= 80) value = it },
                    singleLine = true,
                    isError = invalid,
                    label = { Text(stringResource(R.string.rename_label)) },
                    supportingText = { Text(stringResource(R.string.rename_counter, name.length)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!invalid) onConfirm(name) }),
                    modifier = Modifier.focusRequester(focusRequester),
                )
                if (invalid) {
                    Text(stringResource(R.string.rename_invalid), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = !invalid) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(recording: Recording, clip: ClosedFloatingPointRange<Float>?, previewRequest: Int) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(recording.file) {
        ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
    var failed by remember(recording.file) { mutableStateOf(false) }
    var fullscreen by remember(recording.file) { mutableStateOf(false) }
    var playing by remember(recording.file) { mutableStateOf(false) }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { failed = true }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        lifecycle.addObserver(observer)
        player.addListener(listener)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }
    LaunchedEffect(player, clip, previewRequest) {
        failed = false
        val media = MediaItem.Builder().setUri(Uri.fromFile(recording.file))
        if (clip != null) media.setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(clip.start.toLong()).setEndPositionMs(clip.endInclusive.toLong()).build())
        player.setMediaItem(media.build())
        player.prepare()
        player.playWhenReady = clip != null
    }
    if (failed) Text(stringResource(R.string.preview_error), color = MaterialTheme.colorScheme.error)
    else if (fullscreen) FullscreenPlayer(player) { fullscreen = false }
    else {
        val screenOn = playing
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f), contentAlignment = Alignment.Center) {
            AndroidView(factory = { PlayerView(it) }, update = { it.player = player; it.keepScreenOn = screenOn },
                onRelease = { it.player = null }, modifier = Modifier.fillMaxSize())
            IconButton(
                onClick = { fullscreen = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.35f)),
            ) {
                Icon(painterResource(R.drawable.ic_fullscreen), stringResource(R.string.fullscreen), tint = Color.White)
            }
        }
    }
}

@Composable
private fun FullscreenPlayer(player: ExoPlayer, onExit: () -> Unit) {
    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window ?: dialogWindow(view.context)
        DisposableEffect(window) {
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { PlayerView(it) }, update = { it.player = player; it.keepScreenOn = true },
                onRelease = { it.player = null }, modifier = Modifier.fillMaxSize())
            IconButton(onClick = onExit, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.exit_fullscreen), tint = Color.White)
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current != null) {
        if (current is Activity) return current
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}

private fun dialogWindow(context: Context): Window? {
    var current: Context? = context
    while (current != null) {
        (current as? DialogWindowProvider)?.let { return it.window }
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}

private fun invalidRecordingName(name: String): Boolean {
    val base = name.trim().removeSuffix(".mp4").trim()
    return base.isEmpty() || base.length > 80 || base.first() == '.' ||
        base.any { it in "\\/:*?\"<>|" || it.code < 32 }
}

private fun clock(ms: Long): String = String.format(Locale.getDefault(), "%02d:%02d", ms / 60_000, ms / 1_000 % 60)

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    RecorderTheme { HomeScreen(RecorderUiState(recordings = emptyList()), Modifier.fillMaxSize(), {}, {}, {}, {}, counting = false) }
}

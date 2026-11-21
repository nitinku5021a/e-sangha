package com.digital.sanghaworld.ui.hall

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digital.sanghaworld.audio.AudioDurationProbe
import com.digital.sanghaworld.audio.GongCatalog
import com.digital.sanghaworld.audio.GongPreviewPlayer
import com.digital.sanghaworld.audio.HallGuidedAudioPlayer
import com.digital.sanghaworld.audio.DriveAudioCatalog
import com.digital.sanghaworld.audio.DriveAudioFile
import com.digital.sanghaworld.hall.AudioType
import com.digital.sanghaworld.hall.HallVisibility
import com.digital.sanghaworld.hall.ScheduleType
import com.digital.sanghaworld.ui.QuietButton
import com.digital.sanghaworld.ui.SanghaField
import com.digital.sanghaworld.ui.SanghaScreenPadding
import com.digital.sanghaworld.ui.StatusText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateHallScreen(
    onCreate: (
        name: String,
        description: String,
        durationMinutes: Int,
        hour: Int,
        minute: Int,
        scheduleType: ScheduleType,
        days: Set<DayOfWeek>,
        visibility: HallVisibility,
        audioType: AudioType,
        audioUrl: String?,
        audioFileName: String?,
        audioDurationSeconds: Int?,
        startDate: LocalDate
    ) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Create a hall",
    submitLabel: String = "Create",
    initialName: String = "",
    initialDescription: String = "",
    initialDurationMinutes: Int = 60,
    initialHour: Int = 6,
    initialMinute: Int = 0,
    initialScheduleType: ScheduleType = ScheduleType.DAILY,
    initialVisibility: HallVisibility = HallVisibility.PUBLIC,
    initialAudioType: AudioType = AudioType.BELL,
    initialTimeZone: String = ZoneId.systemDefault().id,
    initialAudioUrl: String? = null,
    initialAudioFileName: String? = null,
    initialAudioDurationSeconds: Int? = null,
    initialDays: Set<DayOfWeek> = emptySet(),
    initialStartDate: LocalDate? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localInitial = remember(initialHour, initialMinute, initialTimeZone) {
        wallClockToLocal(LocalTime.of(initialHour.coerceIn(0, 23), initialMinute.coerceIn(0, 59)), initialTimeZone)
    }
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var duration by remember { mutableStateOf(initialDurationMinutes.toString()) }
    var startTime by remember { mutableStateOf(localInitial) }
    var scheduleType by remember { mutableStateOf(initialScheduleType) }
    var visibility by remember { mutableStateOf(initialVisibility) }
    var audioType by remember { mutableStateOf(initialAudioType) }
    var bellId by remember {
        mutableStateOf(
            GongCatalog.byId(initialAudioFileName ?: GongCatalog.DEFAULT_ID).id
        )
    }
    var previewName by remember { mutableStateOf<String?>(null) }
    val bellPreview = remember { GongPreviewPlayer(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose {
            bellPreview.stop()
            HallGuidedAudioPlayer.stop()
        }
    }
    var driveLink by remember { mutableStateOf(initialAudioUrl.orEmpty()) }
    var audioFiles by remember { mutableStateOf(emptyList<DriveAudioFile>()) }
    var selectedAudio by remember {
        mutableStateOf(
            if (!initialAudioUrl.isNullOrBlank()) {
                DriveAudioFile(null, initialAudioFileName ?: "Selected audio", initialAudioUrl)
            } else null
        )
    }
    var audioDurationSeconds by remember { mutableStateOf(initialAudioDurationSeconds) }
    var audioStatus by remember { mutableStateOf<String?>(null) }
    var listing by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(initialStartDate ?: LocalDate.now()) }
    var selectedDays by remember {
        mutableStateOf(
            when (initialScheduleType) {
                ScheduleType.WEEKLY, ScheduleType.CUSTOM ->
                    initialDays.ifEmpty { setOf(DayOfWeek.SUNDAY) }
                else -> emptySet()
            }
        )
    }
    var customDuration by remember {
        mutableStateOf(initialDurationMinutes !in setOf(30, 45, 60, 90))
    }
    var nameError by remember { mutableStateOf<String?>(null) }
    var scheduleError by remember { mutableStateOf<String?>(null) }
    val zoneLabel = remember {
        val zone = ZoneId.systemDefault()
        zone.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }
    val dateLabel = remember(startDate) {
        startDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
    val timeLabel = remember(startTime) {
        startTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SanghaScreenPadding, vertical = 8.dp)
    ) {
        Text(
            "Set a time for others to meditate with you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        FormHeading("Hall Details")
        SanghaField(
            name,
            { name = it; nameError = null },
            "Hall name",
            placeholder = "e.g. Morning Meditation"
        )
        if (nameError != null) {
            Spacer(Modifier.height(6.dp))
            StatusText(nameError!!, error = true)
        }
        Spacer(Modifier.height(14.dp))
        SanghaField(
            description,
            { description = it },
            "Description (optional)",
            placeholder = "Add a few details about this sitting.",
            singleLine = false
        )
        Spacer(Modifier.height(18.dp))
        FormHeading("Sitting Duration")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 45, 60, 90).forEach { minutes ->
                PillChoice(
                    "$minutes min",
                    selected = !customDuration && duration == minutes.toString(),
                    onClick = {
                        customDuration = false
                        duration = minutes.toString()
                    }
                )
            }
            PillChoice("Custom", selected = customDuration, onClick = { customDuration = true })
        }
        if (customDuration) {
            Spacer(Modifier.height(10.dp))
            SanghaField(
                duration,
                { if (it.length <= 3 && it.all { c -> c.isDigit() }) duration = it },
                "Minutes",
                placeholder = "1–720",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Spacer(Modifier.height(22.dp))
        FormHeading("Schedule")
        val customDays = scheduleType == ScheduleType.CUSTOM || scheduleType == ScheduleType.WEEKLY
        SegmentedRow(
            listOf(
                "Once" to (scheduleType == ScheduleType.ONCE),
                "Daily" to (scheduleType == ScheduleType.DAILY),
                "Weekdays" to (scheduleType == ScheduleType.WEEKDAYS),
                "Custom Days" to customDays
            )
        ) { index ->
            scheduleError = null
            scheduleType = when (index) {
                0 -> ScheduleType.ONCE
                1 -> ScheduleType.DAILY
                2 -> ScheduleType.WEEKDAYS
                else -> ScheduleType.CUSTOM
            }
        }
        if (customDays) {
            Spacer(Modifier.height(14.dp))
            Text("Repeat on", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DayOfWeek.entries.forEach { day ->
                    val on = day in selectedDays
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
                            .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable {
                                scheduleError = null
                                selectedDays = if (on) selectedDays - day else selectedDays + day
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Repeats every week on selected days.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        if (scheduleError != null) {
            Spacer(Modifier.height(6.dp))
            StatusText(scheduleError!!, error = true)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Start date", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(6.dp))
                PickerField(Icons.Outlined.CalendarMonth, dateLabel) {
                    DatePickerDialog(
                        context,
                        { _, year, month, day -> startDate = LocalDate.of(year, month + 1, day) },
                        startDate.year,
                        startDate.monthValue - 1,
                        startDate.dayOfMonth
                    ).show()
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Start time", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(6.dp))
                PickerField(Icons.Outlined.Schedule, timeLabel) {
                    TimePickerDialog(
                        context,
                        { _, hourOfDay, minuteOfHour -> startTime = LocalTime.of(hourOfDay, minuteOfHour) },
                        startTime.hour,
                        startTime.minute,
                        DateFormat.is24HourFormat(context)
                    ).show()
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(zoneLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(22.dp))
        FormHeading("Audio (Optional)")
        Text(
            "Choose what participants will hear during the sitting.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        SegmentedRow(
            listOf(
                "Silence" to (audioType == AudioType.NONE),
                "Meditation Bell" to (audioType == AudioType.BELL),
                "Custom Audio" to (audioType == AudioType.FILE)
            )
        ) { index ->
            audioType = when (index) {
                0 -> AudioType.NONE
                1 -> AudioType.BELL
                else -> AudioType.FILE
            }
        }
        Spacer(Modifier.height(10.dp))
        if (audioType == AudioType.NONE) {
            AudioNote("No audio will be played.")
        }
        if (audioType == AudioType.BELL) {
            AudioNote("A bell marks the start of the sitting.", Icons.Outlined.VolumeUp)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GongCatalog.all.forEach { sound ->
                    PillChoice(sound.title, bellId == sound.id) {
                        bellPreview.play(sound.resId)
                        bellId = sound.id
                    }
                }
            }
        }
        if (audioType == AudioType.FILE) {
            Spacer(Modifier.height(14.dp))
            var showGuide by remember { mutableStateOf(false) }
            Text(
                if (showGuide) "Hide the guide" else "How this works",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showGuide = !showGuide }
            )
            if (showGuide) {
                Spacer(Modifier.height(10.dp))
                AudioLinkGuide()
            }
            Spacer(Modifier.height(14.dp))
            SanghaField(
                value = driveLink,
                onValueChange = { driveLink = it },
                label = "Google Drive folder or audio link",
                supporting = "Share the folder with “Anyone with the link”, then list files and pick one.",
                singleLine = false
            )
            Spacer(Modifier.height(10.dp))
            QuietButton(
                text = if (listing) "Looking for audio…" else "List audio files",
                enabled = !listing,
                onClick = {
                    listing = true
                    audioStatus = null
                    scope.launch {
                        val files = withContext(Dispatchers.IO) { DriveAudioCatalog.listAudio(driveLink) }
                        audioFiles = files
                        listing = false
                        if (files.isEmpty()) {
                            audioStatus = "No audio found. Check that the folder is public and contains mp3/m4a/wav files."
                        } else {
                            audioStatus = "${files.size} audio file${if (files.size == 1) "" else "s"} found."
                            audioStatus = "Listen to a file, then choose it."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (audioFiles.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FormHeading("Listen, then choose")
                Spacer(Modifier.height(8.dp))
                audioFiles.forEach { file ->
                    val chosen = selectedAudio?.playbackUrl == file.playbackUrl
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            file.name + if (chosen) "  ·  chosen" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            if (previewName == file.name) {
                                HallGuidedAudioPlayer.stop()
                                previewName = null
                            } else {
                                HallGuidedAudioPlayer.start(context, file.playbackUrl, 0L, 20_000L, false)
                                previewName = file.name
                            }
                        }) { Text(if (previewName == file.name) "Stop" else "Preview") }
                        TextButton(onClick = {
                            HallGuidedAudioPlayer.stop()
                            previewName = null
                            selectedAudio = file
                            audioStatus = "Reading duration…"
                            scope.launch {
                                val seconds = withContext(Dispatchers.IO) {
                                    AudioDurationProbe.durationSeconds(file.playbackUrl)
                                }
                                audioDurationSeconds = seconds
                                if (seconds != null) {
                                    val mins = ((seconds + 59) / 60).coerceAtLeast(1)
                                    val current = duration.toIntOrNull() ?: 0
                                    if (mins > current) duration = mins.toString()
                                    audioStatus = "Chosen. Length $mins min."
                                } else {
                                    audioStatus = "Chosen. Length could not be read."
                                }
                            }
                        }) { Text(if (chosen) "Chosen" else "Choose") }
                    }
                }
            }
            selectedAudio?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Selected: ${it.name}" + (audioDurationSeconds?.let { s -> " (${(s + 59) / 60} min)" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
            audioStatus?.let {
                Spacer(Modifier.height(6.dp))
                StatusText(it)
            }
        }
        Spacer(Modifier.height(24.dp))
        QuietButton(
            text = if (submitLabel == "Create") "Create Hall" else submitLabel,
            emphasized = true,
            onClick = {
                if (name.isBlank()) {
                    nameError = "Give the hall a name."
                    return@QuietButton
                }
                val days = if (scheduleType == ScheduleType.CUSTOM || scheduleType == ScheduleType.WEEKLY) {
                    selectedDays
                } else {
                    emptySet()
                }
                if ((scheduleType == ScheduleType.CUSTOM || scheduleType == ScheduleType.WEEKLY) && days.isEmpty()) {
                    scheduleError = "Choose at least one day."
                    return@QuietButton
                }
                onCreate(
                    name.trim(),
                    description.trim(),
                    duration.toIntOrNull()?.coerceIn(1, 720) ?: 60,
                    startTime.hour,
                    startTime.minute,
                    if (scheduleType == ScheduleType.WEEKLY) ScheduleType.CUSTOM else scheduleType,
                    days,
                    visibility,
                    audioType,
                    if (audioType == AudioType.FILE) selectedAudio?.playbackUrl else null,
                    when (audioType) {
                        AudioType.FILE -> selectedAudio?.name
                        AudioType.BELL -> bellId
                        else -> null
                    },
                    if (audioType == AudioType.FILE) audioDurationSeconds else null,
                    startDate
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        QuietButton(text = "Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }
}

private val audioGuideSteps = listOf(
    "Put the meditation audio in a Google Drive folder.",
    "Open Share and choose “Anyone with the link”.",
    "Copy that link and paste it in the field below.",
    "Tap List audio files, listen, then choose one."
)

@Composable
private fun AudioLinkGuide() {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2400)
            step = (step + 1) % audioGuideSteps.size
        }
    }
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.secondaryContainer)
            .padding(16.dp)
    ) {
        Text(
            "Using a Drive link",
            style = MaterialTheme.typography.titleSmall,
            color = colors.onBackground
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            audioGuideSteps.forEachIndexed { index, _ ->
                val current = index == step
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (current) colors.primary else colors.background)
                        .border(1.dp, if (current) colors.primary else colors.outline, CircleShape)
                        .clickable { step = index },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${index + 1}",
                        color = if (current) colors.onPrimary else colors.onBackground,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            audioGuideSteps[step],
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onBackground
        )
    }
}

@Composable
private fun FormHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun PillChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        color = if (selected) colors.onPrimary else colors.onBackground,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) colors.primary else colors.background)
            .border(1.dp, if (selected) colors.primary else colors.outline, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun SegmentedRow(options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.secondaryContainer)
            .padding(4.dp)
    ) {
        options.forEachIndexed { index, (label, selected) ->
            Box(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (selected) colors.primary else colors.secondaryContainer)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) colors.onPrimary else colors.onBackground,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PickerField(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.onBackground, maxLines = 1)
    }
}

@Composable
private fun AudioNote(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.VolumeOff
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.secondaryContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onBackground)
    }
}

private fun wallClockToLocal(time: LocalTime, zoneId: String): LocalTime {
    val from = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
    val local = ZoneId.systemDefault()
    if (from == local) return time
    return ZonedDateTime.of(LocalDate.now(from), time, from)
        .withZoneSameInstant(local)
        .toLocalTime()
}

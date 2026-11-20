package com.digital.sanghaworld.ui.hall

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.digital.sanghaworld.audio.AudioDurationProbe
import com.digital.sanghaworld.audio.GongCatalog
import com.digital.sanghaworld.audio.DriveAudioCatalog
import com.digital.sanghaworld.audio.DriveAudioFile
import com.digital.sanghaworld.hall.AudioType
import com.digital.sanghaworld.hall.HallVisibility
import com.digital.sanghaworld.hall.ScheduleType
import com.digital.sanghaworld.ui.ChoiceChip
import com.digital.sanghaworld.ui.QuietButton
import com.digital.sanghaworld.ui.SanghaField
import com.digital.sanghaworld.ui.SanghaScreenPadding
import com.digital.sanghaworld.ui.ScreenIntro
import com.digital.sanghaworld.ui.SectionLabel
import com.digital.sanghaworld.ui.StatusText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
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
        audioDurationSeconds: Int?
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
    initialAudioDurationSeconds: Int? = null
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
    var sunday by remember { mutableStateOf(initialScheduleType == ScheduleType.WEEKLY) }
    var nameError by remember { mutableStateOf<String?>(null) }
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
        ScreenIntro(
            subtitle = "A shared time, a silent room. Others can join by code."
        )
        Spacer(Modifier.height(20.dp))
        SanghaField(name, { name = it; nameError = null }, "Name")
        if (nameError != null) {
            Spacer(Modifier.height(6.dp))
            StatusText(nameError!!, error = true)
        }
        Spacer(Modifier.height(14.dp))
        SanghaField(description, { description = it }, "Description", singleLine = false)
        Spacer(Modifier.height(14.dp))
        SanghaField(
            duration,
            { if (it.all { c -> c.isDigit() }) duration = it },
            "Duration (minutes)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    TimePickerDialog(
                        context,
                        { _, hourOfDay, minuteOfHour ->
                            startTime = LocalTime.of(hourOfDay, minuteOfHour)
                        },
                        startTime.hour,
                        startTime.minute,
                        DateFormat.is24HourFormat(context)
                    ).show()
                }
        ) {
            SanghaField(
                value = timeLabel,
                onValueChange = {},
                label = "Start time",
                supporting = "Your local time",
                enabled = false,
                trailing = { Icon(Icons.Outlined.Schedule, contentDescription = "Pick time") }
            )
        }
        Spacer(Modifier.height(18.dp))
        SectionLabel("Schedule")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Daily", scheduleType == ScheduleType.DAILY, onClick = { scheduleType = ScheduleType.DAILY })
            ChoiceChip("Weekdays", scheduleType == ScheduleType.WEEKDAYS, onClick = { scheduleType = ScheduleType.WEEKDAYS })
            ChoiceChip("Weekly", scheduleType == ScheduleType.WEEKLY, onClick = { scheduleType = ScheduleType.WEEKLY; sunday = true })
            ChoiceChip("Once", scheduleType == ScheduleType.ONCE, onClick = { scheduleType = ScheduleType.ONCE })
        }
        Spacer(Modifier.height(18.dp))
        SectionLabel("Visibility")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Public", visibility == HallVisibility.PUBLIC, onClick = { visibility = HallVisibility.PUBLIC })
            ChoiceChip("Private", visibility == HallVisibility.PRIVATE, onClick = { visibility = HallVisibility.PRIVATE })
        }
        Spacer(Modifier.height(18.dp))
        SectionLabel("Audio")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Silence", audioType == AudioType.NONE, onClick = { audioType = AudioType.NONE })
            ChoiceChip("Bell", audioType == AudioType.BELL, onClick = { audioType = AudioType.BELL })
            ChoiceChip("Custom audio", audioType == AudioType.FILE, onClick = { audioType = AudioType.FILE })
        }
        if (audioType == AudioType.BELL) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GongCatalog.all.forEach { sound ->
                    ChoiceChip(sound.title, bellId == sound.id, onClick = { bellId = sound.id })
                }
            }
        }
        if (audioType == AudioType.FILE) {
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
                            if (files.size == 1) {
                                selectedAudio = files.first()
                                val seconds = withContext(Dispatchers.IO) {
                                    AudioDurationProbe.durationSeconds(files.first().playbackUrl)
                                }
                                audioDurationSeconds = seconds
                                if (seconds != null) {
                                    val mins = ((seconds + 59) / 60).coerceAtLeast(1)
                                    val current = duration.toIntOrNull() ?: 0
                                    if (mins > current) duration = mins.toString()
                                    audioStatus = "Length $mins min. Sitting time is set to match if it was shorter; you can still change it."
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (audioFiles.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SectionLabel("Choose audio")
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    audioFiles.forEach { file ->
                        ChoiceChip(
                            label = file.name,
                            selected = selectedAudio?.playbackUrl == file.playbackUrl || selectedAudio?.name == file.name,
                            onClick = {
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
                                        audioStatus = "Length $mins min. Sitting time is set to match if it was shorter; you can still change it."
                                    } else {
                                        audioStatus = "Could not read length. You can still use this file."
                                    }
                                }
                            }
                        )
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
            text = submitLabel,
            emphasized = true,
            onClick = {
                if (name.isBlank()) {
                    nameError = "Give the hall a name."
                    return@QuietButton
                }
                onCreate(
                    name.trim(),
                    description.trim(),
                    duration.toIntOrNull() ?: 60,
                    startTime.hour,
                    startTime.minute,
                    scheduleType,
                    if (scheduleType == ScheduleType.WEEKLY) setOf(DayOfWeek.SUNDAY) else emptySet(),
                    visibility,
                    audioType,
                    if (audioType == AudioType.FILE) selectedAudio?.playbackUrl else null,
                    when (audioType) {
                        AudioType.FILE -> selectedAudio?.name
                        AudioType.BELL -> bellId
                        else -> null
                    },
                    if (audioType == AudioType.FILE) audioDurationSeconds else null
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        QuietButton(text = "Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
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

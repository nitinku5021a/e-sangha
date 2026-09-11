package com.digital.sanghaworld.ui.hall

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.digital.sanghaworld.audio.DriveAudioCatalog
import com.digital.sanghaworld.audio.DriveAudioFile
import com.digital.sanghaworld.hall.AudioType
import com.digital.sanghaworld.hall.HallVisibility
import com.digital.sanghaworld.hall.ScheduleType
import com.digital.sanghaworld.ui.QuietButton
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val timeLabel = remember(startTime) {
        startTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            duration, { if (it.all { c -> c.isDigit() }) duration = it },
            label = { Text("Duration (minutes)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
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
            val fieldColors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = timeLabel,
                onValueChange = {},
                enabled = false,
                readOnly = true,
                label = { Text("Start time") },
                supportingText = { Text("Your local time") },
                trailingIcon = {
                    Icon(Icons.Outlined.Schedule, contentDescription = "Pick time")
                },
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Schedule", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(scheduleType == ScheduleType.DAILY, { scheduleType = ScheduleType.DAILY }, { Text("Daily") })
            FilterChip(scheduleType == ScheduleType.WEEKDAYS, { scheduleType = ScheduleType.WEEKDAYS }, { Text("Weekdays") })
            FilterChip(scheduleType == ScheduleType.WEEKLY, { scheduleType = ScheduleType.WEEKLY; sunday = true }, { Text("Weekly") })
            FilterChip(scheduleType == ScheduleType.ONCE, { scheduleType = ScheduleType.ONCE }, { Text("Once") })
        }
        Spacer(Modifier.height(12.dp))
        Text("Visibility", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(visibility == HallVisibility.PUBLIC, { visibility = HallVisibility.PUBLIC }, { Text("Public") })
            FilterChip(visibility == HallVisibility.PRIVATE, { visibility = HallVisibility.PRIVATE }, { Text("Private") })
        }
        Spacer(Modifier.height(12.dp))
        Text("Audio", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(audioType == AudioType.NONE, { audioType = AudioType.NONE }, { Text("Silence") })
            FilterChip(audioType == AudioType.BELL, { audioType = AudioType.BELL }, { Text("Bell") })
            FilterChip(audioType == AudioType.FILE, { audioType = AudioType.FILE }, { Text("Custom audio") })
        }
        if (audioType == AudioType.FILE) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = driveLink,
                onValueChange = { driveLink = it },
                label = { Text("Google Drive folder or audio link") },
                supportingText = {
                    Text("Share the folder with “Anyone with the link”, then list files and pick one.")
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            QuietButton(
                text = if (listing) "Looking for audio…" else "List audio files",
                onClick = {
                    if (listing) return@QuietButton
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
                                    audioStatus = "Length ${mins} min. Sitting time is set to match if it was shorter; you can still change it."
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (audioFiles.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Choose audio", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                audioFiles.forEach { file ->
                    FilterChip(
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
                                    audioStatus = "Length ${mins} min. Sitting time is set to match if it was shorter; you can still change it."
                                } else {
                                    audioStatus = "Could not read length. You can still use this file."
                                }
                            }
                        },
                        label = { Text(file.name) }
                    )
                }
                }
            }
            selectedAudio?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Selected: ${it.name}" + (audioDurationSeconds?.let { s -> " (${(s + 59) / 60} min)" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
            audioStatus?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
            }
        }
        Spacer(Modifier.height(20.dp))
        QuietButton(
            text = submitLabel,
            emphasized = true,
            onClick = {
                onCreate(
                    name,
                    description,
                    duration.toIntOrNull() ?: 60,
                    startTime.hour,
                    startTime.minute,
                    scheduleType,
                    if (scheduleType == ScheduleType.WEEKLY) setOf(DayOfWeek.SUNDAY) else emptySet(),
                    visibility,
                    audioType,
                    if (audioType == AudioType.FILE) selectedAudio?.playbackUrl else null,
                    if (audioType == AudioType.FILE) selectedAudio?.name else null,
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

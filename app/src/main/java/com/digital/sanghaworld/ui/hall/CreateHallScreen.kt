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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.digital.sanghaworld.hall.AudioType
import com.digital.sanghaworld.hall.HallVisibility
import com.digital.sanghaworld.hall.ScheduleType
import com.digital.sanghaworld.ui.QuietButton
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
        audioType: AudioType
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
    initialTimeZone: String = ZoneId.systemDefault().id
) {
    val context = LocalContext.current
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
                    audioType
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

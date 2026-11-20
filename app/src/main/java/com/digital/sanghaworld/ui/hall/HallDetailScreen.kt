package com.digital.sanghaworld.ui.hall

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.digital.sanghaworld.audio.GongCatalog
import com.digital.sanghaworld.audio.GongPreviewPlayer
import com.digital.sanghaworld.audio.HallGuidedAudioPlayer
import com.digital.sanghaworld.hall.AudioType
import com.digital.sanghaworld.hall.CompletionStatus
import com.digital.sanghaworld.hall.HallUiState
import com.digital.sanghaworld.hall.ScheduleType
import com.digital.sanghaworld.ui.MetaRow
import com.digital.sanghaworld.ui.QuietButton
import com.digital.sanghaworld.ui.SanghaScreenPadding
import com.digital.sanghaworld.ui.StatusText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay

private const val AudioPreviewMs = 3 * 60 * 60 * 1000L

@Composable
fun HallDetailScreen(
    ui: HallUiState,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onEnter: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onArrive: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hall = ui.selectedHall ?: return
    val isCreator = ui.profile?.id != null && hall.creatorId == ui.profile.id
    val context = LocalContext.current
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var previewing by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    LaunchedEffect(previewing, hall.audioType) {
        while (previewing && hall.audioType == AudioType.FILE) {
            if (!scrubbing) positionMs = HallGuidedAudioPlayer.positionMs()
            durationMs = HallGuidedAudioPlayer.durationMs()
            delay(400)
        }
    }
    val filePreview = remember { mutableStateOf(false) }
    val bellPreview = remember { GongPreviewPlayer(context.applicationContext) }
    LaunchedEffect(hall.id) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
    }
    DisposableEffect(hall.id) {
        onDispose {
            if (filePreview.value) HallGuidedAudioPlayer.stop()
            bellPreview.stop()
        }
    }
    val startMillis = ui.selectedNextStart
    val durationMillis = hall.durationSeconds * 1000L
    val untilStart = if (startMillis > 0) startMillis - nowMillis else Long.MAX_VALUE
    val inSession = startMillis > 0 && nowMillis >= startMillis && nowMillis < startMillis + durationMillis
    val arrivalWindow = untilStart in 1..(15 * 60 * 1000L)
    val canEnter = inSession || arrivalWindow
    val zone = runCatching { ZoneId.of(ui.selectedSchedule?.timezone ?: hall.timezone) }
        .getOrDefault(ZoneId.systemDefault())
    val startLabel = if (startMillis > 0) {
        val localStart = Instant.ofEpochMilli(startMillis).atZone(zone)
        localStart.format(DateTimeFormatter.ofPattern("EEE d MMM")) + ", " +
            localStart.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    } else "Not scheduled"
    val audioLabel = when (hall.audioType) {
        AudioType.FILE -> hall.audioFileName?.takeIf { it.isNotBlank() } ?: "Recorded audio"
        AudioType.BELL -> GongCatalog.byId(hall.audioFileName ?: GongCatalog.DEFAULT_ID).title + " bell"
        AudioType.NONE -> "None"
    }
    val canPreview = hall.audioType == AudioType.BELL ||
        (hall.audioType == AudioType.FILE && !hall.audioUrl.isNullOrBlank())

    fun stopPreview() {
        if (filePreview.value) HallGuidedAudioPlayer.stop()
        filePreview.value = false
        bellPreview.stop()
        previewing = false
    }

    fun togglePreview() {
        if (previewing) {
            stopPreview()
            return
        }
        when (hall.audioType) {
            AudioType.BELL -> {
                bellPreview.play(GongCatalog.byId(hall.audioFileName ?: GongCatalog.DEFAULT_ID).resId)
                previewing = true
            }
            AudioType.FILE -> {
                val url = hall.audioUrl ?: return
                HallGuidedAudioPlayer.start(context, url, 0L, AudioPreviewMs, startMuted = false)
                filePreview.value = true
                previewing = true
            }
            AudioType.NONE -> Unit
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SanghaScreenPadding, vertical = 8.dp)
    ) {
        Text(hall.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                inSession -> "Sitting now"
                untilStart in 1 until Long.MAX_VALUE -> "Starts in ${formatCountdown(untilStart)}"
                else -> "Scheduled"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        if (hall.description.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                hall.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
            )
        }
        if (canEnter && !inSession) {
            Spacer(Modifier.height(12.dp))
            Text(
                "The hall is open. You can take a seat. The sitting begins at the scheduled time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f)
            )
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        MetaRow("Next sitting", startLabel)
        MetaRow("Duration", "${hall.durationSeconds / 60} minutes")
        MetaRow("Repeats", scheduleLabel(ui.selectedSchedule?.scheduleType))
        MetaRow("Time zone", zone.id)
        MetaRow("Visibility", hall.visibility.name.lowercase().replaceFirstChar { it.titlecase() })
        MetaRow("Share code", hall.shareCode)
        MetaRow(
            if (inSession) "Sitting" else "Expected",
            "${ui.selectedParticipantCount}"
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(
                    "Audio",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
                Text(
                    audioLabel + if (previewing) " · playing" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            if (canPreview) {
                TextButton(onClick = { togglePreview() }) {
                    Text(if (previewing) "Stop" else "Listen")
                }
            }
        }
        if (previewing && hall.audioType == AudioType.FILE) {
            Slider(
                value = positionMs.toFloat(),
                onValueChange = {
                    scrubbing = true
                    positionMs = it.toInt()
                },
                onValueChangeFinished = {
                    HallGuidedAudioPlayer.seekTo(positionMs)
                    scrubbing = false
                },
                valueRange = 0f..durationMs.coerceAtLeast(1).toFloat()
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    "${formatClockShort(positionMs)} / ${formatClockShort(durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    HallGuidedAudioPlayer.skipForward()
                    positionMs = HallGuidedAudioPlayer.positionMs()
                }) { Text("Forward 15s") }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

        Spacer(Modifier.height(16.dp))
        Text(
            "Record",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.42f)
        )
        MetaRow("Sessions", "${ui.stats.sessionCount}")
        MetaRow("Attendance", "${ui.stats.totalAttendance}")
        MetaRow("People", "${ui.stats.uniqueParticipants}")
        MetaRow("Minutes sat", "${ui.stats.totalMeditationSeconds / 60}")
        val hallLogs = ui.logs.filter { it.hallId == hall.id }.take(8)
        if (hallLogs.isEmpty()) {
            Text(
                "Sittings in this hall will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
        } else {
            hallLogs.forEach { log ->
                val mark = when (log.completionStatus) {
                    CompletionStatus.COMPLETED -> "Complete"
                    CompletionStatus.PARTIAL -> "Partial"
                    else -> "—"
                }
                MetaRow(
                    log.date.format(DateTimeFormatter.ofPattern("d MMM")),
                    "${log.durationSeconds / 60} min · $mark"
                )
            }
        }

        if (!ui.actionError.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            StatusText(ui.actionError, error = true)
        }

        Spacer(Modifier.height(20.dp))
        if (canEnter) {
            QuietButton(
                text = if (inSession) "Enter sitting" else "Enter hall",
                emphasized = true,
                onClick = {
                    stopPreview()
                    onEnter()
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
        if (ui.selectedJoined) {
            QuietButton(text = "Leave hall", onClick = onLeave, modifier = Modifier.fillMaxWidth())
        } else {
            QuietButton(
                text = "Join hall",
                emphasized = !canEnter,
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            TextButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Sit with me in ${hall.name}: https://meditation.app/h/${hall.shareCode}"
                        )
                    }
                    context.startActivity(Intent.createChooser(send, "Share hall"))
                }
            ) { Text("Share") }
            if (isCreator) {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                stopPreview()
                onBack()
            }) { Text("Close") }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun formatClockShort(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return String.format("%d:%02d", m, s)
}

private fun scheduleLabel(type: ScheduleType?): String = when (type) {
    ScheduleType.ONCE -> "Once"
    ScheduleType.DAILY -> "Daily"
    ScheduleType.WEEKLY -> "Weekly"
    ScheduleType.WEEKDAYS -> "Weekdays"
    ScheduleType.CUSTOM -> "Custom days"
    null -> "—"
}

private fun formatCountdown(millis: Long): String {
    val totalSec = (millis / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)
}

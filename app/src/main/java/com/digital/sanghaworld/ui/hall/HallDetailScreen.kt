package com.digital.sanghaworld.ui.hall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digital.sanghaworld.audio.GongCatalog
import com.digital.sanghaworld.audio.GongPreviewPlayer
import com.digital.sanghaworld.audio.HallGuidedAudioPlayer
import com.digital.sanghaworld.hall.AudioType
import com.digital.sanghaworld.hall.camelCaseHallName
import com.digital.sanghaworld.hall.CompletionStatus
import com.digital.sanghaworld.hall.HallUiState
import com.digital.sanghaworld.hall.MeditationLogEntry
import com.digital.sanghaworld.hall.ScheduleType
import com.digital.sanghaworld.ui.SanghaScreenPadding
import com.digital.sanghaworld.ui.StatusText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
            val fromPlayer = HallGuidedAudioPlayer.durationMs()
            val fromHall = (hall.audioDurationSeconds ?: 0) * 1000
            durationMs = maxOf(fromPlayer, fromHall)
            delay(400)
        }
    }
    val keepSliderDrag = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = if (source == NestedScrollSource.Drag) available else Offset.Zero
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
    val zone = ZoneId.systemDefault()
    val startLabel = if (startMillis > 0) {
        Instant.ofEpochMilli(startMillis).atZone(zone)
            .format(DateTimeFormatter.ofPattern("EEE d MMM, h:mm a"))
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

    fun shareHall() {
        val text = "https://play.google.com/store/apps/details?id=com.digital.meditationsangha\n${camelCaseHallName(hall.name)}"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Share hall"))
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

    val statusText = when {
        inSession -> "Sitting now"
        untilStart in 1 until Long.MAX_VALUE -> "Starts in ${formatCountdown(untilStart)}"
        else -> "Scheduled"
    }
    val expected = if (inSession && ui.sittingParticipants.isNotEmpty()) {
        ui.sittingParticipants.size
    } else {
        ui.selectedParticipantCount
    }
    val hallLogs = ui.logs
        .withIndex()
        .filter { it.value.hallId == hall.id }
        .sortedWith(compareByDescending<IndexedValue<MeditationLogEntry>> { it.value.date }.thenBy { it.index })
        .take(4)
        .map { it.value }
    val colors = MaterialTheme.colorScheme
    val pill = RoundedCornerShape(28.dp)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SanghaScreenPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(
                camelCaseHallName(hall.name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
                color = colors.onBackground
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .clip(pill)
                    .background(colors.secondaryContainer)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Schedule, null, tint = colors.onBackground, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(statusText, style = MaterialTheme.typography.titleSmall, color = colors.onBackground)
            }
            if (hall.description.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    hall.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onBackground
                )
            }
            if (canEnter && !inSession) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "The hall is open. You can take a seat. The sitting begins at the scheduled time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
        }

        DetailCard {
            SectionTitle(Icons.Outlined.CalendarMonth, "Schedule")
            HorizontalDivider(color = colors.outline.copy(alpha = 0.7f), modifier = Modifier.padding(vertical = 12.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Fact(Icons.Outlined.PlayArrow, "Next sitting", startLabel)
                    Fact(Icons.Outlined.Sync, "Repeats", scheduleLabel(ui.selectedSchedule?.scheduleType))
                    Fact(Icons.Outlined.Schedule, "Duration", "${hall.durationSeconds / 60} minutes")
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Fact(Icons.Outlined.Public, "Time zone", zone.id.replace('_', ' '))
                    Fact(
                        Icons.Outlined.Visibility,
                        "Visibility",
                        hall.visibility.name.lowercase().replaceFirstChar { it.titlecase() }
                    )
                    Fact(Icons.Outlined.CheckCircle, if (inSession) "Sitting" else "Expected", "$expected")
                }
            }
        }

        DetailCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Outlined.VolumeUp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Audio", style = MaterialTheme.typography.titleMedium, color = colors.onBackground)
                    Text(
                        audioLabel + if (previewing) " · playing" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                if (canPreview) {
                    Row(
                        modifier = Modifier
                            .clip(pill)
                            .background(colors.primary)
                            .clickable { togglePreview() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.PlayArrow,
                            null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (previewing) "Stop" else "Preview",
                            color = colors.onPrimary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
            if (previewing && hall.audioType == AudioType.FILE) {
                Slider(
                    modifier = Modifier.nestedScroll(keepSliderDrag),
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${formatClockShort(positionMs)} / ${formatClockShort(durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        HallGuidedAudioPlayer.skipForward()
                        positionMs = HallGuidedAudioPlayer.positionMs()
                    }) { Text("Forward 15s") }
                }
            }
        }

        DetailCard {
            SectionTitle(Icons.Outlined.BarChart, "Record")
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StatCell(Icons.Outlined.Groups, "Sessions", "${ui.stats.sessionCount}", Modifier.weight(1f))
                    StatDivider()
                    StatCell(Icons.Outlined.CalendarMonth, "Attendance", "${ui.stats.totalAttendance}", Modifier.weight(1f))
                }
                HorizontalDivider(color = colors.outline.copy(alpha = 0.7f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StatCell(Icons.Outlined.Groups, "People", "${ui.stats.uniqueParticipants}", Modifier.weight(1f))
                    StatDivider()
                    StatCell(Icons.Outlined.Schedule, "Minutes sat", "${ui.stats.totalMeditationSeconds / 60}", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(14.dp))
            if (hallLogs.isEmpty()) {
                Text(
                    "Sittings in this hall will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            } else {
                hallLogs.forEach { log ->
                    val mark = when (log.completionStatus) {
                        CompletionStatus.COMPLETED -> "Complete"
                        CompletionStatus.PARTIAL -> "Partial"
                        else -> "—"
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.secondaryContainer)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.CalendarMonth, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            log.date.format(DateTimeFormatter.ofPattern("d MMM")),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${log.durationSeconds / 60} min · $mark",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (!ui.actionError.isNullOrBlank()) {
            StatusText(ui.actionError, error = true)
        }
        if (canEnter) {
            ActionButton(
                text = if (inSession) "Enter sitting" else "Enter hall",
                filled = true,
                icon = Icons.Outlined.PlayArrow,
                onClick = {
                    stopPreview()
                    onEnter()
                }
            )
        }
        if (ui.selectedJoined) {
            ActionButton(
                text = "Leave Hall",
                filled = !canEnter,
                icon = Icons.Outlined.Logout,
                onClick = onLeave
            )
        } else {
            ActionButton(
                text = "Join hall",
                filled = !canEnter,
                icon = Icons.Outlined.CheckCircle,
                onClick = onJoin
            )
        }
        ActionButton(
            text = "Share hall",
            filled = false,
            icon = Icons.Outlined.Share,
            onClick = { shareHall() }
        )
        ActionButton(
            text = "Close",
            filled = false,
            icon = Icons.Outlined.Close,
            onClick = {
                stopPreview()
                onBack()
            }
        )
        if (isCreator) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(text = "Edit", filled = false, modifier = Modifier.weight(1f), onClick = onEdit)
                ActionButton(text = "Delete", filled = false, modifier = Modifier.weight(1f), onClick = onDelete)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        content()
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun IconBubble(icon: ImageVector) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Fact(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBubble(icon)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun StatCell(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            maxLines = 1,
            softWrap = false
        )
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun StatDivider() {
    VerticalDivider(
        modifier = Modifier.height(48.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    )
}

@Composable
private fun ActionButton(
    text: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (filled) colors.primary else colors.background)
            .border(if (filled) 0.dp else 1.dp, colors.outline, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                null,
                tint = if (filled) colors.onPrimary else colors.onBackground,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = if (filled) colors.onPrimary else colors.onBackground
        )
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

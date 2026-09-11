package com.digital.sanghaworld.ui.hall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import com.digital.sanghaworld.hall.CompletionStatus
import com.digital.sanghaworld.hall.HallUiState
import com.digital.sanghaworld.ui.QuietButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

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
    LaunchedEffect(hall.id) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
    }
    val startMillis = ui.selectedNextStart
    val durationMillis = hall.durationSeconds * 1000L
    val untilStart = if (startMillis > 0) startMillis - nowMillis else Long.MAX_VALUE
    val inSession = startMillis > 0 && nowMillis >= startMillis && nowMillis < startMillis + durationMillis
    val arrivalWindow = untilStart in 1..(15 * 60 * 1000L)
    val sitKey = "${hall.id}-$startMillis"
    val autoSitBlocked = ui.suppressAutoSitKey == sitKey
    LaunchedEffect(hall.id, arrivalWindow, ui.selectedJoined, autoSitBlocked) {
        if (arrivalWindow && ui.selectedJoined && !autoSitBlocked) onArrive()
    }
    // Sitting is started from the hall scene Join Meditation action.
    val startLabel = if (ui.selectedNextStart > 0) {
        val localStart = Instant.ofEpochMilli(ui.selectedNextStart).atZone(ZoneId.systemDefault())
        localStart.format(DateTimeFormatter.ofPattern("EEE d MMM")) + ", " +
            localStart.format(DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
    } else "—"
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(hall.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(hall.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        Spacer(Modifier.height(16.dp))
        Meta("Duration", "${hall.durationSeconds / 60} minutes")
        Meta("Next sitting", startLabel)
        if (untilStart > 0 && untilStart < 24 * 60 * 60 * 1000L) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Starts in",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatCountdown(untilStart),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 52.sp, letterSpacing = 2.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            if (arrivalWindow && ui.selectedJoined) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "You've arrived. The sitting begins at the scheduled time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Meta("Visibility", hall.visibility.name.lowercase().replaceFirstChar { it.titlecase() })
        Meta(
            "Audio",
            when (hall.audioType) {
                com.digital.sanghaworld.hall.AudioType.FILE ->
                    hall.audioFileName?.takeIf { it.isNotBlank() } ?: "Custom audio"
                else -> hall.audioType.name.lowercase().replaceFirstChar { it.titlecase() }
            }
        )
        Meta("Share code", hall.shareCode)
        Meta("Expected now", "${ui.selectedParticipantCount} people")
        Spacer(Modifier.height(8.dp))
        Text("Hall history", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Meta("Sessions recorded", "${ui.stats.sessionCount}")
        Meta("Total sittings", "${ui.stats.totalAttendance}")
        Meta("People who sat", "${ui.stats.uniqueParticipants}")
        Meta("Meditation minutes", "${ui.stats.totalMeditationSeconds / 60}")
        val hallLogs = ui.logs.filter { it.hallId == hall.id }.take(12)
        if (hallLogs.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Sittings in this hall will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
            )
        } else {
            Spacer(Modifier.height(8.dp))
            hallLogs.forEach { log ->
                val mark = when (log.completionStatus) {
                    CompletionStatus.COMPLETED -> "✓"
                    CompletionStatus.PARTIAL -> "partial"
                    else -> "—"
                }
                Text(
                    "${log.date.format(DateTimeFormatter.ofPattern("d MMM"))}  ·  ${log.durationSeconds / 60} min  ·  $mark",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
        if (!ui.actionError.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                ui.actionError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(20.dp))
        if (inSession) {
            QuietButton(
                text = "Enter sitting",
                emphasized = true,
                onClick = onEnter,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
        }
        if (ui.selectedJoined) {
            QuietButton(text = "Leave hall", onClick = onLeave, modifier = Modifier.fillMaxWidth())
        } else {
            QuietButton(text = "Join hall", emphasized = true, onClick = onJoin, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))
        QuietButton(
            text = "Share",
            onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Sit with me in ${hall.name}: https://meditation.app/h/${hall.shareCode}")
                }
                context.startActivity(Intent.createChooser(send, "Share hall"))
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (isCreator) {
            Spacer(Modifier.height(10.dp))
            QuietButton(text = "Edit hall", onClick = onEdit, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            QuietButton(text = "Delete hall", onClick = onDelete, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))
        QuietButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }
}

private fun formatCountdown(millis: Long): String {
    val totalSec = (millis / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}

@Composable
private fun Meta(label: String, value: String) {
    Text(
        "$label  ·  $value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

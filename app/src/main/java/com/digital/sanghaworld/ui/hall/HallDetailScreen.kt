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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import com.digital.sanghaworld.hall.HallUiState
import com.digital.sanghaworld.ui.QuietButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HallDetailScreen(
    ui: HallUiState,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onEnter: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hall = ui.selectedHall ?: return
    val isCreator = ui.profile?.id != null && hall.creatorId == ui.profile.id
    val context = LocalContext.current
    val startLabel = if (ui.selectedNextStart > 0) {
        val localStart = Instant.ofEpochMilli(ui.selectedNextStart).atZone(ZoneId.systemDefault())
        localStart.format(DateTimeFormatter.ofPattern("EEE d MMM")) + ", " +
            localStart.format(DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
    } else "—"
    val remaining = ui.selectedRemaining
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
        Meta("Visibility", hall.visibility.name.lowercase().replaceFirstChar { it.titlecase() })
        Meta("Audio", hall.audioType.name.lowercase().replaceFirstChar { it.titlecase() })
        Meta("Share code", hall.shareCode)
        Meta("Expected now", "${ui.selectedParticipantCount} people")
        Spacer(Modifier.height(8.dp))
        Text("Hall history", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Meta("Sessions recorded", "${ui.stats.sessionCount}")
        Meta("Total sittings", "${ui.stats.totalAttendance}")
        Meta("Meditation minutes", "${ui.stats.totalMeditationSeconds / 60}")
        if (!ui.actionError.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                ui.actionError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(20.dp))
        if (remaining != null) {
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

@Composable
private fun Meta(label: String, value: String) {
    Text(
        "$label  ·  $value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

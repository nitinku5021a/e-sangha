package com.digital.sanghaworld.ui.hall

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.digital.sanghaworld.hall.HallCard
import com.digital.sanghaworld.hall.HallUiState
import com.digital.sanghaworld.hall.ScheduleType
import java.time.DayOfWeek
import com.digital.sanghaworld.ui.SanghaField
import com.digital.sanghaworld.ui.StatusText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HallsHomeScreen(
    ui: HallUiState,
    onOpenHall: (HallCard) -> Unit,
    onJoinCode: (String) -> Unit,
    openJoinCode: Boolean = false,
    onJoinCodeConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showCodeDialog by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    LaunchedEffect(openJoinCode) {
        if (openJoinCode) {
            showCodeDialog = true
            onJoinCodeConsumed()
        }
    }

    val rooms = remember(ui.discovery) {
        (ui.discovery.sittingNow + ui.discovery.startingSoon + ui.discovery.myHalls)
            .distinctBy { it.hall.id }
            .sortedWith(
            compareBy<HallCard> { !it.joined }
                .thenBy { it.remainingSeconds == null }
                .thenBy { it.nextStartMillis }
        )
    }

    if (rooms.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))
            Text(
                "No halls scheduled",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Halls you have joined are listed first. Within that, the soonest sitting is at the top.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                textAlign = TextAlign.Center
            )
            if (!ui.actionError.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                StatusText(ui.actionError, error = true)
            }
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (!ui.actionError.isNullOrBlank()) {
                item {
                    StatusText(
                        ui.actionError,
                        error = true,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }
            items(rooms, key = { it.hall.id }) { card ->
                HallRoomRow(card, onClick = { onOpenHall(card) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            }
        }
    }

    if (showCodeDialog) {
        AlertDialog(
            onDismissRequest = { showCodeDialog = false },
            title = { Text("Join with code", style = MaterialTheme.typography.headlineSmall) },
            text = {
                SanghaField(
                    value = code,
                    onValueChange = { code = it },
                    label = "Share code"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCodeDialog = false
                        onJoinCode(code.trim())
                    }
                ) { Text("Open", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showCodeDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        )
    }
}

private fun frequencyLabel(card: HallCard): String {
    val schedule = card.schedule
    return when (schedule.scheduleType) {
        ScheduleType.ONCE -> "Once"
        ScheduleType.DAILY -> "Daily"
        ScheduleType.WEEKLY -> "Weekly"
        ScheduleType.WEEKDAYS -> "Weekdays"
        ScheduleType.CUSTOM -> {
            val days = schedule.daysOfWeek.sortedBy { it.value }
            if (days.isEmpty()) "Custom" else days.joinToString(", ") { it.shortName() }
        }
    }
}

private fun DayOfWeek.shortName(): String =
    name.lowercase().replaceFirstChar { it.titlecase() }.take(3)

@Composable
private fun HallRoomRow(card: HallCard, onClick: () -> Unit) {
    val localStart = Instant.ofEpochMilli(card.nextStartMillis).atZone(ZoneId.systemDefault())
    val whenLabel = localStart.format(DateTimeFormatter.ofPattern("EEE")) + " " +
        localStart.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    val sitting = card.remainingSeconds != null
    val frequency = frequencyLabel(card)
    val detail = if (sitting) {
        val minutes = (card.remainingSeconds / 60).coerceAtLeast(0)
        "$whenLabel · $frequency · ${card.participantCount} sitting · ${minutes}m left"
    } else {
        "$whenLabel · $frequency · ${card.participantCount} expected"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                card.hall.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(3.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (card.joined) "Joined" else "Not joined",
                style = MaterialTheme.typography.labelMedium,
                color = if (card.joined) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                }
            )
            if (sitting) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "Now",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

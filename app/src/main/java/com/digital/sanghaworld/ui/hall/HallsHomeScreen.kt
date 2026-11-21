package com.digital.sanghaworld.ui.hall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.unit.dp
import com.digital.sanghaworld.hall.HallCard
import com.digital.sanghaworld.hall.HallUiState
import com.digital.sanghaworld.ui.SanghaField
import com.digital.sanghaworld.ui.StatusText

@Composable
fun HallsHomeScreen(
    ui: HallUiState,
    onOpenHall: (HallCard) -> Unit,
    onOpenCommunity: () -> Unit = {},
    onCreateHall: () -> Unit = {},
    onBrowseHalls: () -> Unit = {},
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

    val mine = ui.discovery.myHalls
        .distinctBy { it.hall.id }
        .sortedBy { it.nextStartMillis }

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
            item {
                Spacer(Modifier.height(12.dp))
                OpenHallCard(count = ui.communityCount, onEnter = onOpenCommunity)
                Spacer(Modifier.height(14.dp))
                ScheduleHallCard(onBrowse = onBrowseHalls, onCreate = onCreateHall)
                if (mine.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    MyHallsCard(halls = mine, onOpen = onOpenHall)
                }
                Spacer(Modifier.height(24.dp))
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

@Composable
private fun OpenHallCard(count: Int, onEnter: () -> Unit) {
    MainCard {
        Text(
            "Open Meditation Hall",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Open all day. Your timer, your bell.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
        )
        Spacer(Modifier.height(16.dp))
        LiveCount(count, onDark = false)
        Spacer(Modifier.height(18.dp))
        LoudButton("Enter Hall", onEnter)
    }
}

@Composable
private fun ScheduleHallCard(onBrowse: () -> Unit, onCreate: () -> Unit) {
    MainCard {
        Text(
            "Schedule Meditation Hall",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Sit with others at a set time, or open a hall of your own.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
        )
        Spacer(Modifier.height(18.dp))
        LoudButton("Scheduled halls", onBrowse)
        Spacer(Modifier.height(10.dp))
        LoudButton("Create a meditation hall", onCreate)
    }
}

@Composable
private fun MyHallsCard(halls: List<HallCard>, onOpen: (HallCard) -> Unit) {
    MainCard {
        Text(
            "My Meditation Halls",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(14.dp))
        halls.forEach { card ->
            JoinedHallRow(card, onClick = { onOpen(card) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun JoinedHallRow(card: HallCard, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val localStart = Instant.ofEpochMilli(card.nextStartMillis).atZone(ZoneId.systemDefault())
    val whenLabel = localStart.format(DateTimeFormatter.ofPattern("EEE")) + " " +
        localStart.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            card.hall.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            whenLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
        )
    }
}

@Composable
private fun MainCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) { content() }
}

@Composable
private fun LoudButton(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    )
}

@Composable
private fun LiveCount(count: Int, onDark: Boolean) {
    val dot = if (count > 0) {
        if (onDark) Color(0xFFF6F5F2) else MaterialTheme.colorScheme.primary
    } else {
        if (onDark) Color(0xFFF6F5F2).copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline
    }
    val label = if (count == 1) "1 sitting" else "$count sitting"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (onDark) Color(0xFFF6F5F2) else MaterialTheme.colorScheme.onSurface
        )
    }
}



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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digital.sanghaworld.hall.HallCard
import com.digital.sanghaworld.hall.HallUiState
import com.digital.sanghaworld.ui.QuietButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HallsHomeScreen(
    ui: HallUiState,
    onOpenHall: (String) -> Unit,
    onCreate: () -> Unit,
    onSupport: () -> Unit,
    onLogs: () -> Unit,
    onJoinCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var code by remember { mutableStateOf("") }
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 88.dp)
        ) {
            Text(
                text = "Meditation halls",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Sit together. Stay silent.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QuietButton(text = "Support", onClick = onSupport, modifier = Modifier.weight(1f))
                QuietButton(text = "Hall log", onClick = onLogs, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Share code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            QuietButton(text = "Open by code", onClick = { onJoinCode(code) }, modifier = Modifier.fillMaxWidth())

            Section("Sitting now", ui.discovery.sittingNow, onOpenHall)
            Section("Starting soon", ui.discovery.startingSoon, onOpenHall)
            Section("My halls", ui.discovery.myHalls, onOpenHall)
            Spacer(Modifier.height(24.dp))
        }
        FloatingActionButton(
            onClick = onCreate,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 20.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Create hall")
        }
    }
}

@Composable
private fun Section(title: String, cards: List<HallCard>, onOpen: (String) -> Unit) {
    Spacer(Modifier.height(24.dp))
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp, fontSize = 12.sp),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
    )
    Spacer(Modifier.height(10.dp))
    if (cards.isEmpty()) {
        Text(
            "None right now.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
        )
    } else {
        cards.distinctBy { it.hall.id }.forEach { card ->
            HallCardRow(card, onOpen)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun HallCardRow(card: HallCard, onOpen: (String) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val localStart = Instant.ofEpochMilli(card.nextStartMillis).atZone(ZoneId.systemDefault())
    val time = localStart.format(DateTimeFormatter.ofPattern("EEE")) + " " +
        localStart.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    val subtitle = if (card.remainingSeconds != null) {
        val m = card.remainingSeconds / 60
        "${card.participantCount} sitting · ${m}m remaining"
    } else {
        "${card.participantCount} expected · $time"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), shape)
            .clickable { onOpen(card.hall.id) }
            .padding(16.dp)
    ) {
        Text(card.hall.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

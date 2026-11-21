package com.digital.sanghaworld.ui.hall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.digital.sanghaworld.hall.HallCard
import com.digital.sanghaworld.hall.camelCaseHallName
import com.digital.sanghaworld.hall.ScheduleType
import com.digital.sanghaworld.ui.SanghaField
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HallDirectoryScreen(
    halls: List<HallCard>,
    onOpen: (HallCard) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(halls, query) {
        val q = query.trim()
        halls.filter { card ->
            q.isBlank() ||
                card.hall.name.contains(q, ignoreCase = true) ||
                camelCaseHallName(card.hall.name).contains(q, ignoreCase = true) ||
                card.hall.description.contains(q, ignoreCase = true)
        }
    }
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SanghaField(
            value = query,
            onValueChange = { query = it },
            label = "Search halls",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (shown.isEmpty()) {
            Text(
                if (halls.isEmpty()) "No halls yet." else "No halls match that search.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.hall.id }) { card ->
                    DirectoryCard(card, onClick = { onOpen(card) })
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun DirectoryCard(card: HallCard, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val localStart = Instant.ofEpochMilli(card.nextStartMillis).atZone(ZoneId.systemDefault())
    val whenLabel = localStart.format(DateTimeFormatter.ofPattern("EEE")) + " " +
        localStart.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    val people = if (card.participantCount == 1) "1 sitting" else "${card.participantCount} sitting"
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row {
            Text(
                camelCaseHallName(card.hall.name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (card.joined) {
                Text(
                    "Joined",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "$whenLabel · ${scheduleName(card)} · $people",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun scheduleName(card: HallCard): String = when (card.schedule.scheduleType) {
    ScheduleType.ONCE -> "Once"
    ScheduleType.DAILY -> "Daily"
    ScheduleType.WEEKLY -> "Weekly"
    ScheduleType.WEEKDAYS -> "Weekdays"
    ScheduleType.CUSTOM -> "Custom"
}

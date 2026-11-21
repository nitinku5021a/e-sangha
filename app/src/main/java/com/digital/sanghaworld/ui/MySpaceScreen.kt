package com.digital.sanghaworld.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.digital.sanghaworld.hall.HallCard
import com.digital.sanghaworld.hall.camelCaseHallName
import com.digital.sanghaworld.logging.DailyTotal
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun MySpaceScreen(
    displayName: String,
    photo: File?,
    logs: List<DailyTotal>,
    halls: List<HallCard>,
    page: String,
    onOpen: (String) -> Unit,
    onName: (String) -> Unit,
    onAddPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (page) {
        "practice" -> PracticeLog(logs, modifier)
        "halls" -> ScheduledHalls(halls, modifier)
        "reminders" -> QuietNote(
            "Reminders",
            "A bell sounds when a hall you have joined is about to begin. Nothing else is tracked.",
            modifier
        )
        "settings" -> GongSettingsScreen(modifier)
        else -> SpaceHome(displayName, photo, onOpen, onName, onAddPhoto, onRemovePhoto, modifier)
    }
}

@Composable
private fun SpaceHome(
    displayName: String,
    photo: File?,
    onOpen: (String) -> Unit,
    onName: (String) -> Unit,
    onAddPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(displayName) { mutableStateOf(displayName) }
    val bitmap = remember(photo?.absolutePath, photo?.lastModified()) {
        photo?.takeIf { it.exists() }?.let { file ->
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        }
    }
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Display name", style = MaterialTheme.typography.headlineSmall) },
            text = {
                SanghaField(value = draft, onValueChange = { draft = it }, label = "Name")
            },
            confirmButton = {
                TextButton(onClick = {
                    onName(draft)
                    editing = false
                }) { Text("Save", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        )
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SanghaScreenPadding, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonMark(bitmap, Modifier.size(72.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    displayName.ifBlank { "You" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Edit name",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        draft = displayName
                        editing = true
                    }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (bitmap == null) "Add a photograph" else "Change photograph",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onAddPhoto)
        )
        if (bitmap != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Remove photograph",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onRemovePhoto)
            )
        }
        Spacer(Modifier.height(28.dp))
        MenuRow(Icons.Outlined.SelfImprovement, "My Practice") { onOpen("practice") }
        MenuRow(Icons.Outlined.Schedule, "My Scheduled Halls") { onOpen("halls") }
        MenuRow(Icons.Outlined.NotificationsNone, "Reminders") { onOpen("reminders") }
        MenuRow(Icons.Outlined.Settings, "Settings") { onOpen("settings") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PersonMark(bitmap: ImageBitmap?, modifier: Modifier) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Profile photograph",
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape)
        )
    } else {
        Icon(
            Icons.Outlined.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        )
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

private enum class Period { WEEK, MONTH, YEAR }

@Composable
private fun PracticeLog(logs: List<DailyTotal>, modifier: Modifier) {
    var period by remember { mutableStateOf(Period.WEEK) }
    val today = remember { LocalDate.now() }
    val start = when (period) {
        Period.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Period.MONTH -> today.withDayOfMonth(1)
        Period.YEAR -> today.withDayOfYear(1)
    }
    val days = logs.filter { !it.date.isBefore(start) && !it.date.isAfter(today) }
        .sortedByDescending { it.date }
    val formatter = DateTimeFormatter.ofPattern("EEE  ·  MMM d")
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SanghaScreenPadding, vertical = 8.dp)
    ) {
        Text(
            "Sits on this device",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Week", period == Period.WEEK, onClick = { period = Period.WEEK })
            ChoiceChip("Month", period == Period.MONTH, onClick = { period = Period.MONTH })
            ChoiceChip("Year", period == Period.YEAR, onClick = { period = Period.YEAR })
        }
        Spacer(Modifier.height(20.dp))
        if (days.isEmpty()) {
            Text(
                "No sits in this period.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        } else {
            days.forEach { day ->
                DayRow(day.date.format(formatter), formatSit(day.totalMillis))
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DayRow(date: String, length: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            date,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            length,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ScheduledHalls(halls: List<HallCard>, modifier: Modifier) {
    val joined = halls.filter { it.joined }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SanghaScreenPadding, vertical = 8.dp)
    ) {
        if (joined.isEmpty()) {
            Text(
                "You have not joined a scheduled hall.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        } else {
            joined.forEach { card ->
                DayRow(camelCaseHallName(card.hall.name), "${card.hall.durationSeconds / 60} min")
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun QuietNote(title: String, body: String, modifier: Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = SanghaScreenPadding, vertical = 8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

private fun formatSit(totalMillis: Long): String {
    val minutes = (totalMillis / 60000).coerceAtLeast(1)
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0) "${hours}h ${rest}m" else "${minutes}m"
}

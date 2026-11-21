package com.digital.sanghaworld.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.digital.sanghaworld.hall.HallCard
import com.digital.sanghaworld.hall.MeditationLogEntry
import com.digital.sanghaworld.hall.camelCaseHallName
import com.digital.sanghaworld.logging.DailyTotal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun MySpaceScreen(
    displayName: String,
    logs: List<DailyTotal>,
    sittings: List<MeditationLogEntry> = emptyList(),
    halls: List<HallCard>,
    page: String,
    onOpen: (String) -> Unit,
    onName: (String) -> Unit,
    onDeleteAccount: () -> Unit = {},
    onOpenHall: (HallCard) -> Unit = {},
    modifier: Modifier = Modifier
) {
    when (page) {
        "practice" -> PracticeLog(logs, sittings, modifier)
        "halls" -> ScheduledHalls(halls, onOpenHall, modifier)
        "settings" -> GongSettingsScreen(modifier)
        else -> SpaceHome(displayName, onOpen, onName, onDeleteAccount, modifier)
    }
}

@Composable
private fun SpaceHome(
    displayName: String,
    onOpen: (String) -> Unit,
    onName: (String) -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember(displayName) { mutableStateOf(displayName) }
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
            PersonMark(Modifier.size(72.dp))
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
        Spacer(Modifier.height(28.dp))
        MenuRow(Icons.Outlined.SelfImprovement, "My Practice") { onOpen("practice") }
        MenuRow(Icons.Outlined.Schedule, "My Scheduled Halls") { onOpen("halls") }
        MenuRow(Icons.Outlined.Settings, "Settings") { onOpen("settings") }
        Spacer(Modifier.height(8.dp))
        Text(
            "Delete account",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .clickable { confirmDelete = true }
                .padding(vertical = 16.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete account", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text("This removes your account, halls you created, and sitting records on the server. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteAccount()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        )
    }
}

@Composable
private fun PersonMark(modifier: Modifier) {
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

@Composable
private fun PracticeLog(
    logs: List<DailyTotal>,
    sittings: List<MeditationLogEntry>,
    modifier: Modifier
) {
    val today = remember { LocalDate.now() }
    val byDate = remember(logs, sittings) { practiceMillisByDate(logs, sittings) }
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val monthStart = today.withDayOfMonth(1)
    val todayMillis = byDate[today] ?: 0L
    val weekMillis = byDate.filter { (date, _) -> !date.isBefore(weekStart) && !date.isAfter(today) }
        .values.sum()
    val monthMillis = byDate.filter { (date, _) -> !date.isBefore(monthStart) && !date.isAfter(today) }
        .values.sum()
    val recent = remember(sittings, logs, today) { recentSittings(sittings, logs, today) }
    var showMonth by remember { mutableStateOf(false) }
    if (showMonth) {
        BackHandler { showMonth = false }
        PracticeMonth(
            byDate = byDate,
            sittings = sittings,
            today = today,
            onClose = { showMonth = false },
            modifier = modifier
        )
        return
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SanghaScreenPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PracticeCard {
            Text(
                "Today",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                formatHours(todayMillis),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (todayMillis > 0L) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Meditation completed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PracticeCard(Modifier.weight(1f)) {
                Text(
                    "This week",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    formatHours(weekMillis),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            PracticeCard(Modifier.weight(1f)) {
                Text(
                    "This month",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    formatHours(monthMillis),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        PracticeCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Practice calendar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = "Calendar view",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { showMonth = true }
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val letters = listOf("M", "T", "W", "T", "F", "S", "S")
                letters.forEachIndexed { index, letter ->
                    val date = weekStart.plusDays(index.toLong())
                    val sat = date == today || date.isBefore(today)
                    val practiced = sat && (byDate[date] ?: 0L) > 0L
                    WeekDay(letter, practiced, Modifier.weight(1f))
                }
            }
        }
        PracticeCard {
            Text(
                "Recent sittings",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (recent.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Sittings will appear here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            } else {
                recent.forEachIndexed { index, row ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                    } else {
                        Spacer(Modifier.height(6.dp))
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            row.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            row.length,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PracticeMonth(
    byDate: Map<LocalDate, Long>,
    sittings: List<MeditationLogEntry>,
    today: LocalDate,
    onClose: () -> Unit,
    modifier: Modifier
) {
    var month by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var selected by remember { mutableStateOf(today) }
    val daysInMonth = month.lengthOfMonth()
    val lead = (month.dayOfWeek.value + 6) % 7
    val cells = lead + daysInMonth
    val rows = (cells + 6) / 7
    val daySittings = sittings.filter { it.date == selected }
    val selectedMillis = byDate[selected] ?: 0L
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SanghaScreenPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PracticeCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.ChevronLeft,
                    contentDescription = "Previous month",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp).clickable { month = month.minusMonths(1) }
                )
                Text(
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = "Next month",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp).clickable { month = month.plusMonths(1) }
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { letter ->
                    Text(
                        letter,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            repeat(rows) { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    repeat(7) { col ->
                        val index = row * 7 + col
                        val day = index - lead + 1
                        if (day !in 1..daysInMonth) {
                            Spacer(Modifier.weight(1f).height(40.dp))
                        } else {
                            val date = month.withDayOfMonth(day)
                            val practiced = (byDate[date] ?: 0L) > 0L
                            val isSelected = date == selected
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            practiced -> MaterialTheme.colorScheme.surfaceVariant
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable { selected = date },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$day",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        practiced -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onBackground
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        PracticeCard {
            Text(
                selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (daySittings.isEmpty() && selectedMillis <= 0L) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "No sitting on this day.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            } else if (daySittings.isNotEmpty()) {
                daySittings.forEach { entry ->
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            camelCaseHallName(entry.hallName),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${(entry.durationSeconds / 60).coerceAtLeast(1)} min",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    formatHours(selectedMillis),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        Text(
            "Close",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onClose).padding(vertical = 8.dp)
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PracticeCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        content()
    }
}

@Composable
private fun WeekDay(letter: String, practiced: Boolean, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            letter,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        if (practiced) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "Practiced",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Spacer(Modifier.height(36.dp))
        }
    }
}

private fun practiceMillisByDate(
    logs: List<DailyTotal>,
    sittings: List<MeditationLogEntry>
): Map<LocalDate, Long> {
    val merged = HashMap<LocalDate, Long>()
    logs.forEach { day ->
        merged[day.date] = day.totalMillis.coerceAtLeast(0L)
    }
    sittings.groupBy { it.date }.forEach { (date, rows) ->
        val serverMillis = rows.sumOf { it.durationSeconds.coerceAtLeast(0).toLong() * 1000L }
        val localMillis = merged[date] ?: 0L
        merged[date] = maxOf(localMillis, serverMillis)
    }
    return merged
}

private data class SittingRow(val label: String, val length: String)

private fun recentSittings(
    sittings: List<MeditationLogEntry>,
    logs: List<DailyTotal>,
    today: LocalDate
): List<SittingRow> {
    if (sittings.isNotEmpty()) {
        return sittings
            .sortedByDescending { it.date }
            .take(8)
            .map { entry ->
                val whenLabel = when (entry.date) {
                    today -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    else -> entry.date.format(DateTimeFormatter.ofPattern("d MMM"))
                }
                val minutes = (entry.durationSeconds / 60).coerceAtLeast(1)
                SittingRow("$whenLabel · ${camelCaseHallName(entry.hallName)}", "$minutes min")
            }
    }
    return logs.sortedByDescending { it.date }.take(8).map { day ->
        val whenLabel = when (day.date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> day.date.format(DateTimeFormatter.ofPattern("d MMM"))
        }
        SittingRow(whenLabel, formatSit(day.totalMillis))
    }
}

@Composable
private fun DayRow(date: String, length: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
private fun ScheduledHalls(halls: List<HallCard>, onOpenHall: (HallCard) -> Unit, modifier: Modifier) {
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
                DayRow(
                    camelCaseHallName(card.hall.name),
                    "${card.hall.durationSeconds / 60} min",
                    onClick = { onOpenHall(card) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun formatHours(totalMillis: Long): String {
    if (totalMillis <= 0L) return "0 hours"
    val minutes = (totalMillis / 60000).coerceAtLeast(1)
    val hours = minutes / 60
    val rest = minutes % 60
    if (hours == 0L) return if (minutes == 1L) "1 minute" else "$minutes minutes"
    val hoursLabel = if (hours == 1L) "1 hour" else "$hours hours"
    return if (rest == 0L) hoursLabel else "$hoursLabel $rest min"
}

private fun formatSit(totalMillis: Long): String {
    val minutes = (totalMillis / 60000).coerceAtLeast(1)
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0) "${hours}h ${rest}m" else "${minutes}m"
}

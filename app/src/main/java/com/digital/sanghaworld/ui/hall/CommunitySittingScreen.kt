package com.digital.sanghaworld.ui.hall

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digital.sanghaworld.hall.ParticipantPresence
import kotlinx.coroutines.delay

private val Page = Color(0xFFF4F1E8)
private val CardBeige = Color(0xFFE9E3D8)
private val HallPanel = Color(0xFFDDE3D7)
private val Seat = Color(0xFFC9D1C2)
private val InkOlive = Color(0xFF344334)
private val CountOlive = Color(0xFF526650)
private val Caption = Color(0xFF6E7664)
private val Forest = Color(0xFF526650)
private val White = Color(0xFFFFFFFF)

@Composable
fun CommunitySittingScreen(
    people: List<ParticipantPresence>,
    currentUserId: String?,
    timeLeftMillis: Long = 0L,
    onOpenTimer: () -> Unit = {},
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shown = people.distinctBy { it.userId }.ifEmpty {
        listOf(ParticipantPresence(currentUserId ?: "you", "You", com.digital.sanghaworld.hall.DisplayMode.AVATAR))
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Page)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(CardBeige)
                .padding(14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "OPEN MEDITATION HALL",
                    color = InkOlive,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (shown.size == 1) "1 sitting" else "${shown.size} sitting",
                    color = CountOlive,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(HallPanel)
                    .padding(10.dp)
            ) {
                val columns = if (maxWidth >= 376.dp) 4 else 3
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    shown.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { person ->
                                val you = person.userId.equals(currentUserId, ignoreCase = true)
                                SittingSeat(
                                    name = seatName(person.label.ifBlank { if (you) "You" else "Someone" }),
                                    spent = spentLabel(person.joinedAtMillis, now),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(columns - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "LEAVE",
                color = Forest,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(White)
                    .clickable(onClick = onLeave)
                    .padding(vertical = 14.dp)
            )
        }
    }
        if (timeLeftMillis > 0L) {
            MovableChip(
                parentWidthPx = constraints.maxWidth,
                parentHeightPx = constraints.maxHeight,
                onClick = onOpenTimer
            ) {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Forest)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text("Your sit", color = White.copy(alpha = 0.85f), fontSize = 11.sp)
                    Text(
                        formatClock(timeLeftMillis),
                        color = White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SittingSeat(name: String, spent: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .height(78.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Seat)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            name,
            color = InkOlive,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            spent,
            color = Caption,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

private fun formatClock(millis: Long): String {
    val totalSec = (millis / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}

private fun seatName(raw: String): String {
    val parts = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.size < 2) return parts.firstOrNull().orEmpty()
    return parts[0] + " " + parts[1].first().uppercaseChar() + "."
}

private fun spentLabel(joinedAtMillis: Long, now: Long): String {
    if (joinedAtMillis <= 0) return "—"
    val elapsed = now - joinedAtMillis
    val totalSec = (elapsed / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}

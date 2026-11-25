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
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digital.sanghaworld.hall.DisplayMode
import com.digital.sanghaworld.hall.MeditationSeat
import com.digital.sanghaworld.hall.ParticipantPresence
import com.digital.sanghaworld.hall.camelCaseHallName
import com.digital.sanghaworld.ui.theme.Ink

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
@Suppress("UNUSED_PARAMETER")
fun MeditationHallScreen(
    hallName: String,
    seats: List<MeditationSeat>,
    people: List<ParticipantPresence> = emptyList(),
    currentUserId: String? = null,
    joined: Boolean,
    timeLeftMillis: Long? = null,
    startsAtMillis: Long? = null,
    muted: Boolean,
    focusMode: Boolean,
    onJoin: () -> Unit,
    onPreviewAudio: (() -> Unit)? = null,
    onLeaveMeditation: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleFocus: () -> Unit,
    onMoreDetails: () -> Unit,
    onOpenTimer: (() -> Unit)? = null,
    canSpeak: Boolean = false,
    micOpen: Boolean = false,
    hostSpeaking: Boolean = false,
    onSpeakToggle: () -> Unit = {},
    onBack: () -> Unit,
    onNavHome: () -> Unit = {},
    onNavHalls: () -> Unit = {},
    onNavSessions: () -> Unit = {},
    onNavSangha: () -> Unit = {},
    onNavProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sitting = visibleSitters(people, seats, currentUserId, joined)
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startsAtMillis, joined) {
        while (joined && startsAtMillis != null && startsAtMillis > System.currentTimeMillis()) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
        nowMillis = System.currentTimeMillis()
    }
    val untilStart = if (joined && startsAtMillis != null) startsAtMillis - nowMillis else 0L
    val shownHallName = hallName.trim().let { if (it.isEmpty()) "Meditation hall" else camelCaseHallName(it) }
    val chipMillis = when {
        untilStart > 0L -> untilStart
        timeLeftMillis != null && timeLeftMillis > 0L -> timeLeftMillis
        else -> 0L
    }
    val chipLabel = if (untilStart > 0L) "STARTS IN" else "REMAINING"

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
                        shownHallName.uppercase(),
                        color = InkOlive,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.6.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (sitting.size == 1) "1 sitting" else "${sitting.size} sitting",
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
                        sitting.chunked(columns).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { person ->
                                    val you = currentUserId != null && person.userId.equals(currentUserId, ignoreCase = true)
                                    ScheduledSeat(
                                        name = shortSeatName(person.label.ifBlank { if (you) "You" else "Someone" }),
                                        line = "Sitting",
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
                if (canSpeak && joined) {
                    Text(
                        if (micOpen) "Tap to stop. The hall can hear you." else "Tap to speak. The hall can hear you.",
                        color = Caption,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (micOpen) "STOP" else "SPEAK",
                        color = White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.2.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Forest)
                            .clickable(onClick = onSpeakToggle)
                            .padding(vertical = 14.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                } else if (hostSpeaking && joined) {
                    Text(
                        "The host is speaking.",
                        color = Caption,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
                if (joined) {
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
                            .clickable(onClick = onLeaveMeditation)
                            .padding(vertical = 14.dp)
                    )
                } else {
                    Text(
                        "JOIN",
                        color = White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.2.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Forest)
                            .clickable(onClick = onJoin)
                            .padding(vertical = 14.dp)
                    )
                }
            }
        }
        if (chipMillis > 0L) {
            MovableChip(
                parentWidthPx = constraints.maxWidth,
                parentHeightPx = constraints.maxHeight,
                onClick = { onOpenTimer?.invoke() }
            ) {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFF4F1E8))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(chipLabel, color = Forest, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(
                        formatClock(chipMillis),
                        color = Ink,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduledSeat(name: String, line: String, modifier: Modifier = Modifier) {
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
            line,
            color = Caption,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

private fun visibleSitters(
    people: List<ParticipantPresence>,
    seats: List<MeditationSeat>,
    currentUserId: String?,
    joined: Boolean
): List<ParticipantPresence> {
    val remote = people
        .filter { it.displayMode != DisplayMode.HIDDEN && it.userId.isNotBlank() }
        .distinctBy { it.userId.lowercase() }
    val fromSeats = seats.filter { it.occupied }.sortedWith(compareBy({ it.row }, { it.col })).map { seat ->
        ParticipantPresence(
            userId = seat.participantId ?: if (seat.isCurrentUser) currentUserId.orEmpty() else "seat-${seat.id}",
            label = seat.label?.ifBlank { null } ?: if (seat.isCurrentUser) "You" else "Someone",
            displayMode = DisplayMode.AVATAR
        )
    }
    val listed = if (remote.isNotEmpty()) remote else fromSeats
    val youListed = currentUserId != null && listed.any { it.userId.equals(currentUserId, ignoreCase = true) }
    return if (joined && currentUserId != null && !youListed) {
        listOf(ParticipantPresence(currentUserId, "You", DisplayMode.AVATAR)) + listed
    } else {
        listed
    }
}

private fun shortSeatName(raw: String): String {
    val parts = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.size < 2) return parts.firstOrNull().orEmpty()
    return parts[0] + " " + parts[1].first().uppercaseChar() + "."
}

private fun formatClock(millis: Long): String {
    val totalSec = (millis / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}



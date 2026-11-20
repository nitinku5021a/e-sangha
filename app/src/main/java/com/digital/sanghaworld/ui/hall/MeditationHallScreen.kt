package com.digital.sanghaworld.ui.hall

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digital.sanghaworld.hall.MeditationSeat
import com.digital.sanghaworld.ui.theme.ForestSoft
import com.digital.sanghaworld.ui.theme.HallCream
import com.digital.sanghaworld.ui.theme.Ink

private val Forest = ForestSoft
private val Cream = HallCream

@Composable
fun MeditationHallScreen(
    hallName: String,
    seats: List<MeditationSeat>,
    joined: Boolean,
    timeLeftMillis: Long? = null,
    startsAtMillis: Long? = null,
    muted: Boolean,
    focusMode: Boolean,
    onJoin: () -> Unit,
    onLeaveMeditation: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleFocus: () -> Unit,
    onMoreDetails: () -> Unit,
    onBack: () -> Unit,
    onNavHome: () -> Unit = {},
    onNavHalls: () -> Unit = {},
    onNavSessions: () -> Unit = {},
    onNavSangha: () -> Unit = {},
    onNavProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val occupied = seats.count { it.occupied }
    val youSeat = seats.firstOrNull { it.isCurrentUser }
    val enter = remember { Animatable(1f) }
    LaunchedEffect(joined, youSeat?.id) {
        if (joined && youSeat != null) {
            enter.snapTo(1.12f)
            enter.animateTo(1f, tween(650))
        } else {
            enter.snapTo(1f)
        }
    }
    var moreOpen by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startsAtMillis, joined) {
        while (joined && startsAtMillis != null && startsAtMillis > System.currentTimeMillis()) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
        nowMillis = System.currentTimeMillis()
    }
    val untilStart = if (joined && startsAtMillis != null) startsAtMillis - nowMillis else 0L

    Column(
        modifier
            .fillMaxSize()
            .background(Cream)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        if (!focusMode) {
            HallHeader(
                hallName = hallName.ifBlank { "Meditation hall" },
                occupied = occupied,
                moreOpen = moreOpen,
                onBack = onBack,
                onMore = { moreOpen = true },
                onDismissMore = { moreOpen = false },
                onDetails = { moreOpen = false; onMoreDetails() }
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            HallArchitecture(Modifier.fillMaxSize())
            if (timeLeftMillis != null && timeLeftMillis > 0) {
                Text(
                    formatClock(timeLeftMillis),
                    color = Ink,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Cream.copy(alpha = 0.92f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            if (untilStart > 0) {
                Text(
                    "Meditation starts in ${formatLead(untilStart)}",
                    color = Ink,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Cream.copy(alpha = 0.94f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val pw = maxWidth
                val ph = maxHeight
                seats.sortedBy { it.ny }.forEach { seat ->
                    val extraY = if (seat.isCurrentUser) (enter.value - 1f) * 72f else 0f
                    Box(
                        Modifier.offset(
                            x = pw * seat.nx - 54.dp * seat.scale,
                            y = ph * seat.ny - 70.dp * seat.scale + extraY.dp
                        )
                    ) {
                        SeatedMeditator(seat)
                    }
                }
            }
        }

        if (focusMode) {
            Text(
                hallName.ifBlank { "Meditation hall" },
                color = Ink,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Text(
                "Leave Focus",
                color = Forest,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(onClick = onToggleFocus)
                    .padding(12.dp)
            )
        } else if (!joined) {
            Text(
                "$occupied meditating",
                color = Forest.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
            )
            JoinButton(onJoin)
            Spacer(Modifier.height(16.dp))
        } else {
            SittingControls(
                muted = muted,
                onLeave = onLeaveMeditation,
                onMute = onToggleMute,
                onFocus = onToggleFocus,
                onDetails = onMoreDetails
            )
        }
    }
}

@Composable
private fun HallHeader(
    hallName: String,
    occupied: Int,
    moreOpen: Boolean,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onDismissMore: () -> Unit,
    onDetails: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = Ink)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                hallName,
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Groups, contentDescription = null, tint = Forest, modifier = Modifier.size(16.dp))
            Text(" $occupied", color = Forest, style = MaterialTheme.typography.bodyMedium)
            Box {
                IconButton(onClick = onMore) {
                    Icon(Icons.Outlined.Settings, contentDescription = "More", tint = Forest)
                }
                DropdownMenu(expanded = moreOpen, onDismissRequest = onDismissMore) {
                    DropdownMenuItem(text = { Text("Hall details") }, onClick = onDetails)
                }
            }
        }
    }
}

@Composable
private fun JoinButton(onJoin: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 28.dp)
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(50))
            .background(Forest)
            .clickable(onClick = onJoin),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.SelfImprovement, contentDescription = null, tint = Color.White)
        Spacer(Modifier.size(8.dp))
        Text("Join Meditation", color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

private fun formatClock(millis: Long): String {
    val totalSec = (millis / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}

private fun formatLead(millis: Long): String {
    val totalSec = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return when {
        minutes > 0 && seconds > 0 -> "$minutes min $seconds sec"
        minutes > 0 -> if (minutes == 1L) "1 minute" else "$minutes minutes"
        seconds == 1L -> "1 second"
        else -> "$seconds seconds"
    }
}

@Composable
private fun SittingControls(
    muted: Boolean,
    onLeave: () -> Unit,
    onMute: () -> Unit,
    onFocus: () -> Unit,
    onDetails: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "You are meditating",
            color = Forest,
            style = MaterialTheme.typography.bodySmall
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MiniAction("Leave", Icons.Outlined.SelfImprovement, onLeave)
            MiniAction(if (muted) "Unmute" else "Mute", if (muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp, onMute)
            MiniAction("Focus", Icons.Outlined.CropFree, onFocus)
            MiniAction("Details", Icons.Outlined.Info, onDetails)
        }
    }
}

@Composable
private fun MiniAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Forest, modifier = Modifier.size(20.dp))
        Text(label, color = Forest, fontSize = 11.sp)
    }
}

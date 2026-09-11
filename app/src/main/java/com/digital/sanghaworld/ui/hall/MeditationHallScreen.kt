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
import androidx.compose.material.icons.outlined.MoreHoriz
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digital.sanghaworld.hall.MeditationSeat

private val Forest = Color(0xFF2F5D4A)
private val Cream = Color(0xFFF7F1E6)
private val Ink = Color(0xFF2A241C)

@Composable
fun MeditationHallScreen(
    hallName: String,
    seats: List<MeditationSeat>,
    joined: Boolean,
    timeLeftMillis: Long? = null,
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

    Column(
        modifier
            .fillMaxSize()
            .background(Cream)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        if (!focusMode) {
            HallHeader(
                subtitle = hallName.ifBlank { "Together in Silence" },
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
                timeLeftMillis = timeLeftMillis,
                muted = muted,
                onLeave = onLeaveMeditation,
                onMute = onToggleMute,
                onFocus = onToggleFocus,
                onMore = { moreOpen = true }
            )
        }
    }
}

@Composable
private fun HallHeader(
    subtitle: String,
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
                "Meditation Hall",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                color = Ink
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Forest
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

@Composable
private fun SittingControls(
    timeLeftMillis: Long?,
    muted: Boolean,
    onLeave: () -> Unit,
    onMute: () -> Unit,
    onFocus: () -> Unit,
    onMore: () -> Unit
) {
    val time = timeLeftMillis?.let {
        val m = (it / 1000) / 60
        val s = (it / 1000) % 60
        String.format("%d:%02d", m, s)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (time != null) "You are meditating  ·  $time" else "You are meditating",
            color = Forest,
            style = MaterialTheme.typography.bodySmall
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MiniAction("Leave", Icons.Outlined.SelfImprovement, onLeave)
            MiniAction(if (muted) "Unmute" else "Mute", if (muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp, onMute)
            MiniAction("Focus", Icons.Outlined.CropFree, onFocus)
            MiniAction("More", Icons.Outlined.MoreHoriz, onMore)
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

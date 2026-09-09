package com.digital.sanghaworld.ui.hall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digital.sanghaworld.R
import com.digital.sanghaworld.hall.DisplayMode
import com.digital.sanghaworld.hall.MeditationHallState
import com.digital.sanghaworld.hall.ParticipantPresence
import com.digital.sanghaworld.hall.SeatAllocator
import com.digital.sanghaworld.ui.QuietButton

@Composable
fun HallSittingScreen(
    hallName: String,
    timeLeft: Long,
    participants: List<ParticipantPresence>,
    expectedSeats: Int,
    currentUserId: String,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val arrived = participants.filter { it.displayMode != DisplayMode.HIDDEN }
    val seats = expectedSeats.coerceAtLeast(arrived.size)
    val emptyCount = (seats - arrived.size).coerceAtLeast(0)
    val minutes = (timeLeft / 1000) / 60
    val seconds = (timeLeft / 1000) % 60
    val timeFormatted = String.format("%d:%02d", minutes, seconds)
    val hallState = remember(arrived, currentUserId) {
        MeditationHallState(
            currentUserId = currentUserId,
            occupants = SeatAllocator.allocate(arrived.map { it.userId }, currentUserId)
        )
    }
    var use3d by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (use3d) {
            MeditationHallView(
                state = hallState,
                modifier = Modifier.fillMaxSize(),
                onUnavailable = { use3d = false }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 48.dp)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    itemsIndexed(arrived, key = { _, p -> p.userId }) { _, person ->
                        OccupiedCushion(name = person.label)
                    }
                    items(emptyCount) {
                        EmptyCushion()
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = hallName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp, top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 16.sp,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "${arrived.size} meditators",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
            )
            Spacer(Modifier.weight(1f))
            QuietButton(
                text = "Leave sitting",
                onClick = onLeave,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun OccupiedCushion(name: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sitter),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp)
        )
    }
}

@Composable
private fun EmptyCushion() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_cushion),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.28f),
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Spacer(Modifier.height(16.dp))
    }
}

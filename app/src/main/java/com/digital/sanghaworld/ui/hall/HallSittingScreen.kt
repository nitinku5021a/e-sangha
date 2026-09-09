package com.digital.sanghaworld.ui.hall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val occupants = remember(arrived, currentUserId) {
        SeatAllocator.allocate(arrived.map { it.userId }, currentUserId)
    }
    val minutes = (timeLeft / 1000) / 60
    val seconds = (timeLeft / 1000) % 60
    val timeFormatted = String.format("%d:%02d", minutes, seconds)
    var use3d by remember { mutableStateOf(true) }

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFD6CBB6))
    ) {
        if (use3d) {
            MeditationHallView(
                state = MeditationHallState(currentUserId, occupants),
                modifier = Modifier.fillMaxSize(),
                onUnavailable = { use3d = false }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    text = hallName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF5C4A32),
                    modifier = Modifier.weight(1f).padding(end = 12.dp, top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, letterSpacing = 1.sp),
                    color = Color(0xFF5C4A32)
                )
            }
            Text(
                text = "${arrived.size} meditators",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF5C4A32).copy(alpha = 0.55f)
            )
            Spacer(Modifier.weight(1f))
            QuietButton(text = "Leave sitting", onClick = onLeave, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
    }
}

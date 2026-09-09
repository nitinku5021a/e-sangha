package com.digital.sanghaworld.ui.hall

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import com.digital.sanghaworld.R
import com.digital.sanghaworld.hall.DisplayMode
import com.digital.sanghaworld.hall.HallOccupant
import com.digital.sanghaworld.hall.ParticipantPresence
import com.digital.sanghaworld.hall.SeatAllocator
import com.digital.sanghaworld.hall.SeatLayout
import com.digital.sanghaworld.ui.QuietButton
import kotlin.math.abs

private val HallCream = Color(0xFFD8CDB8)

private val SitterDrawables = intArrayOf(
    R.drawable.sitter_a,
    R.drawable.sitter_b,
    R.drawable.sitter_c,
    R.drawable.sitter_d
)

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HallCream)
    ) {
        PhotorealHall(
            occupants = occupants,
            modifier = Modifier.fillMaxSize()
        )
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
                    color = Color(0xFF5C4A32),
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
                    color = Color(0xFF5C4A32)
                )
            }
            Text(
                text = "${arrived.size} meditators",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF5C4A32).copy(alpha = 0.55f)
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
private fun PhotorealHall(
    occupants: List<HallOccupant>,
    modifier: Modifier = Modifier
) {
    val breath = rememberInfiniteTransition(label = "breath")
    val breathScale by breath.animateFloat(
        initialValue = 1f,
        targetValue = 1.012f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )
    BoxWithConstraints(modifier) {
        Image(
            painter = painterResource(R.drawable.hall_interior),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        )
        val w = maxWidth
        val h = maxHeight
        val byId = SeatLayout.slots.associateBy { it.id }
        occupants.sortedBy { byId[it.seatId]?.ny ?: 0f }.forEach { occ ->
            val slot = byId[occ.seatId] ?: return@forEach
            val sprite = SitterDrawables[abs(occ.userId.hashCode()) % SitterDrawables.size]
            val size = w * slot.scale
            val youBoost = if (occ.isCurrentUser) 1.03f else 1f
            Image(
                painter = painterResource(sprite),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = w * slot.nx - size / 2f,
                        y = h * slot.ny - size * 0.82f
                    )
                    .width(size)
                    .graphicsLayer {
                        scaleX = youBoost
                        scaleY = breathScale * youBoost
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
                    .alpha(0.98f)
            )
        }
    }
}

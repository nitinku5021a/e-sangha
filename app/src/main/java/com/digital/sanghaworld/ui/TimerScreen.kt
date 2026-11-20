package com.digital.sanghaworld.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import com.digital.sanghaworld.hall.DisplayMode
import com.digital.sanghaworld.hall.ParticipantPresence
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimerScreen(
    timeLeft: Long,
    totalDuration: Long,
    isInPrep: Boolean,
    onStop: () -> Unit,
    hallName: String? = null,
    sittingCount: Int = 0,
    participants: List<ParticipantPresence> = emptyList()
) {
    val minutes = (timeLeft / 1000) / 60
    val seconds = (timeLeft / 1000) % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = hallName ?: if (isInPrep) "SETTLING" else "SITTING",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(28.dp))

        if (isInPrep) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Starting in…",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                AnalogClock(timeLeft = timeLeft, totalDuration = totalDuration)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = timeFormatted,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 52.sp, letterSpacing = 1.5.sp),
            color = MaterialTheme.colorScheme.primary
        )

        if (hallName != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (sittingCount == 1) "1 sitting" else "$sittingCount sitting",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                participants.filter { it.displayMode != DisplayMode.HIDDEN }.forEach { p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        InitialAvatar(p.label, size = 48.dp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = p.label,
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        QuietButton(text = "End session", onClick = onStop)
    }
}

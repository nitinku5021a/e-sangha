package com.digital.sanghaworld.ui.hall

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.digital.sanghaworld.hall.MeditationSeat

@Composable
fun HallSittingScreen(
    hallName: String,
    timeLeft: Long,
    seats: List<MeditationSeat>,
    joined: Boolean,
    muted: Boolean,
    focusMode: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleFocus: () -> Unit,
    onMoreDetails: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    MeditationHallScreen(
        hallName = hallName,
        seats = seats,
        joined = joined,
        timeLeftMillis = timeLeft,
        muted = muted,
        focusMode = focusMode,
        onJoin = onJoin,
        onLeaveMeditation = onLeave,
        onToggleMute = onToggleMute,
        onToggleFocus = onToggleFocus,
        onMoreDetails = onMoreDetails,
        onBack = onBack,
        modifier = modifier
    )
}

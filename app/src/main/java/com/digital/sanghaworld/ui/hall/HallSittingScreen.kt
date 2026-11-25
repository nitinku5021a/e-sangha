package com.digital.sanghaworld.ui.hall

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.digital.sanghaworld.hall.MeditationSeat
import com.digital.sanghaworld.hall.ParticipantPresence

@Composable
fun HallSittingScreen(
    hallName: String,
    timeLeft: Long,
    seats: List<MeditationSeat>,
    people: List<ParticipantPresence> = emptyList(),
    currentUserId: String? = null,
    joined: Boolean,
    startsAtMillis: Long? = null,
    muted: Boolean,
    focusMode: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleFocus: () -> Unit,
    onMoreDetails: () -> Unit,
    onOpenTimer: () -> Unit = {},
    canSpeak: Boolean = false,
    micOpen: Boolean = false,
    hostSpeaking: Boolean = false,
    onSpeakToggle: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    MeditationHallScreen(
        hallName = hallName,
        seats = seats,
        people = people,
        currentUserId = currentUserId,
        joined = joined,
        timeLeftMillis = timeLeft,
        startsAtMillis = startsAtMillis,
        muted = muted,
        focusMode = focusMode,
        onJoin = onJoin,
        onLeaveMeditation = onLeave,
        onToggleMute = onToggleMute,
        onToggleFocus = onToggleFocus,
        onMoreDetails = onMoreDetails,
        onOpenTimer = onOpenTimer,
        canSpeak = canSpeak,
        micOpen = micOpen,
        hostSpeaking = hostSpeaking,
        onSpeakToggle = onSpeakToggle,
        onBack = onBack,
        modifier = modifier
    )
}

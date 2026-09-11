package com.digital.sanghaworld.hall

import com.digital.sanghaworld.R

data class MeditatorLook(
    val drawableRes: Int
)

data class MeditationSeat(
    val id: Int,
    val row: Int,
    val col: Int,
    val nx: Float,
    val ny: Float,
    val scale: Float,
    val occupied: Boolean,
    val participantId: String? = null,
    val label: String? = null,
    val look: MeditatorLook? = null,
    val isCurrentUser: Boolean = false
)

object HallSeatLayout {
    private val sitters = listOf(
        R.drawable.sitter_01,
        R.drawable.sitter_02,
        R.drawable.sitter_03,
        R.drawable.sitter_04,
        R.drawable.sitter_05
    )
    val emptyCushion = R.drawable.seat_empty
    val youLook = MeditatorLook(R.drawable.sitter_01)
    private val mockNames = listOf("Asha", "Dev", "Maya", "Arun", "Leela", "Sam", "Nira", "Omar")

    fun lookFor(seed: Int): MeditatorLook {
        val i = seed.and(0x7fffffff)
        return MeditatorLook(sitters[i % sitters.size])
    }

    fun standard(): List<MeditationSeat> {
        data class Slot(val row: Int, val col: Int, val nx: Float, val ny: Float, val scale: Float)
        val slots = listOf(
            Slot(0, 0, 0.26f, 0.50f, 0.62f),
            Slot(0, 1, 0.42f, 0.50f, 0.62f),
            Slot(0, 2, 0.58f, 0.50f, 0.62f),
            Slot(0, 3, 0.74f, 0.50f, 0.62f),
            Slot(1, 0, 0.18f, 0.64f, 0.82f),
            Slot(1, 1, 0.38f, 0.64f, 0.82f),
            Slot(1, 2, 0.62f, 0.64f, 0.82f),
            Slot(1, 3, 0.82f, 0.64f, 0.82f),
            Slot(2, 0, 0.30f, 0.80f, 1.05f),
            Slot(2, 1, 0.70f, 0.80f, 1.05f)
        )
        return slots.mapIndexed { id, s ->
            MeditationSeat(
                id = id,
                row = s.row,
                col = s.col,
                nx = s.nx,
                ny = s.ny,
                scale = s.scale,
                occupied = false
            )
        }
    }

    fun seedOccupied(
        seats: List<MeditationSeat>,
        live: List<ParticipantPresence>,
        currentUserId: String,
        userJoined: Boolean,
        assignedSeatId: Int?
    ): List<MeditationSeat> {
        val layout = if (seats.isEmpty()) standard() else seats
        val joinSeats = layout.filter { it.row == layout.maxOf { s -> s.row } }.map { it.id }.toSet()
        val liveOthers = live.filter { it.userId != currentUserId && it.displayMode != DisplayMode.HIDDEN }
        val frontIds = layout.filter { it.id !in joinSeats }.map { it.id }
        val liveBySeat = mutableMapOf<Int, ParticipantPresence>()
        liveOthers.forEachIndexed { i, p ->
            if (i < frontIds.size) liveBySeat[frontIds[i]] = p
        }
        var mockIndex = 0
        return layout.map { seat ->
            when {
                userJoined && assignedSeatId == seat.id -> seat.copy(
                    occupied = true,
                    participantId = currentUserId,
                    label = "You",
                    look = youLook,
                    isCurrentUser = true
                )
                seat.id in joinSeats -> seat.copy(
                    occupied = false,
                    participantId = null,
                    label = null,
                    look = null,
                    isCurrentUser = false
                )
                else -> {
                    val livePerson = liveBySeat[seat.id]
                    if (livePerson != null) {
                        seat.copy(
                            occupied = true,
                            participantId = livePerson.userId,
                            label = livePerson.label,
                            look = lookFor(livePerson.userId.hashCode()),
                            isCurrentUser = false
                        )
                    } else {
                        val name = mockNames[mockIndex % mockNames.size]
                        mockIndex++
                        seat.copy(
                            occupied = true,
                            participantId = "mock-$name-${seat.id}",
                            label = name,
                            look = lookFor(seat.id * 17 + 3),
                            isCurrentUser = false
                        )
                    }
                }
            }
        }
    }
}

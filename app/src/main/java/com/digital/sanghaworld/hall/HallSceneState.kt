package com.digital.sanghaworld.hall

data class HallOccupant(
    val userId: String,
    val seatId: String,
    val isCurrentUser: Boolean
)

data class MeditationHallState(
    val currentUserId: String,
    val occupants: List<HallOccupant>
)

object SeatLayout {
    const val COLUMNS = 5
    const val ROWS = 6
    val seatIds: List<String> = (1..COLUMNS * ROWS).map { "seat_%02d".format(it) }
    val currentUserSeatId: String = seatIds[(ROWS - 1) * COLUMNS + COLUMNS / 2]
}

object SeatAllocator {
    fun allocate(userIds: List<String>, currentUserId: String): List<HallOccupant> {
        val unique = userIds.distinct()
        val others = unique.filter { it != currentUserId }.sorted()
        val remaining = SeatLayout.seatIds.filter { it != SeatLayout.currentUserSeatId }.toMutableList()
        val result = ArrayList<HallOccupant>(unique.size)
        if (currentUserId.isNotBlank() && unique.contains(currentUserId)) {
            result += HallOccupant(currentUserId, SeatLayout.currentUserSeatId, true)
        }
        others.forEach { id ->
            val seat = remaining.removeFirstOrNull() ?: return@forEach
            result += HallOccupant(id, seat, false)
        }
        return result
    }
}

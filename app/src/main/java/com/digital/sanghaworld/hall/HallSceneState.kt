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

data class SeatSlot(
    val id: String,
    val nx: Float,
    val ny: Float,
    val scale: Float
)

object SeatLayout {
    const val COLUMNS = 3
    const val ROWS = 6
    val slots: List<SeatSlot> = buildList {
        for (row in 0 until ROWS) {
            val t = row / (ROWS - 1f)
            val ny = 0.905f - t * 0.43f
            val scale = 0.40f - t * 0.26f
            val spread = 0.37f * (1f - t * 0.52f)
            for (col in 0 until COLUMNS) {
                val nx = 0.50f + (col - 1) * spread
                add(SeatSlot("seat_%02d".format(row * COLUMNS + col + 1), nx, ny, scale))
            }
        }
    }
    val seatIds: List<String> = slots.map { it.id }
    val currentUserSeatId: String = "seat_02"
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

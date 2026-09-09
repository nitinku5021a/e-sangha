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
    val x: Float,
    val y: Float,
    val z: Float
)

object SeatLayout {
    const val ROWS = 6
    const val COLS_PER_SIDE = 3
    val slots: List<SeatSlot> = buildList {
        var n = 1
        for (row in 0 until ROWS) {
            val z = -4.8f + row * 1.15f
            for (col in 0 until COLS_PER_SIDE) {
                val x = -3.15f + col * 0.78f
                add(SeatSlot("seat_%02d".format(n++), x, 0f, z))
            }
            for (col in 0 until COLS_PER_SIDE) {
                val x = 1.59f + col * 0.78f
                add(SeatSlot("seat_%02d".format(n++), x, 0f, z))
            }
        }
    }
    val seatIds: List<String> = slots.map { it.id }
    val currentUserSeatId: String = slots[(ROWS - 1) * (COLS_PER_SIDE * 2) + COLS_PER_SIDE].id
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

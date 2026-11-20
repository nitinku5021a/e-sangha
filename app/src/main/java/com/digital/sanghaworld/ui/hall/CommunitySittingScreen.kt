package com.digital.sanghaworld.ui.hall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.digital.sanghaworld.hall.ParticipantPresence
import com.digital.sanghaworld.ui.InitialAvatar
import com.digital.sanghaworld.ui.QuietButton
import com.digital.sanghaworld.ui.SanghaScreenPadding

@Composable
fun CommunitySittingScreen(
    timeLeftMillis: Long,
    people: List<ParticipantPresence>,
    currentUserId: String?,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shown = people.distinctBy { it.userId }.ifEmpty {
        listOf(ParticipantPresence(currentUserId ?: "you", "You", com.digital.sanghaworld.hall.DisplayMode.AVATAR))
    }
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = SanghaScreenPadding, vertical = 16.dp)
    ) {
        Text(
            "Community Room",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            formatSittingClock(timeLeftMillis),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Your time remaining",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(20.dp))
        Text(
            if (shown.size == 1) "1 sitting" else "${shown.size} sitting",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.userId }) { person ->
                val you = person.userId.equals(currentUserId, ignoreCase = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InitialAvatar(person.label, size = 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        person.label + if (you) "  ·  You" else "",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
        QuietButton("Leave", onClick = onLeave, modifier = Modifier.fillMaxWidth())
    }
}

private fun formatSittingClock(millis: Long): String {
    val totalSec = (millis / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}

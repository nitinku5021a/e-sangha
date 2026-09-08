package com.digital.sanghaworld.ui.hall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.digital.sanghaworld.hall.CompletionStatus
import com.digital.sanghaworld.hall.HallUiState
import com.digital.sanghaworld.ui.QuietButton
import java.time.format.DateTimeFormatter

@Composable
fun HallLogScreen(
    ui: HallUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalMin = ui.logs.sumOf { it.durationSeconds } / 60
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text("Personal hall log", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Text(
            "${ui.logs.size} sessions · $totalMin minutes",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (ui.logs.isEmpty()) {
                item {
                    Text("Sittings in halls will appear here.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f))
                }
            }
            items(ui.logs) { log ->
                val mark = when (log.completionStatus) {
                    CompletionStatus.COMPLETED -> "✓"
                    CompletionStatus.PARTIAL -> "Partial"
                    else -> "—"
                }
                Text(
                    "${log.date.format(DateTimeFormatter.ofPattern("d MMM"))}   ${log.hallName}   ${log.durationSeconds / 60} min   $mark",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
        }
        QuietButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
    }
}

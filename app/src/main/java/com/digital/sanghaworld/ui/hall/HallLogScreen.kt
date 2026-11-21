package com.digital.sanghaworld.ui.hall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.digital.sanghaworld.hall.CompletionStatus
import com.digital.sanghaworld.hall.camelCaseHallName
import com.digital.sanghaworld.hall.HallUiState
import com.digital.sanghaworld.ui.EmptyState
import com.digital.sanghaworld.ui.QuietButton
import com.digital.sanghaworld.ui.SanghaCard
import com.digital.sanghaworld.ui.SanghaScreenPadding
import com.digital.sanghaworld.ui.ScreenIntro
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
            .padding(horizontal = SanghaScreenPadding, vertical = 8.dp)
    ) {
        ScreenIntro(
            subtitle = if (ui.logs.isEmpty()) {
                "Sittings in halls will appear here."
            } else {
                "${ui.logs.size} sessions · $totalMin minutes"
            }
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (ui.logs.isEmpty()) {
                item {
                    EmptyState(
                        title = "No hall sittings yet",
                        body = "Join a hall and sit. Completions are recorded here."
                    )
                }
            }
            items(ui.logs, key = { it.id }) { log ->
                val mark = when (log.completionStatus) {
                    CompletionStatus.COMPLETED -> "Complete"
                    CompletionStatus.PARTIAL -> "Partial"
                    else -> "—"
                }
                SanghaCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                camelCaseHallName(log.hallName),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                log.date.format(DateTimeFormatter.ofPattern("d MMM")),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${log.durationSeconds / 60} min",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                mark,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
        QuietButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
    }
}

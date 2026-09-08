package com.digital.sanghaworld.ui.hall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.digital.sanghaworld.hall.HallUiState
import com.digital.sanghaworld.hall.SupportRequestStatus
import com.digital.sanghaworld.ui.QuietButton

@Composable
fun CommunitySupportScreen(
    ui: HallUiState,
    onToggleSupporter: (Boolean) -> Unit,
    onRequest: () -> Unit,
    onCancel: (String) -> Unit,
    onPrivateSit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text("Support", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            "If maintaining your practice has become difficult, you can ask someone to sit with you. Nothing here is public, and there is no ranking.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
        )
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "I’m willing to support someone",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = ui.supporter?.enabled == true, onCheckedChange = onToggleSupporter)
        }
        Spacer(Modifier.height(16.dp))
        QuietButton(text = "Request support", emphasized = true, onClick = onRequest, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        if (ui.relationships.isNotEmpty()) {
            QuietButton(text = "Open private support sit", onClick = onPrivateSit, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("Your requests", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (ui.supportRequests.isEmpty()) {
            Text("None yet.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f))
        } else {
            ui.supportRequests.reversed().forEach { req ->
                Text(
                    "${req.status.name.lowercase()} · ${req.preferredDurationMinutes} min",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                if (req.status == SupportRequestStatus.OPEN || req.status == SupportRequestStatus.MATCHED) {
                    QuietButton(text = "Cancel", onClick = { onCancel(req.id) })
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        QuietButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }
}

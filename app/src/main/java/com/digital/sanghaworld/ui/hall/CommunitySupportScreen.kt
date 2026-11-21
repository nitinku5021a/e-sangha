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
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.digital.sanghaworld.hall.HallUiState
import com.digital.sanghaworld.hall.SupportRequestStatus
import com.digital.sanghaworld.ui.EmptyState
import com.digital.sanghaworld.ui.QuietButton
import com.digital.sanghaworld.ui.SanghaCard
import com.digital.sanghaworld.ui.SanghaScreenPadding
import com.digital.sanghaworld.ui.ScreenIntro
import com.digital.sanghaworld.ui.SectionLabel

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
            .padding(horizontal = SanghaScreenPadding, vertical = 8.dp)
    ) {
        ScreenIntro(
            subtitle = "If practice has become difficult, you can ask someone to sit with you. Nothing here is public, and there is no ranking."
        )
        Spacer(Modifier.height(20.dp))
        SanghaCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "I’m willing to support someone",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = ui.supporter?.enabled == true,
                    onCheckedChange = onToggleSupporter,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        QuietButton(text = "Request support", emphasized = true, onClick = onRequest, modifier = Modifier.fillMaxWidth())
        if (ui.relationships.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            QuietButton(text = "Open private support sit", onClick = onPrivateSit, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(24.dp))
        SectionLabel("Your requests")
        Spacer(Modifier.height(10.dp))
        if (ui.supportRequests.isEmpty()) {
            EmptyState(
                title = "None yet",
                body = "A request stays private until someone is matched with you."
            )
        } else {
            ui.supportRequests.reversed().forEach { req ->
                SanghaCard(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Text(
                        req.status.name.lowercase().replaceFirstChar { it.titlecase() },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${req.preferredDurationMinutes} minutes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                    if (req.status == SupportRequestStatus.OPEN || req.status == SupportRequestStatus.MATCHED) {
                        Spacer(Modifier.height(12.dp))
                        QuietButton(text = "Cancel", onClick = { onCancel(req.id) })
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        QuietButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        SanghaCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Support is not live yet. This feature will be available in a future update.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

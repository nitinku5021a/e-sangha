package com.digital.sanghaworld.ui.hall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.digital.sanghaworld.audio.GongCatalog
import com.digital.sanghaworld.ui.ChoiceChip
import com.digital.sanghaworld.ui.QuietButton
import com.digital.sanghaworld.ui.SanghaField
import com.digital.sanghaworld.ui.SanghaScreenPadding

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommunityRoomScreen(
    minutes: Int,
    gongId: String,
    onMinutes: (Int) -> Unit,
    onGong: (String) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = listOf(15, 30, 45, 60, 120)
    var custom by remember { mutableStateOf(minutes !in presets) }
    var customText by remember { mutableStateOf(if (minutes !in presets) minutes.toString() else "") }
    val customMinutes = customText.toIntOrNull()
    val customReady = !custom || (customMinutes != null && customMinutes in 1..240)
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SanghaScreenPadding, vertical = 12.dp)
    ) {
        Text(
            "Community Room",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Always open. Your timer and bell are your own. Everyone here can see who is sitting.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(24.dp))
        Text("Your timer", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { choice ->
                val label = if (choice == 120) "2 hr" else "$choice min"
                ChoiceChip(label, !custom && minutes == choice, onClick = {
                    custom = false
                    onMinutes(choice)
                })
            }
            ChoiceChip("Custom", custom, onClick = { custom = true })
        }
        if (custom) {
            Spacer(Modifier.height(12.dp))
            SanghaField(
                value = customText,
                onValueChange = { raw ->
                    customText = raw.filter { it.isDigit() }.take(3)
                    customText.toIntOrNull()?.takeIf { it in 1..240 }?.let(onMinutes)
                },
                label = "Minutes",
                supporting = "From 1 to 240 minutes",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("Your bell", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Silence", gongId == "none", onClick = { onGong("none") })
            GongCatalog.all.forEach { sound ->
                ChoiceChip(sound.title, gongId == sound.id, onClick = { onGong(sound.id) })
            }
        }
        Spacer(Modifier.height(28.dp))
        QuietButton(
            "Start sitting",
            onClick = onStart,
            emphasized = true,
            enabled = customReady,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        QuietButton("Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}

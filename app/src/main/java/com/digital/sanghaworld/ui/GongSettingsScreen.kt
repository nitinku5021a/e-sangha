package com.digital.sanghaworld.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.digital.sanghaworld.audio.GongCatalog
import com.digital.sanghaworld.audio.GongPreferences
import com.digital.sanghaworld.audio.GongPreviewPlayer
import com.digital.sanghaworld.audio.GongSound

@Composable
fun GongSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val previewPlayer = remember { GongPreviewPlayer(context.applicationContext) }
    var selectedId by remember { mutableStateOf(GongPreferences.selectedId(context)) }

    DisposableEffect(Unit) {
        onDispose { previewPlayer.stop() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SanghaScreenPadding)
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        ScreenIntro(
            subtitle = "Used at the start and at intervals. At the end of a sit it plays three times, three seconds apart."
        )
        Spacer(modifier = Modifier.height(20.dp))

        GongCatalog.all.forEach { sound ->
            GongSoundRow(
                sound = sound,
                selected = sound.id == selectedId,
                onSelect = {
                    selectedId = sound.id
                    GongPreferences.setSelectedId(context, sound.id)
                },
                onPreview = { previewPlayer.play(sound.resId) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun GongSoundRow(
    sound: GongSound,
    selected: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.outline
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SanghaCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, borderColor, SanghaCardShape)
            .clickable(onClick = onSelect)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(
            text = sound.title,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = sound.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selected) "SELECTED" else "TAP TO USE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.9f else 0.45f)
            )
            QuietButton(text = "Play", onClick = onPreview)
        }
    }
}

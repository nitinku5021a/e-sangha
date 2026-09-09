package com.digital.sanghaworld.ui.hall

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.digital.sanghaworld.hall.MeditationHallState
import com.digital.sanghaworld.hall.filament.MeditationHallRenderer

@Composable
fun MeditationHallView(
    state: MeditationHallState,
    modifier: Modifier = Modifier,
    onUnavailable: () -> Unit
) {
    var renderer by remember { mutableStateOf<MeditationHallRenderer?>(null) }
    LaunchedEffect(state, renderer) {
        renderer?.setState(state)
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).also { surface ->
                try {
                    renderer = MeditationHallRenderer(context, surface).also { it.setState(state) }
                } catch (_: Throwable) {
                    onUnavailable()
                }
            }
        },
        update = { renderer?.setState(state) }
    )
    DisposableEffect(Unit) {
        onDispose { renderer?.destroy(); renderer = null }
    }
}

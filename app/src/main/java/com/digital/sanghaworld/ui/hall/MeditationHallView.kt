package com.digital.sanghaworld.ui.hall

import android.view.TextureView
import android.widget.FrameLayout
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
            val texture = TextureView(context)
            val host = FrameLayout(context).apply {
                addView(
                    texture,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }
            try {
                renderer = MeditationHallRenderer(context, texture).also { it.setState(state) }
            } catch (_: Throwable) {
                onUnavailable()
            }
            host
        },
        update = { renderer?.setState(state) }
    )
    DisposableEffect(Unit) {
        onDispose { renderer?.destroy(); renderer = null }
    }
}

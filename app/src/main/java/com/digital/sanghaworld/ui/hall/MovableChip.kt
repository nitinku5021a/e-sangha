package com.digital.sanghaworld.ui.hall

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun BoxScope.MovableChip(
    parentWidthPx: Int,
    parentHeightPx: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var drag by remember { mutableStateOf(Offset.Zero) }
    var chip by remember { mutableStateOf(IntSize.Zero) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(chip, parentWidthPx, parentHeightPx) {
        if (!placed && chip.width > 0 && parentWidthPx > 0) {
            val margin = 24f
            drag = Offset(
                (parentWidthPx - chip.width - margin).coerceAtLeast(0f),
                margin
            )
            placed = true
        }
    }
    Box(
        modifier
            .onSizeChanged { chip = it }
            .offset {
                val maxX = (parentWidthPx - chip.width).coerceAtLeast(0)
                val maxY = (parentHeightPx - chip.height).coerceAtLeast(0)
                IntOffset(
                    drag.x.roundToInt().coerceIn(0, maxX),
                    drag.y.roundToInt().coerceIn(0, maxY)
                )
            }
            .pointerInput(parentWidthPx, parentHeightPx, onClick) {
                coroutineScope {
                    launch {
                        detectTapGestures(onTap = { onClick() })
                    }
                    launch {
                        detectDragGestures { change, amount ->
                            change.consume()
                            val maxX = (parentWidthPx - chip.width).coerceAtLeast(0).toFloat()
                            val maxY = (parentHeightPx - chip.height).coerceAtLeast(0).toFloat()
                            drag = Offset(
                                (drag.x + amount.x).coerceIn(0f, maxX),
                                (drag.y + amount.y).coerceIn(0f, maxY)
                            )
                        }
                    }
                }
            }
    ) {
        content()
    }
}

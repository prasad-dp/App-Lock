package com.example.ui.pattern

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

enum class PatternState {
    DRAWING,
    SUCCESS,
    ERROR
}

@Composable
fun PatternLockView(
    modifier: Modifier = Modifier,
    state: PatternState = PatternState.DRAWING,
    onPatternComplete: (List<Int>) -> Unit
) {
    val connectedDots = remember { mutableStateListOf<Int>() }
    var currentTouchPosition by remember { mutableStateOf<Offset?>(null) }
    val haptic = LocalHapticFeedback.current

    // Colors
    val primaryColor = MaterialTheme.colorScheme.primary
    val successColor = Color(0xFF66BB6A)
    val errorColor = MaterialTheme.colorScheme.error
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    val lineColor = when (state) {
        PatternState.DRAWING -> primaryColor
        PatternState.SUCCESS -> successColor
        PatternState.ERROR -> errorColor
    }

    // Reset pattern if the state changes back to drawing externally
    LaunchedEffect(state) {
        if (state == PatternState.DRAWING) {
            connectedDots.clear()
            currentTouchPosition = null
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val sizePx = minOf(constraints.maxWidth, constraints.maxHeight).toFloat()
        
        // Define coordinates of 3x3 dots
        val margin = sizePx / 6f
        val spacing = (sizePx - 2f * margin) / 2f
        
        val density = LocalDensity.current
        val dotRadiusPx = with(density) { 14.dp.toPx() } // Correctly scaled visual radius via density
        val detectRadiusPx = with(density) { 48.dp.toPx() } // Larger touch target radius for responsive unlocking

        val dots = remember(sizePx) {
            List(9) { i ->
                val row = i / 3
                val col = i % 3
                Offset(
                    x = margin + col * spacing,
                    y = margin + row * spacing
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state) {
                    if (state != PatternState.DRAWING) return@pointerInput

                    awaitEachGesture {
                        val down = awaitFirstDown()
                        connectedDots.clear()
                        val offset = down.position
                        
                        // Check if initial touch is on any dot
                        dots.forEachIndexed { index, dot ->
                            if (getDistance(offset, dot) < detectRadiusPx) {
                                connectedDots.add(index)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                        currentTouchPosition = offset

                        while (true) {
                            val event = awaitPointerEvent()
                            val anyPressed = event.changes.any { it.pressed }
                            if (!anyPressed) {
                                // All touches released -> complete pattern
                                if (connectedDots.size >= 4) {
                                    onPatternComplete(connectedDots.toList())
                                } else if (connectedDots.isNotEmpty()) {
                                    onPatternComplete(emptyList())
                                    connectedDots.clear()
                                }
                                currentTouchPosition = null
                                break
                            }

                            val change = event.changes.firstOrNull()
                            if (change != null) {
                                val currentOffset = change.position
                                currentTouchPosition = currentOffset

                                dots.forEachIndexed { index, dot ->
                                    if (getDistance(currentOffset, dot) < detectRadiusPx) {
                                        if (index !in connectedDots) {
                                            // Auto-add intermediate skips (e.g., going diagonal or jumping over dots)
                                            if (connectedDots.isNotEmpty()) {
                                                val last = connectedDots.last()
                                                val lastRow = last / 3
                                                val lastCol = last % 3
                                                val currRow = index / 3
                                                val currCol = index % 3

                                                val midRow = (lastRow + currRow) / 2
                                                val midCol = (lastCol + currCol) / 2
                                                val midIndex = midRow * 3 + midCol

                                                if ((lastRow + currRow) % 2 == 0 && (lastCol + currCol) % 2 == 0) {
                                                    if (midIndex !in connectedDots) {
                                                        connectedDots.add(midIndex)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                }
                                            }
                                            connectedDots.add(index)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                }
                                change.consume()
                            }
                        }
                    }
                }
        ) {
            // 1. Draw connecting lines
            if (connectedDots.isNotEmpty()) {
                for (i in 0 until connectedDots.size - 1) {
                    val p1 = dots[connectedDots[i]]
                    val p2 = dots[connectedDots[i + 1]]
                    drawLine(
                        color = lineColor,
                        start = p1,
                        end = p2,
                        strokeWidth = 12f, // Slightly thicker line for better visual presence
                        cap = StrokeCap.Round
                    )
                }

                // Draw line from last connected dot to current finger touch
                val lastDotPos = dots[connectedDots.last()]
                val currentTouch = currentTouchPosition
                if (currentTouch != null && state == PatternState.DRAWING) {
                    drawLine(
                        color = lineColor,
                        start = lastDotPos,
                        end = currentTouch,
                        strokeWidth = 12f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // 2. Draw 3x3 dots background & active overlays
            dots.forEachIndexed { index, dot ->
                val isSelected = index in connectedDots
                
                if (isSelected) {
                    // Draw outer translucent glow circle for connected dots
                    drawCircle(
                        color = lineColor.copy(alpha = 0.25f),
                        radius = dotRadiusPx * 2.5f,
                        center = dot
                    )
                    // Draw active inner core index
                    drawCircle(
                        color = lineColor,
                        radius = dotRadiusPx,
                        center = dot
                    )
                } else {
                    // Standard inactive dot
                    drawCircle(
                        color = dotColor,
                        radius = dotRadiusPx * 0.7f,
                        center = dot
                    )
                }
            }
        }
    }
}

private fun getDistance(p1: Offset, p2: Offset): Float {
    val dx = p1.x - p2.x
    val dy = p1.y - p2.y
    return sqrt(dx * dx + dy * dy)
}

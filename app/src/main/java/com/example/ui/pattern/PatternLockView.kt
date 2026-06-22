package com.example.ui.pattern

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
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

    // Colors
    val primaryColor = MaterialTheme.colorScheme.primary
    val successColor = Color(0xFF4CAF50)
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
        val dotRadius = 14.dp.value // Base visual radius
        val detectRadius = 40.dp.value // Radius for touch detection (larger to be user-friendly)

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

                    detectDragGestures(
                        onDragStart = { offset ->
                            connectedDots.clear()
                            // Check if initial touch is on any dot
                            dots.forEachIndexed { index, dot ->
                                if (getDistance(offset, dot) < detectRadius) {
                                    connectedDots.add(index)
                                }
                            }
                            currentTouchPosition = offset
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val offset = change.position
                            currentTouchPosition = offset

                            dots.forEachIndexed { index, dot ->
                                if (getDistance(offset, dot) < detectRadius) {
                                    if (index !in connectedDots) {
                                        // Auto-add skips (e.g. going from 0 to 2 directly should add 1)
                                        if (connectedDots.isNotEmpty()) {
                                            val last = connectedDots.last()
                                            val lastRow = last / 3
                                            val lastCol = last % 3
                                            val currRow = index / 3
                                            val currCol = index % 3
                                            
                                            // Check intermediate
                                            val midRow = (lastRow + currRow) / 2
                                            val midCol = (lastCol + currCol) / 2
                                            val midIndex = midRow * 3 + midCol
                                            
                                            if ((lastRow + currRow) % 2 == 0 && (lastCol + currCol) % 2 == 0) {
                                                if (midIndex !in connectedDots) {
                                                    connectedDots.add(midIndex)
                                                }
                                            }
                                        }
                                        connectedDots.add(index)
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            if (connectedDots.size >= 4) { // Patterns usually require at least 4 dots
                                onPatternComplete(connectedDots.toList())
                            } else if (connectedDots.isNotEmpty()) {
                                // Too short
                                onPatternComplete(emptyList())
                                connectedDots.clear()
                            }
                            currentTouchPosition = null
                        },
                        onDragCancel = {
                            connectedDots.clear()
                            currentTouchPosition = null
                        }
                    )
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
                        strokeWidth = 10f,
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
                        strokeWidth = 10f,
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
                        radius = dotRadius * 2.5f,
                        center = dot
                    )
                    // Draw active inner core index
                    drawCircle(
                        color = lineColor,
                        radius = dotRadius,
                        center = dot
                    )
                } else {
                    // Standard inactive dot
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius * 0.7f,
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

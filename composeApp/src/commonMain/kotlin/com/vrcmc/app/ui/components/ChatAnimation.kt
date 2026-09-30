package com.vrcmc.app

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val chatAnimationFrameIntervalNanos = 1_000_000_000L / 60
private const val chatLoadingCycleDurationNanos = 1_800_000_000L

@Composable
internal fun rememberChatAnimationTime(active: Boolean): State<Long> {
    val elapsedNanos = remember { mutableLongStateOf(0L) }
    LaunchedEffect(active) {
        elapsedNanos.longValue = 0L
        if (!active) return@LaunchedEffect
        val startedNanos = withFrameNanos { it }
        while (isActive) {
            val remainingNanos =
                chatAnimationFrameIntervalNanos - elapsedNanos.longValue % chatAnimationFrameIntervalNanos
            delay((remainingNanos + 999_999L) / 1_000_000L)
            elapsedNanos.longValue = withFrameNanos { it - startedNanos }
        }
    }
    return elapsedNanos
}

internal fun chatAnimationFraction(elapsedNanos: Long, durationNanos: Long): Float =
    (elapsedNanos % durationNanos).toFloat() / durationNanos

@Composable
internal fun ChatLinearProgressIndicator(animationTimeNanos: State<Long>, modifier: Modifier = Modifier) {
    val color = ProgressIndicatorDefaults.linearColor
    val trackColor = ProgressIndicatorDefaults.linearTrackColor
    val widthPx = remember { mutableIntStateOf(0) }
    Box(
        modifier
            .progressSemantics()
            .height(4.dp)
            .clipToBounds()
            .drawWithCache {
            val strokeWidth = size.height
            val left = strokeWidth / 2
            val right = (size.width - left).coerceAtLeast(left)
            val centerY = size.height / 2
            val trackStart = Offset(left, centerY)
            val trackEnd = Offset(right, centerY)
            onDrawBehind {
                drawLine(trackColor, trackStart, trackEnd, strokeWidth, StrokeCap.Round)
            }
        }
        .onSizeChanged { widthPx.intValue = it.width },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(.3f)
                .graphicsLayer {
                    val indicatorWidth = widthPx.intValue * .3f
                    val travel = widthPx.intValue + indicatorWidth
                    val phase = chatAnimationFraction(animationTimeNanos.value, chatLoadingCycleDurationNanos)
                    translationX = phase * travel - indicatorWidth
                }
                .background(color, RoundedCornerShape(percent = 50)),
        )
    }
}

@Composable
internal fun ChatCircularProgressIndicator(
    animationTimeNanos: State<Long>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.dp,
) {
    val color = ProgressIndicatorDefaults.circularColor
    Box(
        modifier
            .progressSemantics()
            .graphicsLayer {
                rotationZ = chatAnimationFraction(animationTimeNanos.value, chatLoadingCycleDurationNanos) * 360f
            },
    ) {
        Spacer(
            Modifier.matchParentSize().drawWithCache {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val diameter = (size.minDimension - stroke.width).coerceAtLeast(0f)
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            onDrawBehind {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
            }
            }
        )
    }
}

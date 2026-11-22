package com.rushi.modulesmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun VerticalScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val metrics by remember(state) {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            val viewport = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
            if (totalItems == 0 || visibleItems.isEmpty() || viewport <= 0f) {
                null
            } else {
                val averageStep = averageItemStep(visibleItems)
                val totalContent = (averageStep * totalItems).coerceAtLeast(viewport)
                val contentRange = (totalContent - viewport).coerceAtLeast(0f)
                if (contentRange <= 0f) {
                    null
                } else {
                    val current = (state.firstVisibleItemIndex * averageStep + state.firstVisibleItemScrollOffset)
                        .coerceIn(0f, contentRange)
                    val progress = when {
                        !state.canScrollBackward -> 0f
                        !state.canScrollForward -> 1f
                        else -> (current / contentRange).coerceIn(0f, 1f)
                    }
                    val thumbFraction = (viewport / totalContent).coerceIn(0.06f, 1f)
                    ScrollbarMetrics(progress, thumbFraction)
                }
            }
        }
    }

    val currentMetrics = metrics ?: return

    BoxWithConstraints(
        modifier = modifier
            .width(24.dp)
            .pointerInput(state) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val total = state.layoutInfo.totalItemsCount
                    if (total > 1 && size.height > 0) {
                        val fraction = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                        val targetIndex = (fraction * (total - 1)).toInt().coerceIn(0, total - 1)
                        if (targetIndex != state.firstVisibleItemIndex) {
                            scope.launch { state.scrollToItem(targetIndex) }
                        }
                    }
                }
            },
    ) {
        val density = LocalDensity.current
        val trackHeightPx = constraints.maxHeight.toFloat()
        val thumbHeightPx = (trackHeightPx * currentMetrics.thumbFraction)
            .coerceAtLeast(with(density) { 36.dp.toPx() })
        val maxOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .graphicsLayer {
                    translationY = maxOffsetPx * currentMetrics.progress
                }
                .width(8.dp)
                .height(with(density) { thumbHeightPx.toDp() })
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)),
        )
    }
}

private data class ScrollbarMetrics(
    val progress: Float,
    val thumbFraction: Float,
)

private fun averageItemStep(items: List<LazyListItemInfo>): Float {
    if (items.size > 1) {
        val first = items.first()
        val last = items.last()
        return ((last.offset - first.offset + last.size).toFloat() / items.size).coerceAtLeast(1f)
    }
    return items.first().size.toFloat().coerceAtLeast(1f)
}

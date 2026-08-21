package taihon.feature.library.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.ViewConfiguration

/**
 * Modifier to dismiss search when the user taps or scrolls.
 */
fun Modifier.dismissSearchOnTouchOrScroll(
    enabled: Boolean,
    onDismissSearch: () -> Unit,
): Modifier = if (enabled) {
    this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press || event.type == PointerEventType.Scroll) {
                    onDismissSearch()
                }
            }
        }
    }
} else {
    this
}

/**
 * Modifier to dismiss search when the user taps or scrolls, with detailed swipe detection.
 */
fun Modifier.dismissSearchOnTouchOrScrollDetailed(
    enabled: Boolean,
    viewConfiguration: ViewConfiguration,
    onDismissSearch: () -> Unit,
): Modifier = if (enabled) {
    this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press) {
                    onDismissSearch()

                    var totalMovement = Offset.Zero
                    var isSwipe = false
                    do {
                        val nextEvent = awaitPointerEvent(PointerEventPass.Initial)
                        if (nextEvent.type == PointerEventType.Move) {
                            totalMovement += nextEvent.changes.first().positionChange()
                            if (totalMovement.getDistance() > viewConfiguration.touchSlop) {
                                isSwipe = true
                            }
                        } else if (nextEvent.type == PointerEventType.Release) {
                            if (!isSwipe) {
                                nextEvent.changes.forEach { it.consume() }
                            }
                            break
                        }
                    } while (nextEvent.changes.any { it.pressed })
                } else if (event.type == PointerEventType.Scroll) {
                    onDismissSearch()
                }
            }
        }
    }
} else {
    this
}

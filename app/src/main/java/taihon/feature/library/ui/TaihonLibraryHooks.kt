package taihon.feature.library.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.library.components.CoverTextOverlay

object TaihonLibraryHooks {
    @Composable
    fun TaihonComfortableGridItemOverlay(
        scope: BoxScope,
        coverText: String?,
        onClickContinueReading: (() -> Unit)?,
    ) {
        if (coverText != null) {
            with(scope) {
                CoverTextOverlay(
                    title = coverText,
                    onClickContinueReading = onClickContinueReading,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.85f),
                        shadow = Shadow(
                            color = Color.Black,
                            blurRadius = 4f,
                        ),
                        fontSize = 6.sp,
                    ),
                    padding = 2.dp,
                    fillMaxHeight = 0.25f,
                )
            }
        }
    }

    fun taihonLibrarySearchDismiss(
        modifier: Modifier,
        enabled: Boolean,
        onDismissSearch: () -> Unit,
    ): Modifier = modifier.then(
        Modifier.dismissSearchOnTouchOrScroll(
            enabled = enabled,
            onDismissSearch = onDismissSearch,
        ),
    )

    fun taihonLibrarySearchDismissDetailed(
        modifier: Modifier,
        enabled: Boolean,
        onDismissSearch: () -> Unit,
    ): Modifier = modifier.composed {
        val viewConfiguration = LocalViewConfiguration.current
        this.then(
            Modifier.dismissSearchOnTouchOrScrollDetailed(
                enabled = enabled,
                viewConfiguration = viewConfiguration,
                onDismissSearch = onDismissSearch,
            ),
        )
    }
}

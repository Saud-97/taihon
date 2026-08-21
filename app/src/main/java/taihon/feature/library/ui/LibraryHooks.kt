package taihon.feature.library.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.library.components.CoverTextOverlay

object LibraryHooks {
    @Composable
    fun BoxScope.TaihonComfortableGridItemOverlay(
        coverText: String?,
        onClickContinueReading: (() -> Unit)?,
    ) {
        if (coverText != null) {
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

    fun Modifier.taihonLibrarySearchDismiss(
        enabled: Boolean,
        onDismissSearch: () -> Unit,
    ): Modifier = this.then(
        Modifier.dismissSearchOnTouchOrScroll(
            enabled = enabled,
            onDismissSearch = onDismissSearch,
        ),
    )

    fun Modifier.taihonLibrarySearchDismissDetailed(
        enabled: Boolean,
        onDismissSearch: () -> Unit,
    ): Modifier = composed {
        val viewConfiguration = LocalViewConfiguration.current
        this.then(
            Modifier.dismissSearchOnTouchOrScrollDetailed(
                enabled = enabled,
                viewConfiguration = viewConfiguration,
                onDismissSearch = onDismissSearch,
            ),
        )
    }

    fun Modifier.taihonLibraryToolbarClick(
        onSearchQueryChange: (String) -> Unit,
    ): Modifier = composed {
        this.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { onSearchQueryChange("") },
        )
    }
}

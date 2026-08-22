package taihon.feature.library.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.model.icon
import eu.kanade.presentation.browse.components.SourceIcon
import tachiyomi.domain.source.model.Source
import tachiyomi.presentation.core.components.Badge

@Composable
fun TaihonSourceBadge(
    source: Source?,
) {
    if (source != null) {
        val isStub = source.isStub && source.icon == null
        Badge(
            color = if (isStub) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiary,
        ) {
            if (isStub) {
                Image(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
                    modifier = Modifier.size(18.dp),
                )
            } else {
                SourceIcon(
                    source = source,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

package taihon.feature.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            color = if (isStub) Color(0xFF8B0000) else MaterialTheme.colorScheme.tertiary,
        ) {
            SourceIcon(
                source = source,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

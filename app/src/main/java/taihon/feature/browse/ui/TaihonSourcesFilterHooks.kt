package taihon.feature.browse.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Modifier
import eu.kanade.presentation.browse.components.BaseSourceItem
import eu.kanade.tachiyomi.ui.browse.source.SourcesFilterViewModel
import tachiyomi.domain.source.model.Source
import taihon.feature.browse.TaihonSourcesFilterHelper

object TaihonSourcesFilterHooks {
    fun localSourceItem(
        scope: LazyListScope,
        state: SourcesFilterViewModel.State.Success,
        onClickSource: (Source) -> Unit,
    ) {
        val localSource = state.items[TaihonSourcesFilterHelper.TAIHON_LOCAL_KEY]?.firstOrNull()
        localSource?.let { source ->
            scope.item(
                key = "source-filter-local",
                contentType = "source-filter-item",
            ) {
                val enabled = "${source.id}" !in state.disabledSources
                BaseSourceItem(
                    modifier = Modifier.animateItem(),
                    source = source,
                    showLanguageInContent = false,
                    onClickItem = { onClickSource(source) },
                    action = {
                        Checkbox(checked = enabled, onCheckedChange = null)
                    },
                )
            }
        }
    }
}

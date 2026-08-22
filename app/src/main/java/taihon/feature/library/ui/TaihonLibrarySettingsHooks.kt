package taihon.feature.library.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.tachiyomi.ui.library.LibrarySettingsViewModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

object TaihonLibrarySettingsHooks {
    @Composable
    fun FilterPageSources(viewModel: LibrarySettingsViewModel) {
        val sources by viewModel.sourcesFlow.collectAsState()
        if (sources.isNotEmpty()) {
            HeadingItem(MR.strings.label_sources)
            val installedSources = remember(sources) { sources.filterNot { it.isStub } }
            val hasOrphaned = remember(sources) { sources.any { it.isStub } }

            installedSources.forEach { source ->
                val filterSource by viewModel.taihonPreferences.filterSource(source.id).collectAsState()
                TriStateItem(
                    label = source.visualName,
                    state = filterSource,
                    onClick = { viewModel.toggleSource(source.id) },
                )
            }

            if (hasOrphaned) {
                val filterOrphaned by viewModel.taihonPreferences.filterOrphanedSources.collectAsState()
                TriStateItem(
                    label = stringResource(MR.strings.ext_obsolete),
                    state = filterOrphaned,
                    onClick = { viewModel.toggleOrphanedSources() },
                )
            }
        }
    }

    @Composable
    fun DisplayPageSources(viewModel: LibrarySettingsViewModel) {
        CheckboxItem(
            label = stringResource(MR.strings.label_sources) + " (${stringResource(MR.strings.ext_installed)})",
            pref = viewModel.taihonPreferences.sourceInstalledBadge,
        )
        CheckboxItem(
            label = stringResource(MR.strings.label_sources) + " (${stringResource(MR.strings.ext_obsolete)})",
            pref = viewModel.taihonPreferences.sourceOrphanedBadge,
        )
    }
}

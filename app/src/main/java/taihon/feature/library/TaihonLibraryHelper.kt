package taihon.feature.library

import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.ui.library.LibraryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import taihon.domain.preferences.TaihonPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.domain.source.model.Source as DomainSource

object TaihonLibraryHelper {

    @Immutable
    data class State(
        val sourceFilter: SourceFilter,
        val showInstalledBadge: Boolean,
        val showOrphanedBadge: Boolean,
    )

    @Immutable
    data class SourceFilter(
        val sourceMap: Map<Long, TriState>,
        val orphaned: TriState,
    )

    fun getStateFlow(): Flow<State> {
        val sourceManager: SourceManager = Injekt.get()
        val taihonPreferences: TaihonPreferences = Injekt.get()
        return combine(
            getSourceFilterFlow(sourceManager, taihonPreferences),
            taihonPreferences.sourceInstalledBadge.changes(),
            taihonPreferences.sourceOrphanedBadge.changes(),
        ) { sourceFilter, installedBadge, orphanedBadge ->
            State(sourceFilter, installedBadge, orphanedBadge)
        }
    }

    private fun getSourceFilterFlow(
        sourceManager: SourceManager,
        taihonPreferences: TaihonPreferences,
    ): Flow<SourceFilter> {
        return sourceManager.sources
            .map { sources -> sources.filterNot { it is StubSource }.map { it.id }.toSet() }
            .distinctUntilChanged()
            .flatMapLatest { sourceIds ->
                if (sourceIds.isEmpty()) {
                    taihonPreferences.filterOrphanedSources.changes().map {
                        SourceFilter(emptyMap(), it)
                    }
                } else {
                    val filterFlows = sourceIds.map { id ->
                        taihonPreferences.filterSource(id).changes().map { id to it }
                    }
                    combine(filterFlows) { it.toMap() }
                        .combine(taihonPreferences.filterOrphanedSources.changes()) { map, orphaned ->
                            SourceFilter(map, orphaned)
                        }
                }
            }
    }

    fun filterFnSource(
        item: LibraryItem,
        state: State,
    ): Boolean {
        val sourceManager: SourceManager = Injekt.get()
        val filters = state.sourceFilter

        val excludedSources = filters.sourceMap.filter { it.value == TriState.ENABLED_NOT }.keys
        val includedSources = filters.sourceMap.filter { it.value == TriState.ENABLED_IS }.keys
        val orphanedIsExcluded = filters.orphaned == TriState.ENABLED_NOT
        val orphanedIsIncluded = filters.orphaned == TriState.ENABLED_IS

        val sourceId = item.libraryManga.manga.source
        val source = sourceManager.get(sourceId)
        val isOrphaned = source == null || source is StubSource

        val isExcluded = if (isOrphaned) orphanedIsExcluded else sourceId in excludedSources
        val isAnyIncluded = includedSources.isNotEmpty() || orphanedIsIncluded
        val isIncluded = if (isAnyIncluded) {
            if (isOrphaned) orphanedIsIncluded else sourceId in includedSources
        } else {
            true
        }

        return !isExcluded && isIncluded
    }

    fun createBadge(
        sourceId: Long,
        state: State,
    ): DomainSource? {
        val sourceManager: SourceManager = Injekt.get()
        val source = sourceManager.getOrStub(sourceId)
        val isStub = source is StubSource
        val showSource = (isStub && state.showOrphanedBadge) || (!isStub && state.showInstalledBadge)

        return if (showSource) {
            DomainSource(
                id = source.id,
                lang = source.lang,
                name = source.name,
                supportsLatest = source.supportsLatest,
                isStub = isStub,
            )
        } else {
            null
        }
    }

    fun isAnyFilterEnabled(state: State): Boolean {
        return state.sourceFilter.sourceMap.values.any { it != TriState.DISABLED } ||
            state.sourceFilter.orphaned != TriState.DISABLED
    }
}

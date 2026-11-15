package taihon.feature.library

import android.content.Context
import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.ui.library.LibraryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import mihon.app.di.appGraph
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import taihon.domain.preferences.TaihonPreferences
import tachiyomi.domain.source.model.Source as DomainSource

object TaihonLibraryHelper {

    @Immutable
    data class State(
        val sourceFilter: SourceFilter,
        val showInstalledBadge: Boolean,
        val showOrphanedBadge: Boolean,
        val sourceMap: Map<Long, DomainSource>,
    )

    @Immutable
    data class SourceFilter(
        val sourceMap: Map<Long, TriState>,
        val orphaned: TriState,
    )

    fun getStateFlow(context: Context): Flow<State> {
        val sourceManager = context.appGraph.sourceManager
        val taihonPreferences = context.appGraph.taihonPreferences
        return combine(
            getSourceFilterFlow(sourceManager, taihonPreferences),
            taihonPreferences.sourceInstalledBadge.changes(),
            taihonPreferences.sourceOrphanedBadge.changes(),
            sourceManager.sources,
        ) { sourceFilter, installedBadge, orphanedBadge, sources ->
            State(
                sourceFilter = sourceFilter,
                showInstalledBadge = installedBadge,
                showOrphanedBadge = orphanedBadge,
                sourceMap = sources.associate {
                    it.id to DomainSource(
                        id = it.id,
                        lang = it.lang,
                        name = it.name,
                        supportsLatest = it.supportsLatest,
                        isStub = it is StubSource,
                    )
                },
            )
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
        val filters = state.sourceFilter

        val excludedSources = filters.sourceMap.filter { it.value == TriState.ENABLED_NOT }.keys
        val includedSources = filters.sourceMap.filter { it.value == TriState.ENABLED_IS }.keys
        val orphanedIsExcluded = filters.orphaned == TriState.ENABLED_NOT
        val orphanedIsIncluded = filters.orphaned == TriState.ENABLED_IS

        val sourceId = item.libraryManga.manga.source
        val source = state.sourceMap[sourceId]
        val isOrphaned = source == null || source.isStub

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
        val source = state.sourceMap[sourceId]
        val isInstalled = source != null && !source.isStub
        val showSource = (!isInstalled && state.showOrphanedBadge) || (isInstalled && state.showInstalledBadge)

        return if (showSource) {
            source ?: DomainSource(
                id = sourceId,
                lang = "",
                name = "",
                supportsLatest = false,
                isStub = true,
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
